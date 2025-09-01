package com.websarva.wings.android.slevo.ui.thread.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.NgType
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
    @Assisted val viewModelKey: String,
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
            val tabIndex = currentTabs.indexOfFirst { it.key == threadKey && it.boardUrl == boardInfo.url }
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

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadData(isRefresh: Boolean) {
        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        val boardUrl = uiState.value.boardInfo.url
        val key = uiState.value.threadInfo.key

        try {
            val threadData = datRepository.getThread(boardUrl, key) { progress ->
                _uiState.update { it.copy(loadProgress = progress) }
            }
            if (threadData != null) {
                val (posts, title) = threadData
                val derived = deriveReplyMaps(posts)
                val tree = deriveTreeOrder(posts)
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
                updateNgPostNumbers()
                val historyId = historyRepository.recordHistory(
                    uiState.value.boardInfo,
                    uiState.value.threadInfo.copy(title = title ?: uiState.value.threadInfo.title),
                    posts.size
                )
                if (observedThreadHistoryId != historyId) {
                    observedThreadHistoryId = historyId
                    postHistoryCollectJob?.cancel()
                    postHistoryCollectJob = viewModelScope.launch {
                        postHistoryRepository.observeMyPostNumbers(historyId).collect { nums ->
                            _uiState.update { it.copy(myPostNumbers = nums) }
                        }
                    }
                }
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
                    pendingPost = null
                }
            } else {
                _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
                Timber.e("Failed to load thread data for board: $boardUrl key: $key")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
        }
    }

    private fun deriveReplyMaps(posts: List<ReplyInfo>): Triple<Map<String, Int>, List<Int>, Map<Int, List<Int>>> {
        val idCountMap = posts.groupingBy { it.id }.eachCount()
        val idIndexList = run {
            val indexMap = mutableMapOf<String, Int>()
            posts.map { reply ->
                val idx = (indexMap[reply.id] ?: 0) + 1
                indexMap[reply.id] = idx
                idx
            }
        }
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

    private fun deriveTreeOrder(posts: List<ReplyInfo>): Pair<List<Int>, Map<Int, Int>> {
        val children = mutableMapOf<Int, MutableList<Int>>()
        val parent = IntArray(posts.size + 1)
        val depthMap = mutableMapOf<Int, Int>()
        val regex = Regex("^>>(\\d+)")
        posts.forEachIndexed { idx, reply ->
            val current = idx + 1
            val match = regex.find(reply.content)
            val p = match?.groupValues?.get(1)?.toIntOrNull()
            if (p != null && p in 1 until current) {
                parent[current] = p
                children.getOrPut(p) { mutableListOf() }.add(current)
            }
        }
        val order = mutableListOf<Int>()
        fun dfs(num: Int, depth: Int) {
            order.add(num)
            depthMap[num] = depth
            children[num]?.forEach { child -> dfs(child, depth + 1) }
        }
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

    private fun updateDisplayPosts() {
        val posts = uiState.value.posts ?: return
        val firstNewResNo = uiState.value.firstNewResNo
        val prevResCount = uiState.value.prevResCount
        val order = if (uiState.value.sortType == ThreadSortType.TREE) {
            uiState.value.treeOrder
        } else {
            (1..posts.size).toList()
        }
        val orderedPosts = if (uiState.value.sortType == ThreadSortType.TREE && firstNewResNo != null) {
            val parentMap = mutableMapOf<Int, Int>()
            val stack = mutableListOf<Int>()
            order.forEach { num ->
                val depth = uiState.value.treeDepthMap[num] ?: 0
                while (stack.size > depth) stack.removeLast()
                parentMap[num] = stack.lastOrNull() ?: 0
                stack.add(num)
            }
            val before = mutableListOf<DisplayPost>()
            val after = mutableListOf<DisplayPost>()
            val insertedParents = mutableSetOf<Int>()
            order.forEach { num ->
                val parent = parentMap[num] ?: 0
                val post = posts.getOrNull(num - 1) ?: return@forEach
                if (num < firstNewResNo) {
                    before.add(DisplayPost(num, post, false, false))
                } else {
                    val parentOld = parent in 1 until firstNewResNo
                    if (parentOld && num <= prevResCount) {
                        before.add(DisplayPost(num, post, false, false))
                    } else {
                        if (parentOld) {
                            if (insertedParents.add(parent)) {
                                posts.getOrNull(parent - 1)?.let { p ->
                                    after.add(DisplayPost(parent, p, true, true))
                                }
                            }
                        }
                        after.add(DisplayPost(num, post, false, true))
                    }
                }
            }
            before + after
        } else {
            order.mapNotNull { num ->
                posts.getOrNull(num - 1)?.let { post ->
                    val isAfter = firstNewResNo != null && num >= firstNewResNo
                    DisplayPost(num, post, false, isAfter)
                }
            }
        }

        val filteredPosts = if (uiState.value.searchQuery.isNotBlank()) {
            orderedPosts.filter { it.post.content.contains(uiState.value.searchQuery, ignoreCase = true) }
        } else {
            orderedPosts
        }
        val visiblePosts = filteredPosts.filterNot { it.num in uiState.value.ngPostNumbers }
        val replyCounts = visiblePosts.map { p -> uiState.value.replySourceMap[p.num]?.size ?: 0 }
        val firstAfterIndex = visiblePosts.indexOfFirst { it.isAfter }

        _uiState.update { it.copy(visiblePosts = visiblePosts, replyCounts = replyCounts, firstAfterIndex = firstAfterIndex) }
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
