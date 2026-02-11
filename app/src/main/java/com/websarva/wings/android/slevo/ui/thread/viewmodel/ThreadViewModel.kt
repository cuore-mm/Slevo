package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import android.content.Context
import android.net.Uri
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.ui.bbsroute.BaseViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.common.bookmark.ThreadTarget
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveCoordinator
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSavePreparation
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveSummary
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogStateAdapter
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadReplyPostDialogExecutor
import com.websarva.wings.android.slevo.ui.util.distinctImageUrls
import com.websarva.wings.android.slevo.ui.util.toHiragana
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.data.util.ThreadListParser.calculateThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
 * loadData 成功時に必要な派生情報をまとめたコンテナ。
 *
 * 投稿一覧・派生マップ・スレ情報の更新に必要な値を保持する。
 */
private data class ThreadLoadDerived(
    val uiPosts: List<ThreadPostUiModel>,
    val threadTitle: String?,
    val resCount: Int,
    val threadDate: ThreadDate,
    val momentum: Double,
    val idCountMap: Map<String, Int>,
    val idIndexList: List<Int>,
    val replySourceMap: Map<Int, List<Int>>,
    val treeOrder: List<Int>,
    val treeDepthMap: Map<Int, Int>,
)

/**
 * 画像保存の成功/失敗件数を表す結果。
 */
data class ImageSaveSummary(
    val successCount: Int,
    val failureCount: Int,
)

/**
 * ThreadViewModel の初期化に必要な入力。
 *
 * スレッド識別子と表示情報を初期化フローで利用する。
 */
