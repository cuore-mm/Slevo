package com.websarva.wings.android.slevo.ui.board.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogStateAdapter
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadCreatePostDialogExecutor
import com.websarva.wings.android.slevo.ui.thread.state.PostFormState
import com.websarva.wings.android.slevo.ui.bbsroute.BaseViewModel
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.common.bookmark.BoardTarget
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
    threadListCoordinatorFactory: ThreadListCoordinator.Factory,
    postDialogControllerFactory: PostDialogController.Factory,
    private val threadCreatePostDialogExecutor: ThreadCreatePostDialogExecutor,
    boardImageUploaderFactory: BoardImageUploader.Factory,
    @Assisted("viewModelKey") viewModelKey: String
) : BaseViewModel<BoardUiState>(), PostDialogController.IdentityHistoryDelegate {

    // 初期化済みのボードURL（重複初期化を防ぐ）
    private var initializedUrl: String? = null

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
        stateAdapter = BoardPostDialogStateAdapter(_uiState),
        identityHistoryDelegate = this,
        identityHistoryKey = CREATE_IDENTITY_HISTORY_KEY,
        executor = threadCreatePostDialogExecutor,
        boardIdProvider = { uiState.value.boardInfo.boardId },
        onPostSuccess = { refreshBoardData() },
    )

    // 画像アップロード処理（非同期）
    private val boardImageUploader = boardImageUploaderFactory.create(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
        updateState = ::updateUiState
    )

    // UI 状態更新ヘルパー
    private fun updateUiState(transform: (BoardUiState) -> BoardUiState) {
        _uiState.update(transform)
    }

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
        // 同じ URL なら再初期化しない
        if (initializedUrl == boardInfo.url) return
        initializedUrl = boardInfo.url

        // サービス名を URL から解析して UI に保持
        val serviceName = parseServiceName(boardInfo.url)
        _uiState.update { it.copy(boardInfo = boardInfo, serviceName = serviceName) }

        // ボード情報を DB に登録（未登録なら登録）し、noname ファイルを取得する
        viewModelScope.launch {
            val ensuredId = repository.ensureBoard(boardInfo)
            val ensuredInfo = boardInfo.copy(boardId = ensuredId)
            _uiState.update { it.copy(boardInfo = ensuredInfo) }

            // SETTING.TXT から noname を取得して UI に反映
            repository.fetchBoardNoname("${boardInfo.url}SETTING.TXT")?.let { noname ->
                _uiState.update { state ->
                    state.copy(boardInfo = state.boardInfo.copy(noname = noname))
                }
            }

            // スレッド作成時の名前/メール履歴を準備
            postDialogController.prepareIdentityHistory(ensuredId)
        }

        // ブックマーク状態を監視してツールバー表示に反映
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

        // NG リストを監視し、スレッドタイトルのフィルタを更新する
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

        // BaseViewModel の初期化（データロード等）を開始
        initialize() // BaseViewModelの初期化処理を呼び出す
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
        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        val refreshStartAt = System.currentTimeMillis()
        val normalizedUrl = boardUrl.trimEnd('/')
        try {
            // subject.txt を使ってスレッド一覧を取得（進捗コールバックで UI 更新）
            repository.refreshThreadList(
                boardInfo.boardId,
                "$normalizedUrl/subject.txt",
                refreshStartAt,
                isRefresh
            ) { progress ->
                _uiState.update { state -> state.copy(loadProgress = progress) }
            }
        } catch (_: Exception) {
            // 取得失敗は UI で黙殺（既存処理維持）
        } finally {
            // 読み込み終了後の UI 更新とスレッドコーディネータへの通知
            _uiState.update { it.copy(isLoading = false, loadProgress = 1f, resetScroll = true) }
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
        _uiState.update { it.copy(resetScroll = false) }
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
    fun setSortKey(sortKey: ThreadSortKey) = threadListCoordinator.setSortKey(sortKey)

    fun toggleSortOrder() = threadListCoordinator.toggleSortOrder()

    fun setSearchQuery(query: String) = threadListCoordinator.setSearchQuery(query)

    fun setSearchMode(isActive: Boolean) = threadListCoordinator.setSearchMode(isActive)

    // Sort BottomSheet 関連
    fun openSortBottomSheet() = _uiState.update { it.copy(showSortSheet = true) }

    fun closeSortBottomSheet() = _uiState.update { it.copy(showSortSheet = false) }

    // Info ダイアログ表示/非表示
    fun openInfoDialog() = _uiState.update { it.copy(showInfoDialog = true) }

    fun closeInfoDialog() = _uiState.update { it.copy(showInfoDialog = false) }

    // --- スレッド作成関連 ---
    fun showCreateDialog() = postDialogController.showDialog()

    fun hideCreateDialog() = postDialogController.hideDialog()

    fun updateCreateName(name: String) = postDialogController.updateName(name)

    fun updateCreateMail(mail: String) = postDialogController.updateMail(mail)

    fun updateCreateTitle(title: String) = postDialogController.updateTitle(title)

    fun updateCreateMessage(message: String) = postDialogController.updateMessage(message)

    fun selectCreateNameHistory(name: String) =
        postDialogController.selectNameHistory(name)

    fun selectCreateMailHistory(mail: String) =
        postDialogController.selectMailHistory(mail)

    fun deleteCreateNameHistory(name: String) =
        postDialogController.deleteNameHistory(name)

    fun deleteCreateMailHistory(mail: String) =
        postDialogController.deleteMailHistory(mail)

    // 確認画面を閉じる
    fun hideConfirmationScreen() {
        postDialogController.hideConfirmationScreen()
    }

    // エラーページ（WebView）を閉じる
    fun hideErrorWebView() {
        postDialogController.hideErrorWebView()
    }

    // スレッド作成フェーズ（第一段階：確認画面へ遷移）
    fun createThreadFirstPhase(
        host: String,
        board: String,
    ) = postDialogController.postFirstPhase(host, board, threadKey = null)

    // スレッド作成フェーズ（第二段階：投稿処理）
    fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ) = postDialogController.postSecondPhase(host, board, threadKey = null, confirmationData = confirmationData)

    // 画像アップロード（選択された URI を渡して非同期アップロード）
    fun uploadImage(context: Context, uri: Uri) {
        boardImageUploader.uploadImage(context, uri)
    }

    // --- IdentityHistoryDelegate 実装（履歴関連のイベント） ---
    override fun onPrepareIdentityHistory(
        key: String,
        boardId: Long,
        repository: PostHistoryRepository,
        onLastIdentity: ((String, String) -> Unit)?,
        onNameSuggestions: (List<String>) -> Unit,
        onMailSuggestions: (List<String>) -> Unit,
        nameQueryProvider: () -> String,
        mailQueryProvider: () -> String,
    ) {
        // 親クラスの履歴準備処理を呼び出す（具体的ロジックは Base に委譲）
        super.prepareIdentityHistory(
            key,
            boardId,
            repository,
            onLastIdentity,
            onNameSuggestions,
            onMailSuggestions,
            nameQueryProvider,
            mailQueryProvider,
        )
    }

    override fun onRefreshIdentityHistorySuggestions(
        key: String,
        type: PostIdentityType?,
    ) {
        // 履歴候補の更新を親に委譲
        super.refreshIdentityHistorySuggestions(key, type)
    }

    override fun onDeleteIdentityHistory(
        key: String,
        repository: PostHistoryRepository,
        type: PostIdentityType,
        value: String,
    ) {
        // 履歴削除を親に委譲
        super.deleteIdentityHistory(key, repository, type, value)
    }

    // ViewModel が破棄される直前に呼ばれる（アプリ停止や画面遷移時）
    override fun onCleared() {
        bookmarkSheetHolder.dispose()
        val boardId = _uiState.value.boardInfo.boardId
        if (boardId != 0L) {
            // 最終確認時刻（baseline）を同期的に保存しておく
            runBlocking { repository.updateBaseline(boardId, System.currentTimeMillis()) }
        }
        super.onCleared()
    }

    /**
     * 投稿履歴の識別キーを定義する。
     */
    companion object {
        private const val CREATE_IDENTITY_HISTORY_KEY = "board_create_identity"
    }
}

