package com.websarva.wings.android.bbsviewer.ui.board

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import androidx.core.net.toUri
import com.websarva.wings.android.bbsviewer.ui.common.BaseListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class BoardViewModel @AssistedInject constructor(
    private val repository: BoardRepository,
    private val bookmarkRepo: BookmarkBoardRepository,
    @Assisted("viewModelKey") val viewModelKey: String
) : BaseListViewModel<BoardUiState>() {

    private var isInitialized = false

    // 元のスレッドリストを保持
    private var originalThreads: List<ThreadInfo>? = null

    override val _uiState = MutableStateFlow(BoardUiState())

    private var isInitialBoardLoad = true // このViewModelインスタンスでの初回読み込みフラグ

    fun initializeBoard(boardInfo: BoardInfo) {
        if (isInitialized) return
        isInitialized = true

        val serviceName = parseServiceName(boardInfo.url)
        _uiState.update {
            it.copy(
                boardInfo = boardInfo,
                serviceName = serviceName
            )
        }

        loadThreadList(force = true)
        loadBookmarkDetails()
    }

    fun loadThreadList(force: Boolean = false) { // pull-to-refreshからは force=false で呼ばれる想定
        val boardUrl = uiState.value.boardInfo.url
        if (boardUrl.isBlank()) return

        val shouldForceRefresh = force || isInitialBoardLoad
        Log.i(
            "BoardViewModel",
            "Loading thread list for board: $boardUrl, forceRefresh: $shouldForceRefresh"
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val threads = repository.getThreadList("$boardUrl/subject.txt", shouldForceRefresh)
                if (threads != null) {
                    originalThreads = threads
                    applyFiltersAndSort()
                }
                if (isInitialBoardLoad) {
                    isInitialBoardLoad = false // 初回ロード完了
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

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

    private fun loadBookmarkDetails() {
        val boardUrl = uiState.value.boardInfo.url
        if (boardUrl.isBlank()) return

        viewModelScope.launch {
            bookmarkRepo.getBoardWithBookmarkAndGroupByUrlFlow(boardUrl)
                .collectLatest { boardWithBookmarkAndGroup ->
                    boardWithBookmarkAndGroup?.let {
                        _uiState.update { state ->
                            state.copy(
                                boardInfo = BoardInfo(
                                    boardId = it.board.boardId,
                                    name = it.board.name,
                                    url = it.board.url
                                ),
                                isBookmarked = it.bookmarkWithGroup != null,
                                selectedGroup = it.bookmarkWithGroup?.group
                            )
                        }
                    }
                }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            bookmarkRepo.observeGroups()         // Flow<List<BoardGroupEntity>>
                .collect { groups ->
                    _uiState.update { it.copy(groups = groups) }
                }
        }
    }

    /** ブックマークシートを開く */
    fun openBookmarkSheet() = viewModelScope.launch {
        _uiState.update { it.copy(showBookmarkSheet = true) }
    }

    /** ブックマークシートを閉じる */
    fun closeBookmarkSheet() = viewModelScope.launch {
        _uiState.update { it.copy(showBookmarkSheet = false) }
    }

    fun addGroup() = viewModelScope.launch {
        val name = uiState.value.enteredGroupName.takeIf { it.isNotBlank() } ?: return@launch
        val color = uiState.value.selectedColor ?: return@launch
        bookmarkRepo.addGroupAtEnd(name, color)
        closeAddGroupDialog()
    }

    /**
     * お気に入りを登録または更新
     */
    fun saveBookmark(groupId: Long) {
        viewModelScope.launch {
            bookmarkRepo.upsertBookmark(
                BookmarkBoardEntity(
                    boardId = uiState.value.boardInfo.boardId,
                    groupId = groupId,
                )
            )
        }
    }

    /**
     * 現在の板のお気に入りを解除するメソッド
     */
    fun unbookmarkBoard() {
        viewModelScope.launch {
            val currentBoardId = uiState.value.boardInfo.boardId
            bookmarkRepo.deleteBookmark(currentBoardId)
            // UI状態も更新 (ブックマーク解除、選択グループ解除)
            _uiState.update { it.copy(isBookmarked = false, selectedGroup = null) }
        }
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshBoardData() { // Pull-to-refresh 用のメソッド
        loadThreadList(force = false) // 通常の差分取得
    }

    private fun parseServiceName(url: String): String {
        return try {
            val host = url.toUri().host ?: return ""
            val parts = host.split(".")
            if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
        } catch (e: Exception) {
            ""
        }
    }
}


@AssistedFactory
interface BoardViewModelFactory {
    fun create(
        @Assisted("viewModelKey") viewModelKey: String
    ): BoardViewModel
}

