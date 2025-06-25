package com.websarva.wings.android.bbsviewer.ui.board

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BoardRepository
import com.websarva.wings.android.bbsviewer.ui.common.BaseViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.BookmarkStateViewModel
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.BookmarkStateViewModelFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class BoardViewModel @AssistedInject constructor(
    private val repository: BoardRepository,
    private val bookmarkStateViewModelFactory: BookmarkStateViewModelFactory,
    @Assisted("viewModelKey") val viewModelKey: String
) : BaseViewModel<BoardUiState>() {

    private var isInitialized = false

    // 元のスレッドリストを保持
    private var originalThreads: List<ThreadInfo>? = null

    override val _uiState = MutableStateFlow(BoardUiState())
    private var bookmarkStateViewModel: BookmarkStateViewModel? = null

    private var isInitialBoardLoad = true // このViewModelインスタンスでの初回読み込みフラグ

    fun initializeBoard(boardInfo: BoardInfo) {
        if (isInitialized) return
        isInitialized = true

        // Factoryを使ってBookmarkStateViewModelを生成
        bookmarkStateViewModel = bookmarkStateViewModelFactory.create(boardInfo, null)

        val serviceName = parseServiceName(boardInfo.url)
        _uiState.update { it.copy(boardInfo = boardInfo, serviceName = serviceName) }

        // BookmarkStateViewModelのUI状態を監視し、自身のUI状態にマージする
        viewModelScope.launch {
            bookmarkStateViewModel?.uiState?.collect { favState ->
                _uiState.update { it.copy(singleBookmarkState = favState) }
            }
        }

        loadThreadList(force = true)
    }

    // --- お気に入り関連の処理はBookmarkStateViewModelに委譲 ---
    fun saveBookmark(groupId: Long) = bookmarkStateViewModel?.saveBookmark(groupId)
    fun unbookmarkBoard() = bookmarkStateViewModel?.unbookmark()
    fun openAddGroupDialog() = bookmarkStateViewModel?.openAddGroupDialog()
    fun closeAddGroupDialog() = bookmarkStateViewModel?.closeAddGroupDialog()
    fun setEnteredGroupName(name: String) = bookmarkStateViewModel?.setEnteredGroupName(name)
    fun setSelectedColor(color: String) = bookmarkStateViewModel?.setSelectedColor(color)
    fun addGroup() = bookmarkStateViewModel?.addGroup()
    fun openBookmarkSheet() = bookmarkStateViewModel?.openBookmarkSheet()
    fun closeBookmarkSheet() = bookmarkStateViewModel?.closeBookmarkSheet()

    fun loadThreadList(force: Boolean = false) { // pull-to-refreshからは force=false で呼ばれる想定
        val boardUrl = uiState.value.boardInfo.url
        if (boardUrl.isBlank()) return

        val shouldForceRefresh = force || isInitialBoardLoad
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

