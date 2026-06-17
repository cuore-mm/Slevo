package com.websarva.wings.android.slevo.ui.thread.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
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
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogStateAdapter
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadReplyPostDialogExecutor
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.PendingThreadPostState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadLoadingSource
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.distinctImageUrls
import com.websarva.wings.android.slevo.ui.util.extractImageUrls
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ThreadViewModel の初期化に必要な入力。
 *
 * スレッド識別子と表示情報を初期化フローで利用する。
 */
data class ThreadInitArgs(
    val threadKey: String,
    val boardInfo: BoardInfo,
    val threadTitle: String?,
)

/**
 * スレッド画面の状態を管理するViewModel。
 *
 * 投稿の表示や操作に関するUI状態を保持・更新する。
 */
class ThreadViewModel @AssistedInject constructor(
    private val boardRepository: BoardRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val postHistoryRepository: PostHistoryRepository,
    private val threadBookmarkRepository: ThreadBookmarkRepository,
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    private val tabsRepository: TabsRepository,
    private val threadTabsCoordinator: ThreadTabsCoordinator,
    private val threadContentLoadUseCase: ThreadContentLoadUseCase,
    private val threadVisiblePostsUseCase: ThreadVisiblePostsUseCase,
    threadReadStateRepository: ThreadReadStateRepository,
    private val postDialogImageUploaderFactory: PostDialogImageUploader.Factory,
    private val postDialogControllerFactory: PostDialogController.Factory,
    private val replyPostDialogExecutor: ThreadReplyPostDialogExecutor,
    private val logger: AppLogger,
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
        stateAdapter = ThreadPostDialogStateAdapter(
            stateReader = { currentThreadSessionState().postDialogState },
            stateUpdater = { transform ->
                updateCurrentThreadSessionState { current ->
                    current.copy(postDialogState = transform(current.postDialogState))
                }
            },
        ),
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
        threadTitle: String?
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
            title = buildInitialThreadTitle(
                boardUrl = args.boardInfo.url,
                threadKey = args.threadKey,
                threadTitle = args.threadTitle,
            ),
            url = args.boardInfo.url
        )
        _uiState.update { state ->
            state.copy(
                boardInfo = args.boardInfo,
                threadInfo = threadInfo,
                postGroups = emptyList(),
                lastLoadedResCount = 0,
                latestArrivalGroupIndex = null,
                imageLoadFailureByUrl = emptyMap(),
            )
        }
        currentThreadId()?.let { threadId ->
            threadTabsCoordinator.updateThreadSessionState(threadId) { state ->
                state.copy(postDialogState = state.postDialogState.copy(namePlaceholder = args.boardInfo.noname))
            }
            syncThreadUiStateFromSession(threadId)
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
                    )
                }
                currentThreadId()?.let { threadId ->
                    threadTabsCoordinator.updateThreadSessionState(threadId) { state ->
                        state.copy(postDialogState = state.postDialogState.copy(namePlaceholder = noname))
                    }
                    syncThreadUiStateFromSession(threadId)
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
            updateCurrentThreadSessionState { state ->
                state.copy(sortType = if (isTree) ThreadSortType.TREE else ThreadSortType.NUMBER)
            }
            startThreadLoad(ThreadLoadingSource.INITIAL)
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
                    title = buildInitialThreadTitle(
                        boardUrl = args.boardInfo.url,
                        threadKey = args.threadKey,
                        threadTitle = args.threadTitle,
                    ),
                    boardName = args.boardInfo.name,
                    boardId = ensuredBoardId
                )
            }
        }

        // URL解析に失敗した場合はタブ追加を行わない。
        val parsed = parseBoardUrl(args.boardInfo.url) ?: return null
        val (host, board) = parsed
        return currentTabs + ThreadTabInfo(
            id = ThreadId.of(host, board, args.threadKey),
            title = buildInitialThreadTitle(
                boardUrl = args.boardInfo.url,
                threadKey = args.threadKey,
                threadTitle = args.threadTitle,
            ),
            boardName = args.boardInfo.name,
            boardUrl = args.boardInfo.url,
            boardId = ensuredBoardId
        )
    }

    /**
     * スレタイトル未取得時の初期表示名を組み立てる。
     *
     * `threadTitle` が空の場合は、正規化済み `boardUrl` と `threadKey` から
     * スレURLを組み立てる。
     */
    private fun buildInitialThreadTitle(
        boardUrl: String,
        threadKey: String,
        threadTitle: String?,
    ): String {
        threadTitle?.takeIf { it.isNotBlank() }?.let { return it }
        // URL解析に失敗した場合は空文字にフォールバックする。
        val parsed = parseBoardUrl(boardUrl) ?: return ""
        val (host, boardKey) = parsed
        return "https://$host/test/read.cgi/$boardKey/$threadKey/"
    }

    override suspend fun loadData(isRefresh: Boolean) {
        val source = if (isRefresh) {
            ThreadLoadingSource.MANUAL
        } else {
            ThreadLoadingSource.INITIAL
        }
        if (uiState.value.loadingSource == ThreadLoadingSource.NONE) {
            startThreadLoad(source)
        }
        val boardUrl = uiState.value.boardInfo.url
        val key = uiState.value.threadInfo.key

        try {
            val derived = threadContentLoadUseCase.load(boardUrl, key) { progress ->
                updateCurrentThreadSessionState { it.copy(loadProgress = progress) }
            }
            if (derived == null) {
                // データ取得に失敗した場合はここで終了する。
                handleLoadFailure(boardUrl, key)
                return
            }
            applyLoadSuccess(derived)
            updatePostGroupsOnLoad(derived.uiPosts)
            updateNgPostNumbers()
            handleHistoryOnLoad(derived.uiPosts, derived.threadTitle)
        } catch (e: Exception) {
            // 例外詳細はログへ出し、ユーザーには短い文言を通知する。
            handleLoadFailure(boardUrl, key, error = e)
        }
    }

    /**
     * 読み込み開始時の UIState を初期化する。
     */
    private fun startThreadLoad(source: ThreadLoadingSource) {
        updateCurrentThreadSessionState {
            it.copy(
                isLoading = true,
                loadProgress = 0f,
                loadingSource = source,
            )
        }
    }

    /**
     * 取得成功時の UIState を一括で更新する。
     */
    private fun applyLoadSuccess(derived: ThreadContentLoadResult) {
        val activeImageUrls = deriveActiveImageUrls(derived.uiPosts)
        val currentState = uiState.value
        val prunedPopupStack = prunePopupStackWithoutRenderablePosts(
                popupStack = currentState.popupStack,
                posts = derived.uiPosts,
                ngPostNumbers = currentState.ngPostNumbers,
            )
        updateCurrentThreadSessionState { session ->
            val nextSession = session.copy(
                isLoading = false,
                loadProgress = 1f,
                loadingSource = ThreadLoadingSource.NONE,
                popupStack = prunedPopupStack,
            )
            nextSession.copy(isTabSwipeEnabled = shouldEnableTabSwipe(nextSession))
        }
        _uiState.update { state ->
            state.copy(
                posts = derived.uiPosts,
                threadInfo = state.threadInfo.copy(
                    title = derived.threadTitle ?: state.threadInfo.title,
                    resCount = derived.resCount,
                    date = derived.threadDate,
                    momentum = derived.momentum
                ),
                idCountMap = derived.idCountMap,
                idIndexList = derived.idIndexList,
                replySourceMap = derived.replySourceMap,
                treeOrder = derived.treeOrder,
                treeDepthMap = derived.treeDepthMap,
                treeRootMap = derived.treeRootMap,
                imageLoadFailureByUrl = state.imageLoadFailureByUrl.filterKeys { url ->
                    url in activeImageUrls
                },
                imageLoadingUrls = state.imageLoadingUrls.filter { url ->
                    url in activeImageUrls
                }.toSet(),
            )
        }
    }

    /**
     * サムネイル画像の読み込み失敗URLを UI 状態へ記録する。
     */
    fun onThreadImageLoadError(imageUrl: String, failureType: ImageLoadFailureType) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(
                imageLoadFailureByUrl = state.imageLoadFailureByUrl +
                        (imageUrl to failureType),
                imageLoadingUrls = state.imageLoadingUrls - imageUrl,
            )
        }
    }

    /**
     * サムネイル画像の読み込み開始URLを読み込み中状態へ追加する。
     */
    fun onThreadImageLoadStart(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは読み込み管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(imageLoadingUrls = state.imageLoadingUrls + imageUrl)
        }
    }

    /**
     * サムネイル画像の読み込み成功URLを失敗状態から解除する。
     */
    fun onThreadImageLoadSuccess(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(
                imageLoadFailureByUrl = state.imageLoadFailureByUrl - imageUrl,
                imageLoadingUrls = state.imageLoadingUrls - imageUrl,
            )
        }
    }

    /**
     * ユーザーの明示リトライ操作に合わせて失敗状態を解除する。
     */
    fun onThreadImageRetry(imageUrl: String) {
        if (imageUrl.isBlank()) {
            // Guard: 空URLは失敗管理対象にしない。
            return
        }
        _uiState.update { state ->
            state.copy(
                imageLoadFailureByUrl = state.imageLoadFailureByUrl - imageUrl,
                imageLoadingUrls = state.imageLoadingUrls - imageUrl,
            )
        }
    }

    /**
     * 投稿一覧から表示対象の画像URL集合を抽出する。
     */
    private fun deriveActiveImageUrls(posts: List<ThreadPostUiModel>): Set<String> {
        return posts
            .asSequence()
            .filter { post -> post.meta.urlFlags and ReplyInfo.HAS_IMAGE_URL != 0 }
            .flatMap { post -> extractImageUrls(post.body.content).asSequence() }
            .filter { url -> url.isNotBlank() }
            .toSet()
    }

    /**
     * 取得失敗時にローディングを解除し、必要ならログを出力する。
     */
    private fun handleLoadFailure(boardUrl: String, key: String, error: Throwable? = null) {
        updateCurrentThreadSessionState {
            it.copy(
                isLoading = false,
                loadProgress = 1f,
                loadingSource = ThreadLoadingSource.NONE,
            )
        }
        if (error != null) {
            logger.e(message = "Failed to load thread data for board: $boardUrl key: $key", throwable = error)

            logger.e("Failed to load thread data for board: $boardUrl key: $key")
        }
        updateCurrentThreadSessionState { it.copy(pendingToastResId = R.string.thread_load_failed) }
    }

    /**
     * 未表示Toastの消費（UI 側で表示後に呼ぶ）。
     */
    fun consumeToast() {
        updateCurrentThreadSessionState { it.copy(pendingToastResId = null) }
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
        val threadId = currentThreadId() ?: return
        val pending = currentThreadRuntimeState(threadId).pendingPost ?: run {
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
        updateThreadRuntimeState(threadId) { it.copy(pendingPost = null) }
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
        val prunedPopupStack = prunePopupStackWithoutRenderablePosts(
            popupStack = uiState.value.popupStack,
            posts = posts,
            ngPostNumbers = ngNumbers,
        )
        _uiState.update { state ->
            state.copy(ngPostNumbers = ngNumbers)
        }
        updateCurrentThreadSessionState { session ->
            val nextSession = session.copy(popupStack = prunedPopupStack)
            nextSession.copy(isTabSwipeEnabled = shouldEnableTabSwipe(nextSession))
        }
        updateDisplayPosts()
    }

    /**
     * 表示不能になったポップアップをスタックから除外する。
     *
     * 投稿番号が最新投稿範囲外、または NG 指定で描画不可な場合は除外し、
     * 1 件も描画可能な投稿番号を持たないポップアップは残さない。
     */
    private fun prunePopupStackWithoutRenderablePosts(
        popupStack: List<PopupInfo>,
        posts: List<ThreadPostUiModel>,
        ngPostNumbers: Set<Int>,
    ): List<PopupInfo> {
        if (popupStack.isEmpty()) {
            return popupStack
        }
        return popupStack.filter { info ->
            info.postNumbers.any { number ->
                number in 1..posts.size && number !in ngPostNumbers
            }
        }
    }

    /**
     * タブ状態の新着境界をUI状態へ反映する。
     */
    fun setNewArrivalInfo(firstNewResNo: Int?, prevResCount: Int) {
        _uiState.update { it.copy(firstNewResNo = firstNewResNo, prevResCount = prevResCount) }
        updateDisplayPosts()
    }

    /**
     * 検索/NGを反映した表示用投稿リストを更新する。
     */
    private fun updateDisplayPosts() {
        val posts = uiState.value.posts ?: return // 投稿未取得時は更新しない。
        val result = threadVisiblePostsUseCase.buildVisibleRows(
            posts = posts,
            groups = uiState.value.postGroups,
            sortType = uiState.value.sortType,
            treeOrder = uiState.value.treeOrder,
            treeDepthMap = uiState.value.treeDepthMap,
            treeRootMap = uiState.value.treeRootMap,
            latestArrivalGroupIndex = uiState.value.latestArrivalGroupIndex,
            searchQuery = uiState.value.searchQuery,
            ngPostNumbers = uiState.value.ngPostNumbers,
            replySourceMap = uiState.value.replySourceMap,
        )

        _uiState.update {
            it.copy(
                visiblePostRows = result.visiblePostRows,
                replyCounts = result.replyCounts,
                firstAfterIndex = result.firstAfterIndex,
            )
        }
    }

    fun reloadThread() {
        startThreadLoad(ThreadLoadingSource.MANUAL)
        initialize(force = true) // 強制的に初期化処理を再実行
    }

    /**
     * 下端プル更新でスレッドを再読み込みする。
     */
    fun reloadThreadFromBottomPull() {
        startThreadLoad(ThreadLoadingSource.BOTTOM_PULL)
        viewModelScope.launch {
            loadData(isRefresh = true)
        }
    }

    fun toggleAutoScroll() {
        val enabled = !uiState.value.isAutoScroll
        updateCurrentThreadSessionState { it.copy(isAutoScroll = enabled) }
        currentThreadId()?.let { threadId ->
            if (!enabled) {
                updateThreadRuntimeState(threadId) { it.copy(lastAutoRefreshTime = 0L) }
            }
        }
    }

    fun onAutoScrollReachedBottom() {
        if (!uiState.value.isAutoScroll) return
        val threadId = currentThreadId() ?: return
        val now = System.currentTimeMillis()
        val runtime = currentThreadRuntimeState(threadId)
        if (runtime.lastAutoRefreshTime == 0L || now - runtime.lastAutoRefreshTime >= 10_000L) {
            updateThreadRuntimeState(threadId) { it.copy(lastAutoRefreshTime = now) }
            startThreadLoad(ThreadLoadingSource.AUTO_SCROLL)
            initialize(force = true)
        }
    }

    fun toggleSortType() {
        updateCurrentThreadSessionState { state ->
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
        updateCurrentThreadSessionState { it.copy(showThreadInfoSheet = true) }
    }

    fun closeThreadInfoSheet() {
        updateCurrentThreadSessionState { it.copy(showThreadInfoSheet = false) }
    }

    fun openMoreSheet() {
        updateCurrentThreadSessionState { it.copy(showMoreSheet = true) }
    }

    fun closeMoreSheet() {
        updateCurrentThreadSessionState { it.copy(showMoreSheet = false) }
    }

    fun openDisplaySettingsSheet() {
        updateCurrentThreadSessionState { it.copy(showDisplaySettingsSheet = true) }
    }

    fun closeDisplaySettingsSheet() {
        updateCurrentThreadSessionState { it.copy(showDisplaySettingsSheet = false) }
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
        updateCurrentThreadSessionState {
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
        updateCurrentThreadSessionState {
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
        updateCurrentThreadSessionState { state ->
            val stack = state.popupStack
            if (index !in stack.indices) {
                // 範囲外の更新は無視する。
                return@updateCurrentThreadSessionState state
            }
            val target = stack[index]
            if (target.size == size) {
                // 変更がない場合は更新しない。
                return@updateCurrentThreadSessionState state
            }
            val updated = stack.toMutableList()
            updated[index] = target.copy(size = size)
            val nextState = state.copy(popupStack = updated)
            nextState.copy(isTabSwipeEnabled = shouldEnableTabSwipe(nextState))
        }
    }

    /**
     * 最上位のポップアップを取り除く。
     */
    fun removeTopPopup() {
        updateCurrentThreadSessionState { state ->
            if (state.popupStack.isEmpty()) {
                // 表示対象がない場合は何もしない。
                return@updateCurrentThreadSessionState state
            }
            val nextState = state.copy(popupStack = state.popupStack.dropLast(1))
            nextState.copy(isTabSwipeEnabled = shouldEnableTabSwipe(nextState))
        }
    }

    /**
     * 返信元番号の投稿をまとめてポップアップとして追加する。
     *
     * NG投稿や範囲外番号は除外する。
     */
    fun addPopupForReplyFrom(baseOffset: IntOffset, replyNumbers: List<Int>) {
        val state = uiState.value
        val posts = state.posts ?: run {
            // 投稿が未取得の場合は追加しない。
            return
        }
        val ngNumbers = state.ngPostNumbers
        val targetNumbers = replyNumbers.filterNot { it in ngNumbers }
            .filter { it in 1..posts.size }
        val rootNumbers = targetNumbers.map { num ->
            state.treeRootMap[num] ?: num
        }
        if (targetNumbers.isEmpty()) {
            // 有効な対象がない場合は追加しない。
            return
        }
        appendPopup(
            PopupInfo(
                popupId = nextPopupId(),
                postNumbers = targetNumbers,
                offset = baseOffset,
                rootNumbers = rootNumbers,
            )
        )
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
        appendPopup(
            PopupInfo(
                popupId = nextPopupId(),
                postNumbers = listOf(postNumber),
                offset = baseOffset,
                rootNumbers = listOf(postNumber),
            )
        )
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
        val targetNumbers = posts.mapIndexedNotNull { idx, post ->
            val num = idx + 1
            if (post.header.id == id && num !in ngNumbers) num else null
        }
        val rootNumbers = targetNumbers.map { num ->
            uiState.value.treeRootMap[num] ?: num
        }
        if (targetNumbers.isEmpty()) {
            // 有効な対象がない場合は追加しない。
            return
        }
        appendPopup(
            PopupInfo(
                popupId = nextPopupId(),
                postNumbers = targetNumbers,
                offset = baseOffset,
                rootNumbers = rootNumbers,
            )
        )
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
        val postNumbers = mutableListOf<Int>()
        val indentLevels = mutableListOf<Int>()
        val rootNumbers = mutableListOf<Int>()
        selection.numbers.zip(selection.indentLevels).forEach { (num, depth) ->
            if (num in state.ngPostNumbers) {
                return@forEach
            }
            posts.getOrNull(num - 1) ?: return@forEach
            val rootNumber = state.treeRootMap[num] ?: num
            postNumbers.add(num)
            indentLevels.add(depth)
            rootNumbers.add(rootNumber)
        }
        if (postNumbers.size <= 1) {
            // NG除外後に単独になった場合は追加しない。
            return
        }

        // --- Append ---
        appendPopup(
            PopupInfo(
                popupId = nextPopupId(),
                postNumbers = postNumbers,
                offset = baseOffset,
                indentLevels = indentLevels,
                rootNumbers = rootNumbers,
            )
        )
    }

    /**
     * ポップアップ用の安定識別子を採番する。
     */
    private fun nextPopupId(): Long {
        val threadId = currentThreadId() ?: return 1L
        var nextId = 1L
        updateThreadRuntimeState(threadId) { state ->
            nextId = state.nextPopupId
            state.copy(nextPopupId = state.nextPopupId + 1)
        }
        return nextId
    }

    private fun appendPopup(info: PopupInfo) {
        updateCurrentThreadSessionState { state ->
            val updatedStack = appendPopupIfDistinct(state.popupStack, info)
            val nextState = state.copy(popupStack = updatedStack)
            nextState.copy(isTabSwipeEnabled = shouldEnableTabSwipe(nextState))
        }
    }


    /**
     * 現在の SessionState からタブ横スワイプ可否を判定する。
     */
    private fun shouldEnableTabSwipe(state: ThreadSessionState): Boolean {
        return !state.isSearchMode && state.popupStack.isEmpty()
    }

    /**
     * 画像保存対象のURLを正規化して返す。
     *
     * 空URLを除外し、重複を除いた順序で返す。
     */
    fun requestImageSave(context: Context, urls: List<String>) {
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
    fun onImageSavePermissionResult(context: Context, granted: Boolean) {
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
    private fun launchImageSave(context: Context, urls: List<String>) {
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
        updateCurrentThreadSessionState { it.copy(showImageNgDialog = true, imageNgTargetUrl = url) }
    }

    /**
     * 画像URLのNG登録ダイアログを閉じて対象URLをクリアする。
     */
    fun closeImageNgDialog() {
        updateCurrentThreadSessionState { it.copy(showImageNgDialog = false, imageNgTargetUrl = null) }
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
        updateCurrentThreadSessionState { state ->
            val nextState = state.copy(isSearchMode = true)
            nextState.copy(isTabSwipeEnabled = shouldEnableTabSwipe(nextState))
        }
        updateDisplayPosts()
    }

    fun closeSearch() {
        updateCurrentThreadSessionState { state ->
            val nextState = state.copy(isSearchMode = false, searchInputValue = TextFieldValue(""))
            nextState.copy(isTabSwipeEnabled = shouldEnableTabSwipe(nextState))
        }
        updateDisplayPosts()
    }

    /**
     * 検索入力状態を更新し、表示中の投稿リストへ再反映する。
     */
    fun updateSearchInput(inputValue: TextFieldValue) {
        updateCurrentThreadSessionState { it.copy(searchInputValue = inputValue) }
        updateDisplayPosts()
    }

    fun updateSearchQuery(query: String) {
        updateSearchInput(TextFieldValue(query))
    }

    /**
     * 投稿成功時に画面固有の後処理を実行する。
     */
    fun onPostSuccess(resNum: Int?, message: String, name: String, mail: String) {
        currentThreadId()?.let { threadId ->
            updateThreadRuntimeState(threadId) {
                it.copy(
                    pendingPost = PendingThreadPostState(resNum, message, name, mail),
                )
            }
        }
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

    /**
     * 現在表示中のスレッドタブ ID を返す。
     */
    private fun currentThreadId(): ThreadId? {
        val boardUrl = uiState.value.boardInfo.url
        val threadKey = uiState.value.threadInfo.key
        if (boardUrl.isBlank() || threadKey.isBlank()) {
            return null
        }
        val parsed = parseBoardUrl(boardUrl) ?: return null
        val (host, board) = parsed
        return ThreadId.of(host, board, threadKey)
    }

    /**
     * 現在表示中のスレッドタブの SessionState を返す。
     */
    private fun currentThreadSessionState(): ThreadSessionState {
        val threadId = currentThreadId() ?: return ThreadSessionState()
        return threadTabsCoordinator.getThreadSessionState(threadId)
    }

    /**
     * 現在表示中のスレッドタブの継続ランタイム状態を返す。
     */
    private fun currentThreadRuntimeState(threadId: ThreadId): ThreadSessionRuntimeState {
        return threadTabsCoordinator.getThreadRuntimeState(threadId)
    }

    /**
     * 現在表示中のスレッドタブの SessionState を更新し、UiState へ同期する。
     */
    private fun updateCurrentThreadSessionState(
        transform: (ThreadSessionState) -> ThreadSessionState,
    ) {
        val threadId = currentThreadId() ?: return
        threadTabsCoordinator.updateThreadSessionState(threadId, transform)
        syncThreadUiStateFromSession(threadId)
    }

    /**
     * 指定スレッドタブの継続ランタイム状態を更新する。
     */
    private fun updateThreadRuntimeState(
        threadId: ThreadId,
        transform: (ThreadSessionRuntimeState) -> ThreadSessionRuntimeState,
    ) {
        threadTabsCoordinator.updateThreadRuntimeState(threadId, transform)
    }

    /**
     * 指定スレッドタブの SessionState を UiState の読み取り用フィールドへ反映する。
     */
    private fun syncThreadUiStateFromSession(threadId: ThreadId) {
        val session = threadTabsCoordinator.getThreadSessionState(threadId)
        _uiState.update { state ->
            state.copy(
                loadProgress = session.loadProgress,
                isLoading = session.isLoading,
                loadingSource = session.loadingSource,
                postDialogState = session.postDialogState,
                showThreadInfoSheet = session.showThreadInfoSheet,
                showMoreSheet = session.showMoreSheet,
                showDisplaySettingsSheet = session.showDisplaySettingsSheet,
                showImageMenuSheet = session.showImageMenuSheet,
                imageMenuTargetUrl = session.imageMenuTargetUrl,
                imageMenuTargetUrls = session.imageMenuTargetUrls,
                showImageNgDialog = session.showImageNgDialog,
                imageNgTargetUrl = session.imageNgTargetUrl,
                popupStack = session.popupStack,
                searchInputValue = session.searchInputValue,
                isSearchMode = session.isSearchMode,
                sortType = session.sortType,
                isAutoScroll = session.isAutoScroll,
                pendingToastResId = session.pendingToastResId,
                isTabSwipeEnabled = session.isTabSwipeEnabled,
            )
        }
    }
}

/**
 * 現在のポップアップスタックへ新しいポップアップを追加する。
 *
 * 直前の最上位ポップアップと表示内容が同一の場合は連続表示を抑止し、
 * 既存スタックをそのまま返す。
 */
internal fun appendPopupIfDistinct(
    stack: List<PopupInfo>,
    candidate: PopupInfo,
): List<PopupInfo> {
    val top = stack.lastOrNull() ?: return stack + candidate
    if (isSamePopupContent(top, candidate)) {
        // 連続で同一内容を開こうとした場合は積み上げない。
        return stack
    }
    return stack + candidate
}

/**
 * 2つのポップアップが同一表示内容かを判定する。
 *
 * `popupId` やレイアウト情報ではなく、表示対象投稿とツリーインデントの一致で比較する。
 */
internal fun isSamePopupContent(
    left: PopupInfo,
    right: PopupInfo,
): Boolean {
    return left.postNumbers == right.postNumbers &&
            left.indentLevels == right.indentLevels &&
            left.rootNumbers == right.rootNumbers
}

/**
 * Thread画面の投稿状態をPostDialogStateへ橋渡しするアダプタ。
 *
 * Thread タブの SessionState 上にある PostDialogState を読み書きし、
 * 共通コントローラの更新結果を現在タブへ反映する。
 */
private class ThreadPostDialogStateAdapter(
    private val stateReader: () -> PostDialogState,
    private val stateUpdater: ((PostDialogState) -> PostDialogState) -> Unit,
) : PostDialogStateAdapter {

    /**
     * 現在タブの PostDialogState を取得する。
     */
    override fun readState(): PostDialogState {
        return stateReader.invoke()
    }

    /**
     * PostDialogState の更新結果を現在タブの SessionState へ反映する。
     */
    override fun updateState(transform: (PostDialogState) -> PostDialogState) {
        stateUpdater.invoke(transform)
    }
}

/**
 * ThreadViewModel を生成するためのファクトリ。
 */
@AssistedFactory
interface ThreadViewModelFactory {
    fun create(viewModelKey: String): ThreadViewModel
}