data class ThreadInitArgs(
    val threadKey: String,
    val boardInfo: BoardInfo,
    val threadTitle: String,
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
    private val postDialogImageUploaderFactory: PostDialogImageUploader.Factory,
    private val postDialogControllerFactory: PostDialogController.Factory,
    private val replyPostDialogExecutor: ThreadReplyPostDialogExecutor,
    @Assisted @Suppress("unused") val viewModelKey: String,
) : BaseViewModel<ThreadUiState, ThreadInitArgs>() {

    private val tabCoordinator = ThreadTabCoordinator(
        scope = viewModelScope,
        tabsRepository = tabsRepository,
        readStateRepository = threadReadStateRepository,
    )

    override val _uiState = MutableStateFlow(ThreadUiState())
    private var ngList: List<NgEntity> = emptyList()
    private var compiledNg: List<Triple<Long?, Regex, NgType>> = emptyList()
    private var pendingPost: PendingPost? = null
    private val imageSaveCoordinator = ImageSaveCoordinator()
    private val _imageSaveEvents = MutableSharedFlow<ImageSaveUiEvent>(extraBufferCapacity = 1)
    val imageSaveEvents: SharedFlow<ImageSaveUiEvent> = _imageSaveEvents.asSharedFlow()
    private var observedThreadHistoryId: Long? = null
    private var postHistoryCollectJob: Job? = null
    private var bookmarkStatusJob: Job? = null
    val bookmarkSheetHolder = bookmarkSheetStateHolderFactory.create(viewModelScope)
    private val postDialogImageUploader = postDialogImageUploaderFactory.create(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
    )
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
        viewModelScope.launch {
            bookmarkSheetHolder.uiState.collect { sheetState ->
                _uiState.update { it.copy(bookmarkSheetState = sheetState) }
            }
        }
    }

    internal val postDialogController = postDialogControllerFactory.create(
        scope = viewModelScope,
        stateAdapter = ThreadPostDialogStateAdapter(_uiState),
        identityHistoryKey = POST_IDENTITY_HISTORY_KEY,
        executor = replyPostDialogExecutor,
        boardIdProvider = { uiState.value.boardInfo.boardId },
        onPostSuccess = { success ->
            onPostSuccess(
                success.resNum,
                success.message,
                success.name,
                success.mail,
            )
        },
    )

    /**
     * PostDialogの操作をUIへ公開する。
     */
    val postDialogActions: PostDialogController
        get() = postDialogController

    /**
     * 画面遷移時の初期処理を行う。
     */
    fun initializeThread(
        threadKey: String,
        boardInfo: BoardInfo,
        threadTitle: String
    ) {
        initializeFlow(
            ThreadInitArgs(
                threadKey = threadKey,
                boardInfo = boardInfo,
                threadTitle = threadTitle,
            )
        )
    }

    /**
     * 初期化キーを作成する。
     */
    override fun buildInitKey(args: ThreadInitArgs): String {
        return "${args.threadKey}|${args.boardInfo.url}"
    }

    /**
     * UIState にスレッド情報を反映する。
     */
    override fun applyInitialUiState(args: ThreadInitArgs) {
        val threadInfo = ThreadInfo(
            key = args.threadKey,
            title = args.threadTitle,
            url = args.boardInfo.url
        )
        _uiState.update { state ->
            state.copy(
                boardInfo = args.boardInfo,
                threadInfo = threadInfo,
                postDialogState = state.postDialogState.copy(namePlaceholder = args.boardInfo.noname),
                postGroups = emptyList(),
                lastLoadedResCount = 0,
                latestArrivalGroupIndex = null,
            )
        }
    }

    /**
     * タブ情報とBoard情報の補完処理を開始する。
     */
    override fun launchDataComplement(args: ThreadInitArgs) {
        viewModelScope.launch {
            val ensuredId = boardRepository.ensureBoard(args.boardInfo)
            _uiState.update { state ->
                state.copy(boardInfo = state.boardInfo.copy(boardId = ensuredId))
            }

            val currentTabs = tabsRepository.observeOpenThreadTabs().first()
            val updatedTabs = updateThreadTabs(
                currentTabs = currentTabs,
                ensuredBoardId = ensuredId,
                args = args,
            )
            if (updatedTabs != null) {
                tabsRepository.saveOpenThreadTabs(updatedTabs)
            }

            boardRepository.fetchBoardNoname("${args.boardInfo.url}SETTING.TXT")?.let { noname ->
                _uiState.update { state ->
                    state.copy(
                        boardInfo = state.boardInfo.copy(noname = noname),
                        postDialogState = state.postDialogState.copy(namePlaceholder = noname)
                    )
                }
            }
            postDialogController.prepareIdentityHistory(ensuredId)
        }
    }

    /**
     * ブックマーク・NG監視を開始する。
     */
    override fun startObservers(args: ThreadInitArgs) {
        bookmarkStatusJob?.cancel()
        bookmarkStatusJob = viewModelScope.launch {
            threadBookmarkRepository.getBookmarkWithGroup(args.threadKey, args.boardInfo.url)
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
    }

    /**
     * 並び順の設定を反映して初期ロードを開始する。
     */
    override fun startInitialLoad(force: Boolean) {
        viewModelScope.launch {
            val isTree = settingsRepository.observeIsTreeSort().first()
            _uiState.update { state ->
                state.copy(sortType = if (isTree) ThreadSortType.TREE else ThreadSortType.NUMBER)
            }
            initialize(force)
        }
    }

    /**
     * スレッドタブの状態を更新する。
     */
    private fun updateThreadTabs(
        currentTabs: List<ThreadTabInfo>,
        ensuredBoardId: Long,
        args: ThreadInitArgs,
    ): List<ThreadTabInfo>? {
        // --- Update existing ---
        val tabIndex = currentTabs.indexOfFirst {
            it.threadKey == args.threadKey && it.boardUrl == args.boardInfo.url
        }
        if (tabIndex != -1) {
            return currentTabs.toMutableList().apply {
                this[tabIndex] = this[tabIndex].copy(
                    title = args.threadTitle,
                    boardName = args.boardInfo.name,
                    boardId = ensuredBoardId
                )
            }
        }

        // --- Add new ---
        val parsed = parseBoardUrl(args.boardInfo.url)
        if (parsed == null) {
            // URL解析に失敗した場合はタブ追加を行わない。
            return null
        }
        val (host, board) = parsed
        return currentTabs + ThreadTabInfo(
            id = ThreadId.of(host, board, args.threadKey),
            title = args.threadTitle,
            boardName = args.boardInfo.name,
            boardUrl = args.boardInfo.url,
            boardId = ensuredBoardId
        )
    }

    override suspend fun loadData(isRefresh: Boolean) {
        startThreadLoad()
        val boardUrl = uiState.value.boardInfo.url
        val key = uiState.value.threadInfo.key

        try {
            val threadData = fetchThreadData(boardUrl, key)
            if (threadData == null) {
                // データ取得に失敗した場合はここで終了する。
                handleLoadFailure(boardUrl, key, shouldLog = true)
                return
            }
            val derived = buildThreadLoadDerived(threadData, key)
            applyLoadSuccess(derived)
            updatePostGroupsOnLoad(derived.uiPosts)
            updateNgPostNumbers()
            handleHistoryOnLoad(derived.uiPosts, derived.threadTitle)
        } catch (_: Exception) {
            // 例外時はログを出さずにローディングを解除する。
            handleLoadFailure(boardUrl, key, shouldLog = false)
        }
    }

    /**
     * 読み込み開始時の UIState を初期化する。
     */
    private fun startThreadLoad() {
        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
    }

    /**
     * dat 取得を行い、進捗を UIState に反映する。
     */
    private suspend fun fetchThreadData(
        boardUrl: String,
        key: String,
    ): Pair<List<ReplyInfo>, String?>? {
        return datRepository.getThread(boardUrl, key) { progress ->
            _uiState.update { it.copy(loadProgress = progress) }
        }
    }

    /**
     * dat 取得結果から UI 反映に必要な派生情報を構築する。
     */
    private fun buildThreadLoadDerived(
        threadData: Pair<List<ReplyInfo>, String?>,
        key: String,
    ): ThreadLoadDerived {
        // --- 投稿一覧の変換 ---
        val (posts, title) = threadData
        val uiPosts = posts.map { it.toThreadPostUiModel() }

        // --- 派生マップ ---
        val (idCountMap, idIndexList, replySourceMap) = deriveReplyMaps(uiPosts)
        val (treeOrder, treeDepthMap) = deriveTreeOrder(uiPosts)

        // --- スレ情報 ---
        val resCount = uiPosts.size
        val keyLong = key.toLongOrNull()
        val threadDate = if (keyLong != null && keyLong in 1 until THREAD_KEY_THRESHOLD) {
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

        return ThreadLoadDerived(
            uiPosts = uiPosts,
            threadTitle = title,
            resCount = resCount,
            threadDate = threadDate,
            momentum = momentum,
            idCountMap = idCountMap,
            idIndexList = idIndexList,
            replySourceMap = replySourceMap,
            treeOrder = treeOrder,
            treeDepthMap = treeDepthMap,
        )
    }

    /**
     * 取得成功時の UIState を一括で更新する。
     */
    private fun applyLoadSuccess(derived: ThreadLoadDerived) {
        _uiState.update {
            it.copy(
                posts = derived.uiPosts,
                isLoading = false,
                loadProgress = 1f,
                threadInfo = it.threadInfo.copy(
                    title = derived.threadTitle ?: it.threadInfo.title,
                    resCount = derived.resCount,
                    date = derived.threadDate,
                    momentum = derived.momentum
                ),
                idCountMap = derived.idCountMap,
                idIndexList = derived.idIndexList,
                replySourceMap = derived.replySourceMap,
                treeOrder = derived.treeOrder,
                treeDepthMap = derived.treeDepthMap,
            )
        }
    }

    /**
     * 取得失敗時にローディングを解除し、必要ならログを出力する。
     */
    private fun handleLoadFailure(boardUrl: String, key: String, shouldLog: Boolean) {
        _uiState.update { it.copy(isLoading = false, loadProgress = 1f) }
        if (shouldLog) {
            Timber.e("Failed to load thread data for board: $boardUrl key: $key")
        }
    }

    /**
     * 履歴記録・投稿番号監視・保留投稿の記録をまとめて処理する。
     */
    private suspend fun handleHistoryOnLoad(uiPosts: List<ThreadPostUiModel>, title: String?) {
        // --- スレ履歴の記録 ---
        val historyId = historyRepository.recordHistory(
            uiState.value.boardInfo,
            uiState.value.threadInfo.copy(title = title ?: uiState.value.threadInfo.title),
            uiPosts.size
        )

        // --- 自分の投稿番号の監視 ---
        updateMyPostNumbers(historyId)

        // --- 保留投稿の記録 ---
        recordPendingPost(uiPosts, historyId)
    }

    /**
     * 履歴 ID が変わった場合のみ自分の投稿番号監視を再登録する。
     */
    private fun updateMyPostNumbers(historyId: Long) {
        if (observedThreadHistoryId == historyId) {
            // 既に同じ履歴IDを監視中なら更新しない。
            return
        }
        observedThreadHistoryId = historyId
        postHistoryCollectJob?.cancel()
        postHistoryCollectJob = viewModelScope.launch {
            postHistoryRepository.observeMyPostNumbers(historyId).collect { nums ->
                _uiState.update { it.copy(myPostNumbers = nums) }
            }
        }
    }

    /**
     * 保留投稿があれば履歴に記録し、保留状態をクリアする。
     */
    private suspend fun recordPendingPost(uiPosts: List<ThreadPostUiModel>, historyId: Long) {
        val pending = pendingPost ?: run {
            // 保留投稿が無い場合は何もしない。
            return
        }
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

    /**
     * 取得済みレス数の差分から新着グループを更新する。
     *
     * 初回は全件を1グループとして保持し、以降は差分のみを末尾へ追加する。
     */
    private fun updatePostGroupsOnLoad(posts: List<ThreadPostUiModel>) {
        val newResCount = posts.size
        val currentState = uiState.value
        val prevResCount = currentState.lastLoadedResCount
        val currentGroups = currentState.postGroups

        // --- 初期化/リセット ---
        val needsReset = prevResCount == 0 || currentGroups.isEmpty() || newResCount < prevResCount
        if (newResCount == 0 || needsReset) {
            val nextGroups = if (newResCount > 0) {
                listOf(
                    ThreadPostGroup(
                        startResNo = 1,
                        endResNo = newResCount,
                        prevResCount = 0
                    )
                )
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    postGroups = nextGroups,
                    lastLoadedResCount = newResCount,
                    latestArrivalGroupIndex = null
                )
            }
            // 初期化/リセット時はここで終了する。
            return
        }

        // --- 差分追加 ---
        if (newResCount > prevResCount) {
            val nextGroups = currentGroups + ThreadPostGroup(
                startResNo = prevResCount + 1,
                endResNo = newResCount,
                prevResCount = prevResCount
            )
            _uiState.update {
                it.copy(
                    postGroups = nextGroups,
                    lastLoadedResCount = newResCount,
                    latestArrivalGroupIndex = nextGroups.lastIndex
                )
            }
        } else {
            // 新着がない場合はバーを非表示にする。
            _uiState.update {
                it.copy(
                    lastLoadedResCount = newResCount,
                    latestArrivalGroupIndex = null
                )
            }
        }
    }

    /**
     * NG設定を元に非表示レス番号を更新する。
     */
    private fun updateNgPostNumbers() {
        val posts = uiState.value.posts ?: return // 投稿未取得時はNG判定を行わない。
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

    /**
     * タブ状態の新着境界をUI状態へ反映する。
     */
    fun setNewArrivalInfo(firstNewResNo: Int?, prevResCount: Int) {
        _uiState.update { it.copy(firstNewResNo = firstNewResNo, prevResCount = prevResCount) }
        updateDisplayPosts()
    }

    /**
     * グループ情報から表示対象の投稿リストを組み立てる。
     *
     * 最新グループにのみ isAfter を付与し、新着バー表示位置の基準とする。
     */
    private fun buildGroupedDisplayPosts(
        posts: List<ThreadPostUiModel>,
        groups: List<ThreadPostGroup>,
        sortType: ThreadSortType,
        treeOrder: List<Int>,
        treeDepthMap: Map<Int, Int>,
        latestArrivalGroupIndex: Int?
    ): List<DisplayPost> {
        // --- グループ毎の変換 ---
        val result = mutableListOf<DisplayPost>()
        groups.forEachIndexed { index, group ->
            val endResNo = group.endResNo.coerceAtMost(posts.size)
            if (endResNo <= 0 || group.startResNo > endResNo) {
                // 無効な範囲はスキップする。
                return@forEachIndexed
            }
            val targetPosts = posts.take(endResNo)
            val order = if (sortType == ThreadSortType.TREE && treeOrder.isNotEmpty()) {
                treeOrder.filter { it <= endResNo }
            } else {
                (1..endResNo).toList()
            }
            val firstNewResNo = if (group.prevResCount == 0) null else group.startResNo
            val groupPosts = buildGroupDisplayPosts(
                posts = targetPosts,
                order = order,
                sortType = sortType,
                treeDepthMap = treeDepthMap,
                firstNewResNo = firstNewResNo,
                prevResCount = group.prevResCount
            )
            val markAsAfter = latestArrivalGroupIndex != null && index == latestArrivalGroupIndex
            val adjusted = groupPosts.map { post ->
                post.copy(isAfter = markAsAfter)
            }
            result.addAll(adjusted)
        }
        return result
    }

    /**
     * 検索/NGを反映した表示用投稿リストを更新する。
     */
    private fun updateDisplayPosts() {
        val posts = uiState.value.posts ?: return // 投稿未取得時は更新しない。
        // --- グループ反映 ---
        val groupedPosts = buildGroupedDisplayPosts(
            posts = posts,
            groups = uiState.value.postGroups,
            sortType = uiState.value.sortType,
            treeOrder = uiState.value.treeOrder,
            treeDepthMap = uiState.value.treeDepthMap,
            latestArrivalGroupIndex = uiState.value.latestArrivalGroupIndex
        )

        // --- 検索フィルタ ---
        val query = uiState.value.searchQuery.toHiragana()
        val filteredPosts = if (query.isNotBlank()) {
            groupedPosts.filter {
                it.post.body.content.toHiragana().contains(
                    query,
                    ignoreCase = true
                )
            }
        } else {
            groupedPosts
        }
        // --- NGフィルタ ---
        val visiblePosts = filteredPosts.filterNot { it.num in uiState.value.ngPostNumbers }
        // --- 返信数と新着位置 ---
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
     * ブックマークシートを開く。
     */
    fun openBookmarkSheet() {
        val boardInfo = uiState.value.boardInfo
        val threadInfo = uiState.value.threadInfo
        if (boardInfo.url.isBlank() || threadInfo.key.isBlank()) {
            // 必要情報が欠けている場合はシートを開かない。
            return
        }

        val targets = listOf(
            ThreadTarget(
                boardInfo = boardInfo,
                threadInfo = threadInfo,
                currentGroupId = uiState.value.bookmarkStatusState.selectedGroup?.id
            )
        )
        bookmarkSheetHolder.open(targets)
    }

    /**
     * ViewModel破棄時にステートホルダーのジョブを解放する。
     */
    override fun onCleared() {
        bookmarkSheetHolder.dispose()
        super.onCleared()
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
     * 画像メニューを開いて対象URLとレス内画像一覧を設定する。
     */
    fun openImageMenu(url: String, imageUrls: List<String>) {
        if (url.isBlank()) {
            // 空URLはメニューを開かない。
            return
        }
        val menuUrls = buildImageMenuUrls(url, imageUrls)
        _uiState.update {
            it.copy(
                showImageMenuSheet = true,
                imageMenuTargetUrl = url,
                imageMenuTargetUrls = menuUrls,
            )
        }
    }

    /**
     * 画像メニューを閉じて対象URLをクリアする。
     */
    fun closeImageMenu() {
        _uiState.update {
            it.copy(
                showImageMenuSheet = false,
                imageMenuTargetUrl = null,
                imageMenuTargetUrls = emptyList(),
            )
        }
    }

    /**
     * ポップアップのレイアウトサイズをUI状態へ反映する。
     *
     * サイズが変わらない場合は更新しない。
     */
    fun updatePopupSize(index: Int, size: IntSize) {
        _uiState.update { state ->
            val stack = state.popupStack
            if (index !in stack.indices) {
                // 範囲外の更新は無視する。
                return@update state
            }
            val target = stack[index]
            if (target.size == size) {
                // 変更がない場合は更新しない。
                return@update state
            }
            val updated = stack.toMutableList()
            updated[index] = target.copy(size = size)
            state.copy(popupStack = updated)
        }
    }

    /**
     * 最上位のポップアップを取り除く。
     */
    fun removeTopPopup() {
        _uiState.update { state ->
            if (state.popupStack.isEmpty()) {
                // 表示対象がない場合は何もしない。
                return@update state
            }
            state.copy(popupStack = state.popupStack.dropLast(1))
        }
    }

    /**
     * 返信元番号の投稿をまとめてポップアップとして追加する。
     *
     * NG投稿や範囲外番号は除外する。
     */
    fun addPopupForReplyFrom(baseOffset: IntOffset, replyNumbers: List<Int>) {
        val posts = uiState.value.posts ?: run {
            // 投稿が未取得の場合は追加しない。
            return
        }
        val ngNumbers = uiState.value.ngPostNumbers
        val targets = replyNumbers.filterNot { it in ngNumbers }
            .mapNotNull { num -> posts.getOrNull(num - 1) }
        if (targets.isEmpty()) {
            // 有効な対象がない場合は追加しない。
            return
        }
        appendPopup(PopupInfo(posts = targets, offset = baseOffset))
    }

    /**
     * 指定された返信番号の投稿をポップアップとして追加する。
     *
     * 範囲外番号やNG投稿は無視する。
     */
    fun addPopupForReplyNumber(baseOffset: IntOffset, postNumber: Int) {
        val posts = uiState.value.posts ?: run {
            // 投稿が未取得の場合は追加しない。
            return
        }
        val ngNumbers = uiState.value.ngPostNumbers
        if (postNumber !in 1..posts.size || postNumber in ngNumbers) {
            // 無効な番号またはNG投稿は追加しない。
            return
        }
        appendPopup(PopupInfo(posts = listOf(posts[postNumber - 1]), offset = baseOffset))
    }

    /**
     * 指定IDの投稿を抽出し、ポップアップとして追加する。
     *
     * NG投稿は除外する。
     */
    fun addPopupForId(baseOffset: IntOffset, id: String) {
        val posts = uiState.value.posts ?: run {
            // 投稿が未取得の場合は追加しない。
            return
        }
        val ngNumbers = uiState.value.ngPostNumbers
        val targets = posts.mapIndexedNotNull { idx, post ->
            val num = idx + 1
            if (post.header.id == id && num !in ngNumbers) post else null
        }
        if (targets.isEmpty()) {
            // 有効な対象がない場合は追加しない。
            return
        }
        appendPopup(PopupInfo(posts = targets, offset = baseOffset))
    }

    /**
     * 指定レスが属するツリー全体をポップアップとして追加する。
     *
     * NG除外後に単独レスのみの場合は表示しない。
     */
    fun addPopupForTree(baseOffset: IntOffset, postNumber: Int) {
        val state = uiState.value
        val posts = state.posts ?: run {
            // 投稿が未取得の場合は追加しない。
            return
        }

        // --- Selection ---
        val selection = deriveTreePopupSelection(
            postNumber = postNumber,
            treeOrder = state.treeOrder,
            treeDepthMap = state.treeDepthMap,
        ) ?: run {
            // 対象ツリーがない場合は追加しない。
            return
        }

        // --- Build targets ---
        val targets = mutableListOf<ThreadPostUiModel>()
        val indentLevels = mutableListOf<Int>()
        selection.numbers.zip(selection.indentLevels).forEach { (num, depth) ->
            if (num in state.ngPostNumbers) {
                return@forEach
            }
            val post = posts.getOrNull(num - 1) ?: return@forEach
            targets.add(post)
            indentLevels.add(depth)
        }
        if (targets.size <= 1) {
            // NG除外後に単独になった場合は追加しない。
            return
        }

        // --- Append ---
        appendPopup(
            PopupInfo(
                posts = targets,
                offset = baseOffset,
                indentLevels = indentLevels,
            )
        )
    }

    private fun appendPopup(info: PopupInfo) {
        _uiState.update { state ->
            state.copy(popupStack = state.popupStack + info)
        }
    }

    /**
     * 画像保存対象のURLを正規化して返す。
     *
     * 空URLを除外し、重複を除いた順序で返す。
     */
    fun requestImageSave(context: android.content.Context, urls: List<String>) {
        when (val preparation = imageSaveCoordinator.prepareSave(context, urls)) {
            ImageSavePreparation.Ignore -> Unit
            is ImageSavePreparation.RequestPermission -> {
                _imageSaveEvents.tryEmit(ImageSaveUiEvent.RequestPermission(preparation.permission))
            }

            is ImageSavePreparation.ReadyToSave -> {
                launchImageSave(context, preparation.urls)
            }
        }
    }

    /**
     * 権限要求の結果を受け取り、許可時は保留していた保存処理を再開する。
     */
    fun onImageSavePermissionResult(context: android.content.Context, granted: Boolean) {
        if (!granted) {
            imageSaveCoordinator.clearPendingUrls()
            _imageSaveEvents.tryEmit(
                ImageSaveUiEvent.ShowToast(
                    imageSaveCoordinator.buildPermissionDeniedMessage(context)
                )
            )
            return
        }
        val pendingUrls = imageSaveCoordinator.consumePendingUrls()
        if (pendingUrls.isEmpty()) {
            return
        }
        launchImageSave(context, pendingUrls)
    }

    /**
     * 指定URL一覧の保存処理を実行し、進行中通知と結果通知イベントを発行する。
     */
    private fun launchImageSave(context: android.content.Context, urls: List<String>) {
        if (urls.isEmpty()) {
            // Guard: 空URL一覧では保存処理を開始しない。
            return
        }
        _imageSaveEvents.tryEmit(
            ImageSaveUiEvent.ShowToast(imageSaveCoordinator.buildInProgressMessage(context))
        )
        viewModelScope.launch {
            val summary = imageSaveCoordinator.saveImageUrls(context, urls)
            val resultMessage = imageSaveCoordinator.buildResultMessage(
                context = context,
                requestCount = urls.size,
                summary = summary,
            )
            _imageSaveEvents.emit(ImageSaveUiEvent.ShowToast(resultMessage))
        }
    }

    /**
     * 画像メニューで扱うURL一覧を整形する。
     *
     * 空URLは除外し、重複を取り除いたうえで長押し対象を先頭に揃える。
     */
    private fun buildImageMenuUrls(primaryUrl: String, imageUrls: List<String>): List<String> {
        // --- 正規化 ---
        val normalized = distinctImageUrls(imageUrls)
            .filter { it.isNotBlank() }
            .toMutableList()

        // --- フォールバック ---
        if (primaryUrl.isNotBlank() && primaryUrl !in normalized) {
            normalized.add(0, primaryUrl)
        }
        return normalized
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

    /**
     * 投稿成功時に画面固有の後処理を実行する。
     */
    fun onPostSuccess(resNum: Int?, message: String, name: String, mail: String) {
        pendingPost = PendingPost(resNum, message, name, mail)
        reloadThread()
    }

    /**
     * 画像をアップロードし、成功時に本文へURLを挿入する。
     */
    fun uploadImage(context: Context, uri: Uri) {
        postDialogImageUploader.uploadImage(context, uri) { url ->
            postDialogActions.appendImageUrl(url)
        }
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

    /**
     * 投稿履歴の識別キーを定義する。
     */
    companion object {
        private const val POST_IDENTITY_HISTORY_KEY = "thread_post_identity"
    }
}

/**
 * Thread画面の投稿状態をPostDialogStateへ橋渡しするアダプタ。
 *
 * ThreadUiState.postDialogStateを読み書きし、共通コントローラの更新を反映する。
 */
private class ThreadPostDialogStateAdapter(
    private val stateFlow: MutableStateFlow<ThreadUiState>,
) : PostDialogStateAdapter {

    /**
     * 現在のThreadUiStateからPostDialogStateを取得する。
     */
    override fun readState(): PostDialogState {
        return stateFlow.value.postDialogState
    }

    /**
     * PostDialogStateの更新結果をThreadUiStateへ反映する。
     */
    override fun updateState(transform: (PostDialogState) -> PostDialogState) {
        stateFlow.update { current ->
            current.copy(
                postDialogState = transform(current.postDialogState),
            )
        }
    }
}

/**
 * ThreadViewModel を生成するためのファクトリ。
 */
@AssistedFactory
interface ThreadViewModelFactory {
    fun create(viewModelKey: String): ThreadViewModel
}
