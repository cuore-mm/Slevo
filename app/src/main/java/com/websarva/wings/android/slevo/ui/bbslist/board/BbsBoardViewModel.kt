package com.websarva.wings.android.slevo.ui.bbslist.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
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

    init {
        loadBoardInfo()
    }

    /**
     * サービスIDとカテゴリIDから板一覧を取得し、UIに反映。
     * カテゴリIDが0の場合はサービスに属する全板を取得する。
     */
    private fun loadBoardInfo() {
        viewModelScope.launch {
            val flow = if (categoryId == 0L) {
                repository.getBoards(serviceId)
            } else {
                repository.getBoardsForCategory(serviceId, categoryId)
            }
            flow
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
                    _uiState.update { it.copy(boards = infos, isLoading = false, errorMessage = null) }
                }
        }
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
    val errorMessage: String? = null
)
