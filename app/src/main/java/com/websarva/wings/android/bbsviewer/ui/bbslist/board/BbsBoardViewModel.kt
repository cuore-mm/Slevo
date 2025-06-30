package com.websarva.wings.android.bbsviewer.ui.bbslist.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.BbsServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the board list screen.
 * - 指定サービス/カテゴリの板一覧取得
 */
@HiltViewModel
class BbsBoardViewModel @Inject constructor(
    private val repository: BbsServiceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // サービスID, サービス名, カテゴリID, カテゴリ名を取得
    private val serviceId: Long = savedStateHandle.get<Long>("serviceId")
        ?: error("serviceId is required")
    private val serviceName: String = savedStateHandle.get<String>("serviceName")
        ?: ""
    private val categoryId: Long = savedStateHandle.get<Long>("categoryId")
        ?: error("categoryId is required")
    private val categoryName: String = savedStateHandle.get<String>("categoryName")
        ?: ""

    private val _uiState = MutableStateFlow(
        BbsBoardUiState(
            serviceName = serviceName,
            categoryId = categoryId,
            categoryName = categoryName
        )
    )
    val uiState: StateFlow<BbsBoardUiState> = _uiState.asStateFlow()

    // 元の板リストを保持し、検索時に利用
    private var originalBoards: List<BoardInfo>? = null

    init {
        loadBoardInfo()
    }

    /**
     * サービスID＋カテゴリID から板一覧を取得し、UIに反映
     */
    private fun loadBoardInfo() {
        viewModelScope.launch {
            repository.getBoardsForCategory(serviceId, categoryId)
                .onStart {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                }
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "不明なエラー") }
                }
                .collect { list ->
                    val infos = list.map { entity ->
                        BoardInfo(
                            boardId = entity.boardId,
                            name = entity.name,
                            url = entity.url
                        )
                    }
                    originalBoards = infos
                    applyFilter()
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                }
        }
    }

    /** 検索クエリ変更 */
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    /** 検索モード切替 */
    fun setSearchMode(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (!active) {
            setSearchQuery("")
        }
    }

    /** 現在の検索クエリでフィルタリング */
    private fun applyFilter() {
        val base = originalBoards ?: return
        val filtered = if (_uiState.value.searchQuery.isNotBlank()) {
            base.filter { it.name.contains(_uiState.value.searchQuery, ignoreCase = true) }
        } else {
            base
        }
        _uiState.update { it.copy(boards = filtered) }
    }
}

/**
 * UI State for board list screen
 */
data class BbsBoardUiState(
    val serviceName: String,
    val categoryId: Long,
    val categoryName: String,
    val boards: List<BoardInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = ""
)
