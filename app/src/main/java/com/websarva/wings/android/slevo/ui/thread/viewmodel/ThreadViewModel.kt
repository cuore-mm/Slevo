package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.ImageUploadRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.PostRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.ui.bbsroute.BaseViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.common.bookmark.ThreadTarget
import com.websarva.wings.android.slevo.ui.util.toHiragana
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.ui.thread.state.PostUiState
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.data.util.ThreadListParser.calculateThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max

/**
 * 投稿送信前に保持する入力内容。
 *
 * 返信番号や投稿本文など、送信に必要な要素をまとめる。
 */
private data class PendingPost(
    val resNum: Int?,
    val content: String,
    val name: String,
    val email: String,
)

/**
 * スレッド画面の状態を管理するViewModel。
 *
 * 投稿の表示や操作に関するUI状態を保持・更新する。
 */
class ThreadViewModel @AssistedInject constructor(
    private val datRepository: DatRepository,
    private val boardRepository: BoardRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val postHistoryRepository: PostHistoryRepository,
    private val threadBookmarkRepository: ThreadBookmarkRepository,
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    private val tabsRepository: TabsRepository,
    threadReadStateRepository: ThreadReadStateRepository,
    internal val postRepository: PostRepository,
    internal val imageUploadRepository: ImageUploadRepository,
    @Assisted @Suppress("unused") val viewModelKey: String,
) : BaseViewModel<ThreadUiState>() {

    private val tabCoordinator = ThreadTabCoordinator(
        scope = viewModelScope,
        tabsRepository = tabsRepository,
        readStateRepository = threadReadStateRepository,
    )

    override val _uiState = MutableStateFlow(ThreadUiState())
    private var ngList: List<NgEntity> = emptyList()
    private var compiledNg: List<Triple<Long?, Regex, NgType>> = emptyList()
    private var initializedKey: String? = null
    private var pendingPost: PendingPost? = null
    private var observedThreadHistoryId: Long? = null
    private var postHistoryCollectJob: Job? = null
    private var bookmarkStatusJob: Job? = null
    private var bookmarkSheetJob: Job? = null
    private var bookmarkSheetHolder: BookmarkBottomSheetStateHolder? = null
    private var lastAutoRefreshTime: Long = 0L

    init {
        viewModelScope.launch {
            settingsRepository.observeTextScale().collect { scale ->
                _uiState.update { it.copy(textScale = scale) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeIsIndividualTextScale().collect { enabled ->
                _uiState.update { it.copy(isIndividualTextScale = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeHeaderTextScale().collect { scale ->
                _uiState.update { it.copy(headerTextScale = scale) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeBodyTextScale().collect { scale ->
                _uiState.update { it.copy(bodyTextScale = scale) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeLineHeight().collect { height ->
                _uiState.update { it.copy(lineHeight = height) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeIsThreadMinimapScrollbarEnabled().collect { enabled ->
                _uiState.update { it.copy(showMinimapScrollbar = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeGestureSettings().collect { settings ->
                _uiState.update { it.copy(gestureSettings = settings) }
            }
        }
    }

    internal val _postUiState = MutableStateFlow(PostUiState())
    val postUiState: StateFlow<PostUiState> = _postUiState.asStateFlow()

    /**
     * 画面遷移時の初期処理を行う。
     */
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
        _postUiState.update { it.copy(namePlaceholder = boardInfo.noname) }

        viewModelScope.launch {
            val currentTabs = tabsRepository.observeOpenThreadTabs().first()
            val tabIndex =
                currentTabs.indexOfFirst { it.threadKey == threadKey && it.boardUrl == boardInfo.url }
            val updated = if (tabIndex != -1) {
                currentTabs.toMutableList().apply {
                    this[tabIndex] = this[tabIndex].copy(
                        title = threadTitle,
                        boardName = boardInfo.name,
                        boardId = boardInfo.boardId
                    )
                }
            } else {
                val (host, board) = parseBoardUrl(boardInfo.url) ?: return@launch
                currentTabs + ThreadTabInfo(
                    id = ThreadId.of(host, board, threadKey),
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
                _postUiState.update { state -> state.copy(namePlaceholder = noname) }
            }
        }

        viewModelScope.launch {
            val ensuredId = boardRepository.ensureBoard(boardInfo)
            if (ensuredId != boardInfo.boardId) {
                _uiState.update { state ->
                    state.copy(boardInfo = state.boardInfo.copy(boardId = ensuredId))
                }
            }
            preparePostIdentityHistory(ensuredId)
        }

        // ブックマーク状態を監視してツールバー表示に反映
        bookmarkStatusJob?.cancel()
        bookmarkStatusJob = viewModelScope.launch {
            threadBookmarkRepository.getBookmarkWithGroup(threadKey, boardInfo.url)
                .collect { threadWithBookmark ->
                    val group = threadWithBookmark?.group
                    _uiState.update {
                        it.copy(
                            bookmarkStatusState = BookmarkStatusState(
                                isBookmarked = group != null,
                                selectedGroup = group
                            )
                        )
                    }
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
                val uiPosts = posts.map { it.toThreadPostUiModel() }
                // ID カウント / インデックス / 返信ソースマップ を導出
                val derived = deriveReplyMaps(uiPosts)
                // ツリー順と深さマップを導出
                val tree = deriveTreeOrder(uiPosts)
                val resCount = uiPosts.size
                val keyLong = key.toLongOrNull()
                val date = if (keyLong != null && keyLong in 1 until THREAD_KEY_THRESHOLD) {
                    calculateThreadDate(key)
                } else {
                    ThreadDate(0, 0, 0, 0, 0, "")
                }
                val momentum = if (keyLong != null && keyLong in 1 until THREAD_KEY_THRESHOLD && resCount > 0) {
                    val elapsedSeconds = max(1L, System.currentTimeMillis() / 1000 - keyLong)
                    val elapsedDays = elapsedSeconds / 86400.0
                    if (elapsedDays > 0) resCount / elapsedDays else 0.0
                } else {
                    0.0
                }
                // UI 状態に新しい投稿リスト等を反映（読み込みフラグ解除）
                _uiState.update {
                    it.copy(
                        posts = uiPosts,
                        isLoading = false,
                        loadProgress = 1f,
                        threadInfo = it.threadInfo.copy(
                            title = title ?: it.threadInfo.title,
                            resCount = resCount,
                            date = date,
                            momentum = momentum
                        ),
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
                    uiPosts.size
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
                    val resNumber = pending.resNum ?: uiPosts.size
                    if (resNumber in 1..uiPosts.size) {
                        val p = uiPosts[resNumber - 1]
                        postHistoryRepository.recordPost(
                            content = pending.content,
                            date = parseDateToUnix(p.header.date),
                            threadHistoryId = historyId,
                            boardId = uiState.value.boardInfo.boardId,
                            resNum = resNumber,
                            name = pending.name,
                            email = pending.email,
                            postId = p.header.id
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

    private fun updateNgPostNumbers() {
        val posts = uiState.value.posts ?: return
        val boardId = uiState.value.boardInfo.boardId
        val ngNumbers = posts.mapIndexedNotNull { idx, post ->
            val isNg = compiledNg.any { (bId, rx, type) ->
                (bId == null || bId == boardId) && runCatching {
                    val target = when (type) {
                        NgType.USER_ID -> post.header.id
                        NgType.USER_NAME -> post.header.name
                        NgType.WORD -> post.body.content
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
        val orderedPosts = buildOrderedPosts(
            posts = posts,
            order = order,
            sortType = uiState.value.sortType,
            treeDepthMap = uiState.value.treeDepthMap,
            firstNewResNo = firstNewResNo,
            prevResCount = prevResCount
        )

        val query = uiState.value.searchQuery.toHiragana()
        val filteredPosts = if (query.isNotBlank()) {
            orderedPosts.filter {
                it.post.body.content.toHiragana().contains(
                    query,
                    ignoreCase = true
                )
            }
        } else {
            orderedPosts
        }
        val visiblePosts = filteredPosts.filterNot { it.num in uiState.value.ngPostNumbers }
        val replyCounts = visiblePosts.map { p -> uiState.value.replySourceMap[p.num]?.size ?: 0 }
        val firstAfterIndex = visiblePosts.indexOfFirst { it.isAfter }

        _uiState.update {
            it.copy(
                visiblePosts = visiblePosts,
                replyCounts = replyCounts,
                firstAfterIndex = firstAfterIndex
            )
        }
    }

    fun reloadThread() {
        initialize(force = true) // 強制的に初期化処理を再実行
    }

    fun toggleAutoScroll() {
        val enabled = !_uiState.value.isAutoScroll
        _uiState.update { it.copy(isAutoScroll = enabled) }
        if (!enabled) {
            lastAutoRefreshTime = 0L
        }
    }

    fun onAutoScrollReachedBottom() {
        if (!_uiState.value.isAutoScroll) return
        val now = System.currentTimeMillis()
        if (lastAutoRefreshTime == 0L || now - lastAutoRefreshTime >= 10_000L) {
            lastAutoRefreshTime = now
            reloadThread()
        }
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


    // --- ブックマークシート関連 ---
    /**
     * ブックマークシートを開き、ステートホルダーを生成する。
     */
    fun openBookmarkSheet() {
        val boardInfo = uiState.value.boardInfo
        val threadInfo = uiState.value.threadInfo
        if (boardInfo.url.isBlank() || threadInfo.key.isBlank()) {
            // 必要情報が欠けている場合はシートを開かない。
            return
        }

        // --- Holder setup ---
        bookmarkSheetHolder?.dispose()
        bookmarkSheetJob?.cancel()

        val targets = listOf(
            ThreadTarget(
                boardInfo = boardInfo,
                threadInfo = threadInfo,
                currentGroupId = uiState.value.bookmarkStatusState.selectedGroup?.id
            )
        )
        val holder = bookmarkSheetStateHolderFactory.create(viewModelScope, targets)
        bookmarkSheetHolder = holder
        bookmarkSheetJob = viewModelScope.launch {
            holder.uiState.collect { sheetState ->
                _uiState.update { it.copy(bookmarkSheetState = sheetState) }
            }
        }
        _uiState.update {
            it.copy(
                showBookmarkSheet = true,
                bookmarkSheetState = holder.uiState.value
            )
        }
    }

    /**
     * ブックマークシートを閉じてステートホルダーを破棄する。
     */
    fun closeBookmarkSheet() {
        _uiState.update {
            it.copy(
                showBookmarkSheet = false,
                bookmarkSheetState = BookmarkSheetUiState()
            )
        }
        bookmarkSheetJob?.cancel()
        bookmarkSheetHolder?.dispose()
        bookmarkSheetJob = null
        bookmarkSheetHolder = null
    }

    /**
     * ブックマークの保存を実行してシートを閉じる。
     */
    fun saveBookmark(groupId: Long) {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.applyGroup(groupId)
            closeBookmarkSheet()
        }
    }

    /**
     * ブックマークの解除を実行してシートを閉じる。
     */
    fun unbookmarkBoard() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.unbookmarkTargets()
            closeBookmarkSheet()
        }
    }

    /**
     * グループ追加ダイアログを開く。
     */
    fun openAddGroupDialog() {
        bookmarkSheetHolder?.openAddGroupDialog()
    }

    /**
     * グループ編集ダイアログを開く。
     */
    fun openEditGroupDialog(group: Groupable) {
        bookmarkSheetHolder?.openEditGroupDialog(group)
    }

    /**
     * グループ追加/編集ダイアログを閉じる。
     */
    fun closeAddGroupDialog() {
        bookmarkSheetHolder?.closeAddGroupDialog()
    }

    /**
     * 入力中のグループ名を更新する。
     */
    fun setEnteredGroupName(name: String) {
        bookmarkSheetHolder?.setEnteredGroupName(name)
    }

    /**
     * 入力中のグループ色を更新する。
     */
    fun setSelectedColor(color: String) {
        bookmarkSheetHolder?.setSelectedColor(color)
    }

    /**
     * グループ内容を確定する。
     */
    fun confirmGroup() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.confirmGroup()
        }
    }

    /**
     * グループ削除確認ダイアログを開く。
     */
    fun requestDeleteGroup() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.requestDeleteGroup()
        }
    }

    /**
     * グループ削除を確定する。
     */
    fun confirmDeleteGroup() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.confirmDeleteGroup()
        }
    }

    /**
     * グループ削除ダイアログを閉じる。
     */
    fun closeDeleteGroupDialog() {
        bookmarkSheetHolder?.closeDeleteGroupDialog()
    }

    fun openThreadInfoSheet() {
        _uiState.update { it.copy(showThreadInfoSheet = true) }
    }

    fun closeThreadInfoSheet() {
        _uiState.update { it.copy(showThreadInfoSheet = false) }
    }

    fun openMoreSheet() {
        _uiState.update { it.copy(showMoreSheet = true) }
    }

    fun closeMoreSheet() {
        _uiState.update { it.copy(showMoreSheet = false) }
    }

    fun openDisplaySettingsSheet() {
        _uiState.update { it.copy(showDisplaySettingsSheet = true) }
    }

    fun closeDisplaySettingsSheet() {
        _uiState.update { it.copy(showDisplaySettingsSheet = false) }
    }

    /**
     * 画像メニューを開いて対象URLを設定する。
     */
    fun openImageMenu(url: String) {
        if (url.isBlank()) {
            // 空URLはメニューを開かない。
            return
        }
        _uiState.update { it.copy(showImageMenuSheet = true, imageMenuTargetUrl = url) }
    }

    /**
     * 画像メニューを閉じて対象URLをクリアする。
     */
    fun closeImageMenu() {
        _uiState.update { it.copy(showImageMenuSheet = false, imageMenuTargetUrl = null) }
    }

    /**
     * 画像URLを対象にNG登録ダイアログを開く。
     */
    fun openImageNgDialog(url: String) {
        if (url.isBlank()) {
            // 空URLはダイアログを開かない。
            return
        }
        _uiState.update { it.copy(showImageNgDialog = true, imageNgTargetUrl = url) }
    }

    /**
     * 画像URLのNG登録ダイアログを閉じて対象URLをクリアする。
     */
    fun closeImageNgDialog() {
        _uiState.update { it.copy(showImageNgDialog = false, imageNgTargetUrl = null) }
    }

    fun updateTextScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setTextScale(scale)
            if (!_uiState.value.isIndividualTextScale) {
                settingsRepository.setBodyTextScale(scale)
                settingsRepository.setHeaderTextScale(scale * 0.85f)
            }
        }
    }

    fun updateIndividualTextScale(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIndividualTextScale(enabled)
            if (!enabled) {
                settingsRepository.setLineHeight(DEFAULT_THREAD_LINE_HEIGHT)
            }
        }
    }

    fun updateHeaderTextScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setHeaderTextScale(scale)
        }
    }

    fun updateBodyTextScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setBodyTextScale(scale)
        }
    }

    fun updateLineHeight(height: Float) {
        viewModelScope.launch {
            settingsRepository.setLineHeight(height)
        }
    }

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
        val boardId = uiState.value.boardInfo.boardId
        if (boardId != 0L) {
            viewModelScope.launch {
                postHistoryRepository.recordIdentity(
                    boardId = boardId,
                    name = name,
                    email = mail
                )
            }
        }
        _postUiState.update { state ->
            state.copy(
                postFormState = state.postFormState.copy(
                    name = name,
                    mail = mail,
                    message = ""
                )
            )
        }
        pendingPost = PendingPost(resNum, message, name, mail)
        reloadThread()
    }

    private fun preparePostIdentityHistory(boardId: Long) {
        prepareIdentityHistory(
            key = POST_IDENTITY_HISTORY_KEY,
            boardId = boardId,
            repository = postHistoryRepository,
            onLastIdentity = { name, mail ->
                _postUiState.update { current ->
                    val form = current.postFormState
                    if (form.name.isEmpty() && form.mail.isEmpty()) {
                        current.copy(
                            postFormState = form.copy(
                                name = name,
                                mail = mail,
                            ),
                        )
                    } else {
                        current
                    }
                }
            },
            onNameSuggestions = { suggestions ->
                _postUiState.update { it.copy(nameHistory = suggestions) }
            },
            onMailSuggestions = { suggestions ->
                _postUiState.update { it.copy(mailHistory = suggestions) }
            },
            nameQueryProvider = { _postUiState.value.postFormState.name },
            mailQueryProvider = { _postUiState.value.postFormState.mail },
        )
    }

    internal fun refreshPostIdentityHistory(type: PostIdentityType) {
        refreshIdentityHistorySuggestions(POST_IDENTITY_HISTORY_KEY, type)
    }

    internal fun deletePostIdentity(type: PostIdentityType, value: String) {
        deleteIdentityHistory(POST_IDENTITY_HISTORY_KEY, postHistoryRepository, type, value)
    }

    fun updateThreadTabInfo(threadId: ThreadId, title: String, resCount: Int) {
        tabCoordinator.updateThreadTabInfo(threadId, title, resCount)
    }

    fun updateThreadScrollPosition(
        threadId: ThreadId,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
        tabCoordinator.updateThreadScrollPosition(threadId, firstVisibleIndex, scrollOffset)
    }

    fun updateThreadLastRead(threadId: ThreadId, lastReadResNo: Int) {
        tabCoordinator.updateThreadLastRead(threadId, lastReadResNo)
    }

    companion object {
        private const val POST_IDENTITY_HISTORY_KEY = "thread_post_identity"
    }
}

@AssistedFactory
interface ThreadViewModelFactory {
    fun create(viewModelKey: String): ThreadViewModel
}
