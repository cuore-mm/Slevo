package com.websarva.wings.android.slevo.ui.board

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.ui.bbsroute.BaseViewModel
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkViewModelFactory
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BoardViewModel @AssistedInject constructor(
    private val repository: BoardRepository,
    private val singleBookmarkViewModelFactory: SingleBookmarkViewModelFactory,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    private val threadListCoordinatorFactory: ThreadListCoordinator.Factory,
    private val threadCreationControllerFactory: ThreadCreationController.Factory,
    private val boardImageUploaderFactory: BoardImageUploader.Factory,
    @Assisted("viewModelKey") val viewModelKey: String
) : BaseViewModel<BoardUiState>(), ThreadCreationController.IdentityHistoryDelegate {

    private var initializedUrl: String? = null

    override val _uiState = MutableStateFlow(BoardUiState())
    private val threadListCoordinator = threadListCoordinatorFactory.create(_uiState, viewModelScope)
    private val threadCreationController = threadCreationControllerFactory.create(
        scope = viewModelScope,
        stateProvider = { uiState.value },
        updateState = ::updateUiState,
        identityHistoryDelegate = this,
        refreshBoard = ::refreshBoardData
    )
    private val boardImageUploader = boardImageUploaderFactory.create(
        scope = viewModelScope,
        dispatcher = Dispatchers.IO,
        updateState = ::updateUiState
    )

    private fun updateUiState(transform: (BoardUiState) -> BoardUiState) {
        _uiState.update(transform)
    }

    init {
        viewModelScope.launch {
            settingsRepository.observeGestureSettings().collect { settings ->
                _uiState.update { it.copy(gestureSettings = settings) }
            }
        }
    }

    fun initializeBoard(boardInfo: BoardInfo) {
        if (initializedUrl == boardInfo.url) return
        initializedUrl = boardInfo.url
        val bookmarkVm = singleBookmarkViewModelFactory.create(boardInfo, null)
        bookmarkViewModel = bookmarkVm

        val serviceName = parseServiceName(boardInfo.url)
        _uiState.update { it.copy(boardInfo = boardInfo, serviceName = serviceName) }

        viewModelScope.launch {
            val ensuredId = repository.ensureBoard(boardInfo)
            val ensuredInfo = boardInfo.copy(boardId = ensuredId)
            _uiState.update { it.copy(boardInfo = ensuredInfo) }

            repository.fetchBoardNoname("${boardInfo.url}SETTING.TXT")?.let { noname ->
                _uiState.update { state ->
                    state.copy(boardInfo = state.boardInfo.copy(noname = noname))
                }
            }

            threadCreationController.prepareCreateIdentityHistory(ensuredId)
        }

        viewModelScope.launch {
            bookmarkVm.uiState.collect { bkState ->
                _uiState.update { it.copy(singleBookmarkState = bkState) }
            }
        }

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

        initialize() // BaseViewModelの初期化処理を呼び出す
    }

    override suspend fun loadData(isRefresh: Boolean) {
        var boardInfo = uiState.value.boardInfo
        val boardUrl = boardInfo.url
        if (boardUrl.isBlank()) return
        if (boardInfo.boardId == 0L) {
            val id = repository.ensureBoard(boardInfo)
            boardInfo = boardInfo.copy(boardId = id)
            _uiState.update { it.copy(boardInfo = boardInfo) }
        }

        _uiState.update { it.copy(isLoading = true, loadProgress = 0f) }
        val refreshStartAt = System.currentTimeMillis()
        val normalizedUrl = boardUrl.trimEnd('/')
        try {
            repository.refreshThreadList(
                boardInfo.boardId,
                "$normalizedUrl/subject.txt",
                refreshStartAt,
                isRefresh
            ) { progress ->
                _uiState.update { state -> state.copy(loadProgress = progress) }
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            _uiState.update { it.copy(isLoading = false, loadProgress = 1f, resetScroll = true) }
            threadListCoordinator.onRefreshCompleted()
        }
        threadListCoordinator.startObservingThreads(boardInfo.boardId, boardUrl)
    }

    fun refreshBoardData() { // Pull-to-refresh 用のメソッド
        initialize(force = true) // 強制的に初期化処理を再実行
    }

    fun consumeResetScroll() {
        _uiState.update { it.copy(resetScroll = false) }
    }

    // --- お気に入り関連の処理はBookmarkStateViewModelに委譲 ---
    fun saveBookmark(groupId: Long) = bookmarkSaveBookmark(groupId)
    fun unbookmarkBoard() = bookmarkUnbookmark()
    fun openAddGroupDialog() = bookmarkOpenAddGroupDialog()
    fun openEditGroupDialog(group: Groupable) = bookmarkOpenEditGroupDialog(group)
    fun closeAddGroupDialog() = bookmarkCloseAddGroupDialog()
    fun setEnteredGroupName(name: String) = bookmarkSetEnteredGroupName(name)
    fun setSelectedColor(color: String) = bookmarkSetSelectedColor(color)
    fun confirmGroup() = bookmarkConfirmGroup()
    fun requestDeleteGroup() = bookmarkRequestDeleteGroup()
    fun confirmDeleteGroup() = bookmarkConfirmDeleteGroup()
    fun closeDeleteGroupDialog() = bookmarkCloseDeleteGroupDialog()
    fun openBookmarkSheet() = bookmarkOpenBookmarkSheet()
    fun closeBookmarkSheet() = bookmarkCloseBookmarkSheet()

    fun setSortKey(sortKey: ThreadSortKey) {
        threadListCoordinator.setSortKey(sortKey)
    }

    fun toggleSortOrder() {
        threadListCoordinator.toggleSortOrder()
    }

    fun setSearchQuery(query: String) {
        threadListCoordinator.setSearchQuery(query)
    }

    fun setSearchMode(isActive: Boolean) {
        threadListCoordinator.setSearchMode(isActive)
    }

    // Sort BottomSheet 関連
    fun openSortBottomSheet() {
        _uiState.update { it.copy(showSortSheet = true) }
    }

    fun closeSortBottomSheet() {
        _uiState.update { it.copy(showSortSheet = false) }
    }

    fun openInfoDialog() {
        _uiState.update { it.copy(showInfoDialog = true) }
    }

    fun closeInfoDialog() {
        _uiState.update { it.copy(showInfoDialog = false) }
    }

    // --- スレッド作成関連 ---
    fun showCreateDialog() = threadCreationController.showCreateDialog()

    fun hideCreateDialog() = threadCreationController.hideCreateDialog()

    fun updateCreateName(name: String) = threadCreationController.updateCreateName(name)

    fun updateCreateMail(mail: String) = threadCreationController.updateCreateMail(mail)

    fun updateCreateTitle(title: String) = threadCreationController.updateCreateTitle(title)

    fun updateCreateMessage(message: String) = threadCreationController.updateCreateMessage(message)

    fun selectCreateNameHistory(name: String) = threadCreationController.selectCreateNameHistory(name)

    fun selectCreateMailHistory(mail: String) = threadCreationController.selectCreateMailHistory(mail)

    fun deleteCreateNameHistory(name: String) = threadCreationController.deleteCreateNameHistory(name)

    fun deleteCreateMailHistory(mail: String) = threadCreationController.deleteCreateMailHistory(mail)

    fun hideConfirmationScreen() {
        _uiState.update { it.copy(isConfirmationScreen = false) }
    }

    fun hideErrorWebView() {
        _uiState.update { it.copy(showErrorWebView = false, errorHtmlContent = "") }
    }

    fun createThreadFirstPhase(
        host: String,
        board: String,
        title: String,
        name: String,
        mail: String,
        message: String,
    ) = threadCreationController.createThreadFirstPhase(host, board, title, name, mail, message)

    fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ) = threadCreationController.createThreadSecondPhase(host, board, confirmationData)

    fun uploadImage(context: Context, uri: Uri) {
        boardImageUploader.uploadImage(context, uri)
    }

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
        super.refreshIdentityHistorySuggestions(key, type)
    }

    override fun onDeleteIdentityHistory(
        key: String,
        repository: PostHistoryRepository,
        type: PostIdentityType,
        value: String,
    ) {
        super.deleteIdentityHistory(key, repository, type, value)
    }

    override fun onCleared() {
        val boardId = _uiState.value.boardInfo.boardId
        if (boardId != 0L) {
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

