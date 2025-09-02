package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.ui.common.BaseViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkViewModelFactory
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private data class PendingPost(
    val resNum: Int?,
    val content: String,
    val name: String,
    val email: String,
)

class ThreadViewModel @AssistedInject constructor(
    private val datRepository: DatRepository,
    private val boardRepository: BoardRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val postHistoryRepository: PostHistoryRepository,
    private val singleBookmarkViewModelFactory: SingleBookmarkViewModelFactory,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    private val tabsRepository: TabsRepository,
    @Assisted @Suppress("unused") val viewModelKey: String,
) : BaseViewModel<ThreadUiState>() {

    override val _uiState = MutableStateFlow(ThreadUiState())
    private var singleBookmarkViewModel: SingleBookmarkViewModel? = null
    private var ngList: List<NgEntity> = emptyList()
    private var compiledNg: List<Triple<Long?, Regex, NgType>> = emptyList()
    private var initializedKey: String? = null
    private var pendingPost: PendingPost? = null
    private var observedThreadHistoryId: Long? = null
    private var postHistoryCollectJob: Job? = null

    //画面遷移した最初に行う初期処理
    fun initializeThread(
        threadKey: String,
        boardInfo: BoardInfo,
        threadTitle: String
    ) {
        val initKey = "$threadKey|${boardInfo.url}"
        if (initializedKey == initKey) return
        initializedKey = initKey
        val threadInfo = ThreadInfo(
            key = threadKey,
            title = threadTitle,
            url = boardInfo.url
        )
        _uiState.update { it.copy(boardInfo = boardInfo, threadInfo = threadInfo) }

        viewModelScope.launch {
            val currentTabs = tabsRepository.observeOpenThreadTabs().first()
            val tabIndex =
                currentTabs.indexOfFirst { it.key == threadKey && it.boardUrl == boardInfo.url }
            val updated = if (tabIndex != -1) {
                currentTabs.toMutableList().apply {
                    this[tabIndex] = this[tabIndex].copy(
                        title = threadTitle,
                        boardName = boardInfo.name,
                        boardId = boardInfo.boardId
                    )
                }
            } else {
                currentTabs + ThreadTabInfo(
                    key = threadKey,
                    title = threadTitle,
                    boardName = boardInfo.name,
                    boardUrl = boardInfo.url,
                    boardId = boardInfo.boardId
                )
            }
            tabsRepository.saveOpenThreadTabs(updated)
        }

        viewModelScope.launch {
            boardRepository.fetchBoardNoname("${boardInfo.url}SETTING.TXT")?.let { noname ->
                _uiState.update { state ->
                    state.copy(boardInfo = state.boardInfo.copy(noname = noname))
                }
            }
        }

        // Factoryを使ってBookmarkStateViewModelを生成
        singleBookmarkViewModel = singleBookmarkViewModelFactory.create(boardInfo, threadInfo)

        // 状態をマージ
        viewModelScope.launch {
            singleBookmarkViewModel?.uiState?.collect { favState ->
                _uiState.update { it.copy(singleBookmarkState = favState) }
            }
        }

        viewModelScope.launch {
            ngRepository.observeNgs().collect { list ->
                ngList = list
                compiledNg = list.mapNotNull { ng ->
                    runCatching {
                        val rx = if (ng.isRegex) {
                            Regex(ng.pattern)
                        } else {
                            // 通常文字列は正規表現メタ文字をエスケープした上で「部分一致」判定に統一
                            Regex(Regex.escape(ng.pattern))
                        }
                        Triple(ng.boardId, rx, ng.type)
                    }.getOrNull()
                }
                updateNgPostNumbers()
            }
        }

        viewModelScope.launch {
            val isTree = settingsRepository.observeIsTreeSort().first()
            _uiState.update { state ->
                state.copy(sortType = if (isTree) ThreadSortType.TREE else ThreadSortType.NUMBER)
            }
            initialize() // BaseViewModelの初期化処理を呼び出す
        }
    }

    override suspend fun loadData(isRefresh: Boolean) {
        // 画面ローディング状態をセットし、プログレスを初期化
        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        val boardUrl = uiState.value.boardInfo.url
        val key = uiState.value.threadInfo.key

        try {
            // DatRepository からスレ情報を取得（進捗コールバックを渡す）
            val threadData = datRepository.getThread(boardUrl, key) { progress ->
                // 読み込み進捗を UI 状態に反映
                _uiState.update { it.copy(loadProgress = progress) }
            }
            if (threadData != null) {
                // 正常に取得できた場合はパース結果を元に各種派生データを作成
                val (posts, title) = threadData
                // ID カウント / インデックス / 返信ソースマップ を導出
                val derived = deriveReplyMaps(posts)
                // ツリー順と深さマップを導出
                val tree = deriveTreeOrder(posts)
                // UI 状態に新しい投稿リスト等を反映（読み込みフラグ解除）
                _uiState.update {
                    it.copy(
                        posts = posts,
                        isLoading = false,
                        loadProgress = 1f,
                        threadInfo = it.threadInfo.copy(title = title ?: it.threadInfo.title),
                        idCountMap = derived.first,
                        idIndexList = derived.second,
                        replySourceMap = derived.third,
                        treeOrder = tree.first,
                        treeDepthMap = tree.second,
                    )
                }

                // NG 判定を再計算して表示用投稿リストを更新
                updateNgPostNumbers()

                // スレ履歴に件数を記録し、そのIDを取得
                val historyId = historyRepository.recordHistory(
                    uiState.value.boardInfo,
                    uiState.value.threadInfo.copy(title = title ?: uiState.value.threadInfo.title),
                    posts.size
                )

                // 履歴 ID が変わっていれば、過去の自分の投稿番号観察を再登録
                if (observedThreadHistoryId != historyId) {
                    observedThreadHistoryId = historyId
                    postHistoryCollectJob?.cancel()
                    postHistoryCollectJob = viewModelScope.launch {
                        postHistoryRepository.observeMyPostNumbers(historyId).collect { nums ->
                            _uiState.update { it.copy(myPostNumbers = nums) }
                        }
                    }
                }

                // 保留していた投稿情報があれば履歴に記録（該当レス番号が有効な場合）
                pendingPost?.let { pending ->
                    val resNumber = pending.resNum ?: posts.size
                    if (resNumber in 1..posts.size) {
                        val p = posts[resNumber - 1]
                        postHistoryRepository.recordPost(
                            content = pending.content,
                            date = parseDateToUnix(p.date),
                            threadHistoryId = historyId,
                            boardId = uiState.value.boardInfo.boardId,
                            resNum = resNumber,
                            name = pending.name,
                            email = pending.email,
                            postId = p.id
                        )
                    }
                    // 保留をクリア
                    pendingPost = null
                }
            } else {
                // 取得失敗時は読み込みフラグを解除してログを出力
                _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
                Timber.e("Failed to load thread data for board: $boardUrl key: $key")
            }
        } catch (_: Exception) {
            // 例外時は読み込みフラグを解除（例外オブジェクトは参照しない）
            _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
        }
    }

    // 投稿リストからIDカウント・IDインデックス・返信元マップを生成
    // 1. idCountMap: 各IDの出現回数
    // 2. idIndexList: 各投稿のID出現順（同一IDの何番目か）
    // 3. replySourceMap: 各投稿番号に対する返信元（>>n）の投稿番号リスト
    private fun deriveReplyMaps(posts: List<ReplyInfo>): Triple<Map<String, Int>, List<Int>, Map<Int, List<Int>>> {
        // IDごとの出現回数を集計
        val idCountMap = posts.groupingBy { it.id }.eachCount()
        // 各投稿のID出現順を計算
        val idIndexList = run {
            val indexMap = mutableMapOf<String, Int>()
            posts.map { reply ->
                val idx = (indexMap[reply.id] ?: 0) + 1
                indexMap[reply.id] = idx
                idx
            }
        }
        // 各投稿への返信元（>>n）を抽出
        val replySourceMap = run {
            val map = mutableMapOf<Int, MutableList<Int>>()
            val regex = Regex(">>(\\d+)")
            posts.forEachIndexed { idx, reply ->
                regex.findAll(reply.content).forEach { match ->
                    val num = match.groupValues[1].toIntOrNull() ?: return@forEach
                    if (num in 1..posts.size) {
                        map.getOrPut(num) { mutableListOf() }.add(idx + 1)
                    }
                }
            }
            map.mapValues { it.value.toList() }
        }
        return Triple(idCountMap, idIndexList, replySourceMap)
    }

    // 投稿リストからツリー順（親子関係）と各投稿の深さを計算
    // 1. children: 親投稿番号ごとに子投稿番号リストを保持
    // 2. parent: 各投稿番号の親投稿番号（なければ0）
    // 3. depthMap: 各投稿番号のツリー深さ
    // 4. order: DFSで得られる表示順
    private fun deriveTreeOrder(posts: List<ReplyInfo>): Pair<List<Int>, Map<Int, Int>> {
        val children = mutableMapOf<Int, MutableList<Int>>() // 親→子リスト
        val parent = IntArray(posts.size + 1) // 投稿番号→親投稿番号
        val depthMap = mutableMapOf<Int, Int>() // 投稿番号→深さ
        val regex = Regex("^>>(\\d+)") // 先頭>>n形式のみ親とみなす
        posts.forEachIndexed { idx, reply ->
            val current = idx + 1 // 投稿番号
            val match = regex.find(reply.content)
            val p = match?.groupValues?.get(1)?.toIntOrNull()
            if (p != null && p in 1 until current) {
                parent[current] = p // 親投稿番号を記録
                children.getOrPut(p) { mutableListOf() }.add(current) // 親→子リストに追加
            }
        }
        val order = mutableListOf<Int>() // DFSで表示順を構築
        fun dfs(num: Int, depth: Int) {
            order.add(num)
            depthMap[num] = depth
            children[num]?.forEach { child -> dfs(child, depth + 1) }
        }
        // 親がいない投稿（親番号0）からDFS開始
        for (i in 1..posts.size) {
            if (parent[i] == 0) {
                dfs(i, 0)
            }
        }
        return order to depthMap
    }

    private fun parseDateToUnix(dateString: String): Long {
        val sanitized = dateString
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\.\\d+"), "")
            .trim()
        return try {
            DATE_FORMAT.parse(sanitized)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun updateNgPostNumbers() {
        val posts = uiState.value.posts ?: return
        val boardId = uiState.value.boardInfo.boardId
        val ngNumbers = posts.mapIndexedNotNull { idx, post ->
            val isNg = compiledNg.any { (bId, rx, type) ->
                (bId == null || bId == boardId) && runCatching {
                    val target = when (type) {
                        NgType.USER_ID -> post.id
                        NgType.USER_NAME -> post.name
                        NgType.WORD -> post.content
                        else -> ""
                    }
                    rx.containsMatchIn(target)
                }.getOrDefault(false)
            }
            if (isNg) idx + 1 else null
        }.toSet()
        _uiState.update { it.copy(ngPostNumbers = ngNumbers) }
        updateDisplayPosts()
    }

    fun setNewArrivalInfo(firstNewResNo: Int?, prevResCount: Int) {
        _uiState.update { it.copy(firstNewResNo = firstNewResNo, prevResCount = prevResCount) }
        updateDisplayPosts()
    }

    // 表示用投稿リストを更新する
    // NG投稿・検索・ツリー/番号ソート・新着インデックスなどの状態を反映
    private fun updateDisplayPosts() {
        // 投稿リスト取得（nullならreturn）
        val posts = uiState.value.posts ?: return
        // 新着レス番号・前回レス数取得
        val firstNewResNo = uiState.value.firstNewResNo
        val prevResCount = uiState.value.prevResCount
        // 並び順（ツリー or 番号）
        val order = if (uiState.value.sortType == ThreadSortType.TREE) {
            uiState.value.treeOrder
        } else {
            (1..posts.size).toList()
        }
        // ツリーソートかつ新着レスありの場合の並び替え
        val orderedPosts = buildOrderedPosts(
            posts = posts,
            order = order,
            sortType = uiState.value.sortType,
            treeDepthMap = uiState.value.treeDepthMap,
            firstNewResNo = firstNewResNo,
            prevResCount = prevResCount
        )

        // 検索クエリがあれば絞り込み
        val filteredPosts = if (uiState.value.searchQuery.isNotBlank()) {
            orderedPosts.filter {
                it.post.content.contains(
                    uiState.value.searchQuery,
                    ignoreCase = true
                )
            }
        } else {
            orderedPosts
        }
        // NG投稿を除外
        val visiblePosts = filteredPosts.filterNot { it.num in uiState.value.ngPostNumbers }
        // 各投稿の返信数リスト
        val replyCounts = visiblePosts.map { p -> uiState.value.replySourceMap[p.num]?.size ?: 0 }
        // 新着インデックス（最初の新着投稿の位置）
        val firstAfterIndex = visiblePosts.indexOfFirst { it.isAfter }

        // UI状態を更新
        _uiState.update {
            it.copy(
                visiblePosts = visiblePosts,
                replyCounts = replyCounts,
                firstAfterIndex = firstAfterIndex
            )
        }
    }

    private fun buildOrderedPosts(
        posts: List<ReplyInfo>,
        order: List<Int>,
        sortType: ThreadSortType,
        treeDepthMap: Map<Int, Int>,
        firstNewResNo: Int?,
        prevResCount: Int
    ): List<DisplayPost> {
        // ツリーソートかつ新着レスありの場合の並び替え
        if (sortType == ThreadSortType.TREE && firstNewResNo != null) {
            // 親子関係および子リストマップを構築
            val parentMap = mutableMapOf<Int, Int>()
            val childrenMap = mutableMapOf<Int, MutableList<Int>>()
            val stack = mutableListOf<Int>()
            order.forEach { num ->
                val depth = treeDepthMap[num] ?: 0
                while (stack.size > depth) stack.removeAt(stack.lastIndex)
                val parent = stack.lastOrNull() ?: 0
                parentMap[num] = parent
                childrenMap.getOrPut(parent) { mutableListOf() }.add(num)
                stack.add(num)
            }

            // 番号順で before / after を単純分割
            val beforeSet = linkedSetOf<Int>()
            val afterSet = linkedSetOf<Int>()
            for (num in 1..posts.size) {
                val parent = parentMap[num] ?: 0
                if (num < firstNewResNo || (parent in 1 until firstNewResNo && num <= prevResCount)) {
                    beforeSet.add(num)
                } else {
                    afterSet.add(num)
                }
            }

            // before をツリー順に並べ替え
            val before = mutableListOf<DisplayPost>()
            order.forEach { num ->
                if (beforeSet.contains(num)) {
                    posts.getOrNull(num - 1)?.let { post ->
                        val depth = treeDepthMap[num] ?: 0
                        before.add(
                            DisplayPost(
                                id = UUID.randomUUID().toString(),
                                num = num,
                                post = post,
                                dimmed = false,
                                isAfter = false,
                                depth = depth
                            )
                        )
                    }
                }
            }

            // after を番号順からツリー状に並べ替え、必要に応じて親を再表示
            val after = mutableListOf<DisplayPost>()
            val insertedParents = mutableSetOf<Int>()
            val visited = mutableSetOf<Int>()

            fun traverse(num: Int, shift: Int) {
                val isAfter = afterSet.contains(num)
                if (isAfter && !visited.add(num)) return

                if (isAfter) {
                    posts.getOrNull(num - 1)?.let { post ->
                        val depth = (treeDepthMap[num] ?: 0) - shift
                        after.add(
                            DisplayPost(
                                id = UUID.randomUUID().toString(),
                                num = num,
                                post = post,
                                dimmed = false,
                                isAfter = true,
                                depth = depth
                            )
                        )
                    }
                }
                childrenMap[num]?.forEach { child ->
                    traverse(child, shift)
                }
            }

            val afterNums = afterSet.toList().sorted()
            afterNums.forEach { num ->
                if (visited.contains(num)) return@forEach
                val parent = parentMap[num] ?: 0
                if (parent in beforeSet) {
                    if (insertedParents.add(parent)) {
                        posts.getOrNull(parent - 1)?.let { p ->
                            after.add(
                                DisplayPost(
                                    id = UUID.randomUUID().toString(),
                                    num = parent,
                                    post = p,
                                    dimmed = true,
                                    isAfter = true,
                                    depth = 0
                                )
                            )
                        }
                    }
                    val shift = treeDepthMap[parent] ?: 0
                    childrenMap[parent]?.forEach { child -> traverse(child, shift) }
                } else {
                    val shift = treeDepthMap[num] ?: 0
                    traverse(num, shift)
                }
            }

            // before と after を連結
            return before + after
        } else {
            // 通常の並び（番号順 or ツリー順）
            return order.mapNotNull { num ->
                posts.getOrNull(num - 1)?.let { post ->
                    val isAfter = firstNewResNo != null && num >= firstNewResNo
                    val depth = if (sortType == ThreadSortType.TREE) treeDepthMap[num] ?: 0 else 0
                    DisplayPost(
                        id = UUID.randomUUID().toString(),
                        num = num,
                        post = post,
                        dimmed = false,
                        isAfter = isAfter,
                        depth = depth
                    )
                }
            }
        }
    }

    fun reloadThread() {
        initialize(force = true) // 強制的に初期化処理を再実行
    }

    fun toggleSortType() {
        _uiState.update { state ->
            val next = if (state.sortType == ThreadSortType.NUMBER) {
                ThreadSortType.TREE
            } else {
                ThreadSortType.NUMBER
            }
            state.copy(sortType = next)
        }
        updateDisplayPosts()
    }


    // --- お気に入り関連の処理はBookmarkStateViewModelに委譲 ---
    fun saveBookmark(groupId: Long) = singleBookmarkViewModel?.saveBookmark(groupId)
    fun unbookmarkBoard() = singleBookmarkViewModel?.unbookmark()
    fun openAddGroupDialog() = singleBookmarkViewModel?.openAddGroupDialog()
    fun openEditGroupDialog(group: Groupable) = singleBookmarkViewModel?.openEditGroupDialog(group)
    fun closeAddGroupDialog() = singleBookmarkViewModel?.closeAddGroupDialog()
    fun setEnteredGroupName(name: String) = singleBookmarkViewModel?.setEnteredGroupName(name)
    fun setSelectedColor(color: String) = singleBookmarkViewModel?.setSelectedColor(color)
    fun confirmGroup() = singleBookmarkViewModel?.confirmGroup()
    fun requestDeleteGroup() = singleBookmarkViewModel?.requestDeleteGroup()
    fun confirmDeleteGroup() = singleBookmarkViewModel?.confirmDeleteGroup()
    fun closeDeleteGroupDialog() = singleBookmarkViewModel?.closeDeleteGroupDialog()
    fun openBookmarkSheet() = singleBookmarkViewModel?.openBookmarkSheet()
    fun closeBookmarkSheet() = singleBookmarkViewModel?.closeBookmarkSheet()

    // 書き込み画面を表示
    fun startSearch() {
        _uiState.update { it.copy(isSearchMode = true) }
        updateDisplayPosts()
    }

    fun closeSearch() {
        _uiState.update { it.copy(isSearchMode = false, searchQuery = "") }
        updateDisplayPosts()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateDisplayPosts()
    }

    fun onPostSuccess(resNum: Int?, message: String, name: String, mail: String) {
        pendingPost = PendingPost(resNum, message, name, mail)
        reloadThread()
    }


    fun updateThreadTabInfo(key: String, boardUrl: String, title: String, resCount: Int) {
        viewModelScope.launch {
            val current = tabsRepository.observeOpenThreadTabs().first()
            val updated = current.map { tab ->
                if (tab.key == key && tab.boardUrl == boardUrl) {
                    val candidate = if (tab.lastReadResNo == 0) {
                        null
                    } else if (tab.firstNewResNo == null || tab.firstNewResNo <= tab.lastReadResNo) {
                        tab.lastReadResNo + 1
                    } else {
                        tab.firstNewResNo
                    }
                    val newFirst = candidate?.let { if (it > resCount) null else candidate }
                    tab.copy(
                        title = title,
                        resCount = resCount,
                        prevResCount = tab.resCount,
                        firstNewResNo = newFirst
                    )
                } else {
                    tab
                }
            }
            tabsRepository.saveOpenThreadTabs(updated)
        }
    }

    fun updateThreadScrollPosition(
        tabKey: String,
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
        viewModelScope.launch {
            val current = tabsRepository.observeOpenThreadTabs().first()
            val updated = current.map { tab ->
                if (tab.key == tabKey && tab.boardUrl == boardUrl) {
                    tab.copy(
                        firstVisibleItemIndex = firstVisibleIndex,
                        firstVisibleItemScrollOffset = scrollOffset
                    )
                } else {
                    tab
                }
            }
            tabsRepository.saveOpenThreadTabs(updated)
        }
    }

    fun updateThreadLastRead(tabKey: String, boardUrl: String, lastReadResNo: Int) {
        viewModelScope.launch {
            val current = tabsRepository.observeOpenThreadTabs().first()
            val updated = current.map { tab ->
                if (tab.key == tabKey && tab.boardUrl == boardUrl && lastReadResNo > tab.lastReadResNo) {
                    tab.copy(lastReadResNo = lastReadResNo)
                } else {
                    tab
                }
            }
            tabsRepository.saveOpenThreadTabs(updated)
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        }
    }
}

@AssistedFactory
interface ThreadViewModelFactory {
    fun create(viewModelKey: String): ThreadViewModel
}