/**
 * Board画面の投稿状態をPostDialogStateへ変換するアダプタ。
 *
 * CreateThreadFormStateをPostFormStateへ写像し、共通コントローラの更新を反映する。
 */
private class BoardPostDialogStateAdapter(
    private val stateFlow: MutableStateFlow<BoardUiState>,
) : PostDialogStateAdapter {

    /**
     * 現在のBoardUiStateをPostDialogStateへ変換して返す。
     */
    override fun readState(): PostDialogState {
        val state = stateFlow.value
        return PostDialogState(
            isDialogVisible = state.createDialog,
            formState = PostFormState(
                name = state.createFormState.name,
                mail = state.createFormState.mail,
                title = state.createFormState.title,
                message = state.createFormState.message,
            ),
            nameHistory = state.createNameHistory,
            mailHistory = state.createMailHistory,
            isPosting = state.isPosting,
            postConfirmation = state.postConfirmation,
            isConfirmationScreen = state.isConfirmationScreen,
            showErrorWebView = state.showErrorWebView,
            errorHtmlContent = state.errorHtmlContent,
            postResultMessage = state.postResultMessage,
        )
    }

    /**
     * PostDialogStateの更新結果をBoardUiStateへ反映する。
     */
    override fun updateState(transform: (PostDialogState) -> PostDialogState) {
        stateFlow.update { current ->
            // --- Mapping ---
            // CreateThreadFormState を PostDialogState に変換し、更新結果を再マッピングする。
            val updated = transform(
                PostDialogState(
                    isDialogVisible = current.createDialog,
                    formState = PostFormState(
                        name = current.createFormState.name,
                        mail = current.createFormState.mail,
                        title = current.createFormState.title,
                        message = current.createFormState.message,
                    ),
                    nameHistory = current.createNameHistory,
                    mailHistory = current.createMailHistory,
                    isPosting = current.isPosting,
                    postConfirmation = current.postConfirmation,
                    isConfirmationScreen = current.isConfirmationScreen,
                    showErrorWebView = current.showErrorWebView,
                    errorHtmlContent = current.errorHtmlContent,
                    postResultMessage = current.postResultMessage,
                )
            )
            current.copy(
                createDialog = updated.isDialogVisible,
                createFormState = CreateThreadFormState(
                    name = updated.formState.name,
                    mail = updated.formState.mail,
                    title = updated.formState.title,
                    message = updated.formState.message,
                ),
                createNameHistory = updated.nameHistory,
                createMailHistory = updated.mailHistory,
                isPosting = updated.isPosting,
                postConfirmation = updated.postConfirmation,
                isConfirmationScreen = updated.isConfirmationScreen,
                showErrorWebView = updated.showErrorWebView,
                errorHtmlContent = updated.errorHtmlContent,
                postResultMessage = updated.postResultMessage,
            )
        }
    }
}


@AssistedFactory
interface BoardViewModelFactory {
    fun create(
        @Assisted("viewModelKey") viewModelKey: String
    ): BoardViewModel
}
