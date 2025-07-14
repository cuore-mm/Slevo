package com.websarva.wings.android.bbsviewer.ui.board

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.Groupable
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.ThreadCreateRepository
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.data.repository.PostResult
import com.websarva.wings.android.bbsviewer.ui.common.BaseViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkViewModelFactory
import com.websarva.wings.android.bbsviewer.ui.util.parseServiceName
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class BoardViewModel @AssistedInject constructor(
    private val repository: BoardRepository,
    private val threadCreateRepository: ThreadCreateRepository,
    private val singleBookmarkViewModelFactory: SingleBookmarkViewModelFactory,
    @Assisted("viewModelKey") val viewModelKey: String
) : BaseViewModel<BoardUiState>() {

    // 元のスレッドリストを保持
    private var originalThreads: List<ThreadInfo>? = null

    override val _uiState = MutableStateFlow(BoardUiState())
    private var singleBookmarkViewModel: SingleBookmarkViewModel? = null

    fun initializeBoard(boardInfo: BoardInfo) {
        // Factoryを使ってBookmarkStateViewModelを生成
        singleBookmarkViewModel = singleBookmarkViewModelFactory.create(boardInfo, null)

        val serviceName = parseServiceName(boardInfo.url)
        _uiState.update { it.copy(boardInfo = boardInfo, serviceName = serviceName) }

        // BookmarkStateViewModelのUI状態を監視し、自身のUI状態にマージする
        viewModelScope.launch {
            singleBookmarkViewModel?.uiState?.collect { bkState ->
                _uiState.update { it.copy(singleBookmarkState = bkState) }
            }
        }

        initialize() // BaseViewModelの初期化処理を呼び出す
    }

    override suspend fun loadData(isRefresh: Boolean) {
        val boardUrl = uiState.value.boardInfo.url
        if (boardUrl.isBlank()) return

        _uiState.update { it.copy(isLoading = true) }
        try {
            val normalizedUrl = boardUrl.trimEnd('/')
            val threads =
                repository.getThreadList("$normalizedUrl/subject.txt", forceRefresh = isRefresh)
            if (threads != null) {
                originalThreads = threads
                applyFiltersAndSort()
            }
        } catch (e: Exception) {
            // Handle error
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshBoardData() { // Pull-to-refresh 用のメソッド
        initialize(force = true) // 強制的に初期化処理を再実行
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

    fun setSortKey(sortKey: ThreadSortKey) {
        _uiState.update { it.copy(currentSortKey = sortKey) }
        applyFiltersAndSort()
    }

    fun toggleSortOrder() {
        if (_uiState.value.currentSortKey != ThreadSortKey.DEFAULT) {
            _uiState.update { it.copy(isSortAscending = !it.isSortAscending) }
            applyFiltersAndSort()
        }
    }

    // 検索クエリの更新
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    // 検索モードの切り替え
    fun setSearchMode(isActive: Boolean) {
        _uiState.update { it.copy(isSearchActive = isActive) }
        if (!isActive) {
            // 検索モード終了時にクエリをクリアし、フィルタもリセット
            setSearchQuery("")
        }
    }

    private fun applyFiltersAndSort() {
        originalThreads?.let { allThreads ->
            // 1. フィルタリング
            val filteredList = if (_uiState.value.searchQuery.isNotBlank()) {
                allThreads.filter {
                    it.title.contains(_uiState.value.searchQuery, ignoreCase = true)
                }
            } else {
                allThreads
            }
            // 2. ソート
            val sortedList = applySort(
                filteredList,
                _uiState.value.currentSortKey,
                _uiState.value.isSortAscending
            )
            _uiState.update { it.copy(threads = sortedList) }
        }
    }


    private fun applySort(
        list: List<ThreadInfo>,
        sortKey: ThreadSortKey,
        ascending: Boolean
    ): List<ThreadInfo> {
        if (sortKey == ThreadSortKey.DEFAULT && _uiState.value.searchQuery.isBlank()) {
            // 検索もしていないデフォルトの場合は originalThreads の順序をそのまま使うが、
            // この関数に渡される list は既にフィルタリングされた可能性のあるリスト。
            // ここでは渡された list をソートせずに返すことで「フィルタ後のデフォルト順」とする。
            // 厳密な「サーバーから返ってきた順」は originalThreads を直接使う必要があるが、
            // フィルタリングと組み合わせる場合はこれで良い。
            return list
        }
        // 検索時、またはデフォルト以外のソートキーの場合はソートを行う
        val sortedList = when (sortKey) {
            ThreadSortKey.DEFAULT -> list // フィルタ適用済みの場合、この時点での順序を維持
            ThreadSortKey.MOMENTUM -> list.sortedBy { it.momentum }
            ThreadSortKey.RES_COUNT -> list.sortedBy { it.resCount }
            ThreadSortKey.DATE_CREATED -> list.sortedBy { it.key.toLongOrNull() ?: 0L }
        }
        return if (ascending) sortedList else sortedList.reversed()
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
    fun showCreateDialog() {
        _uiState.update { it.copy(createDialog = true) }
    }

    fun hideCreateDialog() {
        _uiState.update { it.copy(createDialog = false) }
    }

    fun updateCreateName(name: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(name = name)) }
    }

    fun updateCreateMail(mail: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(mail = mail)) }
    }

    fun updateCreateTitle(title: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(title = title)) }
    }

    fun updateCreateMessage(message: String) {
        _uiState.update { it.copy(createFormState = it.createFormState.copy(message = message)) }
    }

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
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, createDialog = false) }
            val result = threadCreateRepository.createThreadFirstPhase(host, board, title, name, mail, message)
            _uiState.update { it.copy(isPosting = false) }
            when (result) {
                is PostResult.Success -> {
                    _uiState.update { it.copy(postResultMessage = "書き込みに成功しました。") }
                    refreshBoardData()
                }
                is PostResult.Confirm -> {
                    _uiState.update {
                        it.copy(
                            postConfirmation = result.confirmationData,
                            isConfirmationScreen = true
                        )
                    }
                }
                is PostResult.Error -> {
                    _uiState.update {
                        it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                    }
                }
            }
        }
    }

    fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, isConfirmationScreen = false) }
            val result = threadCreateRepository.createThreadSecondPhase(host, board, confirmationData)
            _uiState.update { it.copy(isPosting = false) }
            when (result) {
                is PostResult.Success -> {
                    _uiState.update { it.copy(postResultMessage = "書き込みに成功しました。") }
                    refreshBoardData()
                }
                is PostResult.Error -> {
                    _uiState.update {
                        it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                    }
                }
                is PostResult.Confirm -> {
                    _uiState.update {
                        it.copy(
                            postConfirmation = result.confirmationData,
                            isConfirmationScreen = true
                        )
                    }
                }
            }
        }
    }

}


@AssistedFactory
interface BoardViewModelFactory {
    fun create(
        @Assisted("viewModelKey") viewModelKey: String
    ): BoardViewModel
}

