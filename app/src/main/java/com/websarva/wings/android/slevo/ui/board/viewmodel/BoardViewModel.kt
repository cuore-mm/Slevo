package com.websarva.wings.android.slevo.ui.board.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.ui.bbsroute.BaseViewModel
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.common.bookmark.BoardTarget
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogStateAdapter
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadCreatePostDialogExecutor
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.websarva.wings.android.slevo.core.log.AppLogger

/**
 * BoardViewModel の初期化に必要な入力。
 *
 * BoardInfo を初期化フローで利用する。
 */
data class BoardInitArgs(
    val boardInfo: BoardInfo,
)

/**
 * 板画面の表示と操作を担うViewModel。
 *
 * スレッド一覧やブックマーク状態などのUI状態を管理する。
 */
@Suppress("unused")
class BoardViewModel @AssistedInject constructor(
    private val repository: BoardRepository,
    private val bookmarkBoardRepository: BookmarkBoardRepository,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    private val boardTabsCoordinator: BoardTabsCoordinator,
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
    threadListCoordinatorFactory: ThreadListCoordinator.Factory,
    postDialogControllerFactory: PostDialogController.Factory,
    private val threadCreatePostDialogExecutor: ThreadCreatePostDialogExecutor,
    postDialogImageUploaderFactory: PostDialogImageUploader.Factory,
    private val logger: AppLogger,
    @Assisted("viewModelKey") viewModelKey: String
) : BaseViewModel<BoardUiState, BoardInitArgs>() {

    private var bookmarkStatusJob: Job? = null
    val bookmarkSheetHolder = bookmarkSheetStateHolderFactory.create(viewModelScope)

    // UI 状態の StateFlow（View 側で監視される）
    override val _uiState = MutableStateFlow(BoardUiState())

    // スレッド一覧の監視・ソート・フィルタを行うコーディネータ
    private val threadListCoordinator =
        threadListCoordinatorFactory.create(_uiState, viewModelScope)

    // PostDialogの状態/操作を共通化するコントローラ
    private val postDialogController = postDialogControllerFactory.create(
        scope = viewModelScope,
        stateAdapter = BoardPostDialogStateAdapter(
            stateReader = { currentBoardSessionState().postDialogState },
            stateUpdater = { transform ->
                updateCurrentBoardSessionState { current ->
                    current.copy(postDialogState = transform(current.postDialogState))
                }
            },
        ),
        identityHistoryKey = CREATE_IDENTITY_HISTORY_KEY,
        executor = threadCreatePostDialogExecutor,
        boardIdProvider = { uiState.value.boardInfo.boardId },
        onPostSuccess = { refreshBoardData() },
    )

    /**
     * PostDialogの操作をUIへ公開する。
     */
    val postDialogActions: PostDialogController
        get() = postDialogController

    // 画像アップロード処理（非同期）
    private val postDialogImageUploader = postDialogImageUploaderFactory.create(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
    )

    init {
        // 設定（ジェスチャー等）の変更を監視して UI 状態に反映する
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

    /**
     * 板画面の初期化処理を行う。
     */
    fun initializeBoard(boardInfo: BoardInfo) {
        initializeFlow(BoardInitArgs(boardInfo))
    }

    /**
     * 初期化キーを作成する。
     */
    override fun buildInitKey(args: BoardInitArgs): String {
        return args.boardInfo.url
    }

    /**
     * UIState に初期値を反映する。
     */
    override fun applyInitialUiState(args: BoardInitArgs) {
        val boardInfo = args.boardInfo
        val serviceName = parseServiceName(boardInfo.url)
        _uiState.update { state ->
            state.copy(
                boardInfo = boardInfo,
                serviceName = serviceName,
            )
        }
        updateBoardSessionStateByUrl(boardInfo.url) { state ->
            state.copy(postDialogState = state.postDialogState.copy(namePlaceholder = boardInfo.noname))
        }
        syncBoardUiStateFromSession(boardInfo.url)
    }

    /**
     * ボード情報の永続化と補完を行う。
     */
    override fun launchDataComplement(args: BoardInitArgs) {
        val boardInfo = args.boardInfo
        viewModelScope.launch {
            val ensuredId = repository.ensureBoard(boardInfo)
            val ensuredInfo = boardInfo.copy(boardId = ensuredId)
            _uiState.update { it.copy(boardInfo = ensuredInfo) }

            // SETTING.TXT から noname を取得して UI に反映する。
            repository.fetchBoardNoname("${boardInfo.url}SETTING.TXT")?.let { noname ->
                _uiState.update { state ->
                    state.copy(
                        boardInfo = state.boardInfo.copy(noname = noname),
                    )
                }
                updateBoardSessionStateByUrl(boardInfo.url) { state ->
                    state.copy(postDialogState = state.postDialogState.copy(namePlaceholder = noname))
                }
                syncBoardUiStateFromSession(boardInfo.url)
            }

            // スレッド作成時の名前/メール履歴を準備する。
            postDialogController.prepareIdentityHistory(ensuredId)
        }
    }

    /**
     * ブックマークとNG監視を開始する。
     */
    override fun startObservers(args: BoardInitArgs) {
        val boardInfo = args.boardInfo
        bookmarkStatusJob?.cancel()
        bookmarkStatusJob = viewModelScope.launch {
            bookmarkBoardRepository.getBoardWithBookmarkAndGroupByUrlFlow(boardInfo.url)
                .collect { boardWithBookmark ->
                    val group = boardWithBookmark?.bookmarkWithGroup?.group
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

        // NG リストを監視し、スレッドタイトルのフィルタを更新する。
        viewModelScope.launch {
            ngRepository.observeNgs().collect { list ->
                val filters = list.filter { it.type == NgType.THREAD_TITLE }
                    .mapNotNull { ng ->
                        runCatching {
                            val rx = if (ng.isRegex) {
                                Regex(ng.pattern)
                            } else {
                                Regex(Regex.escape(ng.pattern))
                            }
                            ng.boardId to rx
                        }.getOrNull()
                    }
                threadListCoordinator.updateThreadTitleNg(filters)
            }
        }
    }

    // データ読み込み（スレッド一覧を取得）
    override suspend fun loadData(isRefresh: Boolean) {
        var boardInfo = uiState.value.boardInfo
        val boardUrl = boardInfo.url
        if (boardUrl.isBlank()) return
        // boardId が未登録なら登録して UIState に反映
        if (boardInfo.boardId == 0L) {
            val id = repository.ensureBoard(boardInfo)
            boardInfo = boardInfo.copy(boardId = id)
            _uiState.update { it.copy(boardInfo = boardInfo) }
        }

        // ローディング UI を表示しプログレスを初期化
        updateCurrentBoardSessionState { it.copy(isLoading = true, loadProgress = 0f) }
        val refreshStartAt = System.currentTimeMillis()
        val normalizedUrl = boardUrl.trimEnd('/')
        try {
            // subject.txt を使ってスレッド一覧を取得（進捗コールバックで UI 更新）
            val success = repository.refreshThreadList(
                boardInfo.boardId,
                "$normalizedUrl/subject.txt",
                refreshStartAt,
                isRefresh
            ) { progress ->
                updateCurrentBoardSessionState { state -> state.copy(loadProgress = progress) }
            }
            if (!success) {
                updateCurrentBoardSessionState { it.copy(pendingToastResId = R.string.board_load_failed) }
            }
        } catch (e: Exception) {
            // 例外詳細はログへ出し、ユーザーには短い文言を通知する。
            logger.e(message = "Failed to refresh board threads: ${boardInfo.url}", throwable = e)
            updateCurrentBoardSessionState { it.copy(pendingToastResId = R.string.board_load_failed) }
        } finally {
            // 読み込み終了後の UI 更新とスレッドコーディネータへの通知
            updateCurrentBoardSessionState { it.copy(isLoading = false, loadProgress = 1f, resetScroll = true) }
            threadListCoordinator.onRefreshCompleted()
        }
        // 取得結果を監視させる（リアルタイム更新の開始）
        threadListCoordinator.startObservingThreads(boardInfo.boardId, boardUrl)
    }

    // Pull-to-refresh 用のメソッド（外部から強制再初期化）
    fun refreshBoardData() { // Pull-to-refresh 用のメソッド
        initialize(force = true) // 強制的に初期化処理を再実行
    }

    // スクロールリセットフラグの消費（UI 側で呼ぶ）
    fun consumeResetScroll() {
        updateCurrentBoardSessionState { it.copy(resetScroll = false) }
    }

    /**
     * 未表示Toastの消費（UI 側で表示後に呼ぶ）。
     */
    fun consumeToast() {
        updateCurrentBoardSessionState { it.copy(pendingToastResId = null) }
    }

    // --- ブックマークシート関連 ---
    /**
     * ブックマークシートを開く。
     */
    fun openBookmarkSheet() {
        val boardInfo = uiState.value.boardInfo
        if (boardInfo.url.isBlank()) {
            // URLが空の場合はシートを開かない。
            return
        }

        val targets = listOf(
            BoardTarget(
                boardInfo = boardInfo,
                currentGroupId = uiState.value.bookmarkStatusState.selectedGroup?.id
            )
        )
        bookmarkSheetHolder.open(targets)
    }

    // ソート関連の操作
    fun setSortKey(sortKey: ThreadSortKey) {
        updateCurrentBoardSessionState { it.copy(currentSortKey = sortKey) }
        threadListCoordinator.applyFiltersAndSort()
    }

    fun toggleSortOrder() {
        if (uiState.value.currentSortKey == ThreadSortKey.DEFAULT) {
            return
        }
        updateCurrentBoardSessionState { it.copy(isSortAscending = !it.isSortAscending) }
        threadListCoordinator.applyFiltersAndSort()
    }

    fun updateSearchInput(inputValue: TextFieldValue) {
        updateCurrentBoardSessionState { it.copy(searchInputValue = inputValue) }
        threadListCoordinator.applyFiltersAndSort()
    }

    fun setSearchQuery(query: String) = updateSearchInput(TextFieldValue(query))

    fun setSearchMode(isActive: Boolean) {
        updateCurrentBoardSessionState {
            if (isActive) {
                it.copy(isSearchActive = true, isTabSwipeEnabled = false)
            } else {
                it.copy(
                    isSearchActive = false,
                    isTabSwipeEnabled = true,
                    searchInputValue = TextFieldValue(""),
                )
            }
        }
        if (!isActive) {
            threadListCoordinator.applyFiltersAndSort()
        }
    }

    // Sort BottomSheet 関連
    fun openSortBottomSheet() = updateCurrentBoardSessionState { it.copy(showSortSheet = true) }

    fun closeSortBottomSheet() = updateCurrentBoardSessionState { it.copy(showSortSheet = false) }

    /**
     * スレッド情報シートを開く。
     */
    fun openThreadInfoSheet(threadInfo: ThreadInfo) {
        val boardUrl = uiState.value.boardInfo.url
        if (boardUrl.isBlank()) {
            // URLが空の場合はシートを開かない。
            return
        }
        updateCurrentBoardSessionState { state ->
            state.copy(
                showThreadInfoSheet = true,
                // シート側でスレURLを組み立てられるよう、板URLを注入する。
                threadInfoSheetTarget = threadInfo.copy(url = boardUrl),
            )
        }
    }

    /**
     * スレッド情報シートを閉じる。
     */
    fun closeThreadInfoSheet() = updateCurrentBoardSessionState { it.copy(showThreadInfoSheet = false) }

    /**
     * 板情報シートを開く。
     */
    fun openBoardInfoSheet() = updateCurrentBoardSessionState { it.copy(showBoardInfoSheet = true) }

    /**
     * 板情報シートを閉じる。
     */
    fun closeBoardInfoSheet() = updateCurrentBoardSessionState { it.copy(showBoardInfoSheet = false) }

    /**
     * 画像をアップロードし、成功時に本文へURLを挿入する。
     */
    fun uploadImage(context: Context, uri: Uri) {
        postDialogImageUploader.uploadImage(context, uri) { url ->
            postDialogActions.appendImageUrl(url)
        }
    }

    /**
     * route-level キャッシュから外す際に、このタブ用の監視ジョブと補助状態を解放する。
     */
    fun disposeResources() {
        bookmarkSheetHolder.dispose()
        val boardId = _uiState.value.boardInfo.boardId
        if (boardId != 0L) {
            // 最終確認時刻（baseline）を同期的に保存しておく
            runBlocking { repository.updateBaseline(boardId, System.currentTimeMillis()) }
        }
        viewModelScope.cancel()
    }

    // ViewModel が破棄される直前に呼ばれる（アプリ停止や画面遷移時）
    override fun onCleared() {
        disposeResources()
        super.onCleared()
    }

    /**
     * 投稿履歴の識別キーを定義する。
     */
    companion object {
        private const val CREATE_IDENTITY_HISTORY_KEY = "board_create_identity"
    }

    /**
     * 現在表示中の板タブ key を返す。
     */
    private fun currentBoardUrl(): String? {
        return uiState.value.boardInfo.url.takeIf { it.isNotBlank() }
    }

    /**
     * 現在表示中の板タブの SessionState を返す。
     */
    private fun currentBoardSessionState(): BoardSessionState {
        val boardUrl = currentBoardUrl() ?: return BoardSessionState()
        return boardTabsCoordinator.getBoardSessionState(boardUrl)
    }

    /**
     * 現在表示中の板タブの SessionState を更新し、UiState へ同期する。
     */
    private fun updateCurrentBoardSessionState(
        transform: (BoardSessionState) -> BoardSessionState,
    ) {
        val boardUrl = currentBoardUrl()
        if (boardUrl == null) {
            applyBoardSessionToUiState(transform(currentBoardSessionState()))
            return
        }
        updateBoardSessionStateByUrl(boardUrl, transform)
        syncBoardUiStateFromSession(boardUrl)
    }

    /**
     * 指定板タブの SessionState を更新する。
     */
    private fun updateBoardSessionStateByUrl(
        boardUrl: String,
        transform: (BoardSessionState) -> BoardSessionState,
    ) {
        if (boardUrl.isBlank()) {
            return
        }
        boardTabsCoordinator.updateBoardSessionState(boardUrl, transform)
    }

    /**
     * 指定板タブの SessionState を UiState の読み取り用フィールドへ反映する。
     */
    private fun syncBoardUiStateFromSession(boardUrl: String) {
        if (boardUrl.isBlank()) {
            return
        }
        val session = boardTabsCoordinator.getBoardSessionState(boardUrl)
        applyBoardSessionToUiState(session)
    }

    /**
     * SessionState の内容を現在の UiState へ投影する。
     */
    private fun applyBoardSessionToUiState(session: BoardSessionState) {
        _uiState.update { state ->
            state.copy(
                showSortSheet = session.showSortSheet,
                showThreadInfoSheet = session.showThreadInfoSheet,
                threadInfoSheetTarget = session.threadInfoSheetTarget,
                showBoardInfoSheet = session.showBoardInfoSheet,
                currentSortKey = session.currentSortKey,
                isSortAscending = session.isSortAscending,
                isSearchActive = session.isSearchActive,
                searchInputValue = session.searchInputValue,
                postDialogState = session.postDialogState,
                resetScroll = session.resetScroll,
                pendingToastResId = session.pendingToastResId,
                isLoading = session.isLoading,
                loadProgress = session.loadProgress,
                isTabSwipeEnabled = session.isTabSwipeEnabled,
            )
        }
    }
}

/**
 * Board画面の投稿状態をPostDialogStateへ橋渡しするアダプタ。
 *
 * Board タブの SessionState 上にある PostDialogState を読み書きし、
 * 共通コントローラの更新結果を現在タブへ反映する。
 */
private class BoardPostDialogStateAdapter(
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
 * BoardViewModel を生成するためのファクトリ。
 */
@AssistedFactory
interface BoardViewModelFactory {
    fun create(
        @Assisted("viewModelKey") viewModelKey: String
    ): BoardViewModel
}
