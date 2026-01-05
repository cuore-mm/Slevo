package com.websarva.wings.android.slevo.ui.board.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.ui.bbsroute.BaseViewModel
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.common.bookmark.BoardTarget
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
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
    threadCreationControllerFactory: ThreadCreationController.Factory,
    boardImageUploaderFactory: BoardImageUploader.Factory,
    @Assisted("viewModelKey") viewModelKey: String
) : BaseViewModel<BoardUiState>(), ThreadCreationController.IdentityHistoryDelegate {

    // 初期化済みのボードURL（重複初期化を防ぐ）
    private var initializedUrl: String? = null

    private var bookmarkStatusJob: Job? = null
    private var bookmarkSheetJob: Job? = null
    private var bookmarkSheetHolder: BookmarkBottomSheetStateHolder? = null

    // UI 状態の StateFlow（View 側で監視される）
    override val _uiState = MutableStateFlow(BoardUiState())

    // スレッド一覧の監視・ソート・フィルタを行うコーディネータ
    private val threadListCoordinator =
        threadListCoordinatorFactory.create(_uiState, viewModelScope)

    // スレッド作成に関する操作をまとめるコントローラ
    private val threadCreationController = threadCreationControllerFactory.create(
        scope = viewModelScope,
        stateProvider = { uiState.value },
        updateState = ::updateUiState,
        identityHistoryDelegate = this,
        refreshBoard = ::refreshBoardData
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
            threadCreationController.prepareCreateIdentityHistory(ensuredId)
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
     * ブックマークシートを開き、ステートホルダーを生成する。
     */
    fun openBookmarkSheet() {
        val boardInfo = uiState.value.boardInfo
        if (boardInfo.url.isBlank()) {
            // URLが空の場合はシートを開かない。
            return
        }

        // --- Holder setup ---
        bookmarkSheetHolder?.dispose()
        bookmarkSheetJob?.cancel()

        val targets = listOf(
            BoardTarget(
                boardInfo = boardInfo,
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
    fun showCreateDialog() = threadCreationController.showCreateDialog()

    fun hideCreateDialog() = threadCreationController.hideCreateDialog()

    fun updateCreateName(name: String) = threadCreationController.updateCreateName(name)

    fun updateCreateMail(mail: String) = threadCreationController.updateCreateMail(mail)

    fun updateCreateTitle(title: String) = threadCreationController.updateCreateTitle(title)

    fun updateCreateMessage(message: String) = threadCreationController.updateCreateMessage(message)

    fun selectCreateNameHistory(name: String) =
        threadCreationController.selectCreateNameHistory(name)

    fun selectCreateMailHistory(mail: String) =
        threadCreationController.selectCreateMailHistory(mail)

    fun deleteCreateNameHistory(name: String) =
        threadCreationController.deleteCreateNameHistory(name)

    fun deleteCreateMailHistory(mail: String) =
        threadCreationController.deleteCreateMailHistory(mail)

    // 確認画面を閉じる
    fun hideConfirmationScreen() {
        _uiState.update { it.copy(isConfirmationScreen = false) }
    }

    // エラーページ（WebView）を閉じる
    fun hideErrorWebView() {
        _uiState.update { it.copy(showErrorWebView = false, errorHtmlContent = "") }
    }

    // スレッド作成フェーズ（第一段階：確認画面へ遷移）
    fun createThreadFirstPhase(
        host: String,
        board: String,
        title: String,
        name: String,
        mail: String,
        message: String,
    ) = threadCreationController.createThreadFirstPhase(host, board, title, name, mail, message)

    // スレッド作成フェーズ（第二段階：投稿処理）
    fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ) = threadCreationController.createThreadSecondPhase(host, board, confirmationData)

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
        val boardId = _uiState.value.boardInfo.boardId
        if (boardId != 0L) {
            // 最終確認時刻（baseline）を同期的に保存しておく
            runBlocking { repository.updateBaseline(boardId, System.currentTimeMillis()) }
        }
        super.onCleared()
    }

}


@AssistedFactory
interface BoardViewModelFactory {
    fun create(
        @Assisted("viewModelKey") viewModelKey: String
    ): BoardViewModel
}
