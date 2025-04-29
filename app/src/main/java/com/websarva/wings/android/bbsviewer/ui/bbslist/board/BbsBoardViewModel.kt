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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// UIState：板一覧
data class BbsBoardUiState(
    val serviceName: String = "",
    val categoryName: String = "",
    val boards: List<BoardInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class BbsBoardViewModel @Inject constructor(
    private val repository: BbsServiceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serviceId = savedStateHandle.get<String>("serviceId")!!
    private val categoryName = savedStateHandle.get<String>("categoryName")!!

    private val _uiState = MutableStateFlow(
        BbsBoardUiState(
            serviceName  = savedStateHandle.get<String>("serviceName") ?: "",
            categoryName = categoryName
        )
    )
    val uiState: StateFlow<BbsBoardUiState> = _uiState.asStateFlow()

    init {
        // ViewModel の初期化時に一度だけ板一覧をロード
        loadBoardInfo(serviceId, categoryName)
    }

    /**
     * serviceId＋categoryName から板一覧を取得し、
     * uiState.boards に格納する
     */
    fun loadBoardInfo(
        serviceId: String,
        categoryName: String
    ) {
        viewModelScope.launch {
            repository.getBoardsForCategory(serviceId, categoryName)
                // BoardEntity → BoardInfo にマッピング
                .map { list ->
                    list.map { boardEntity ->
                        BoardInfo(
                            name = boardEntity.name,
                            url = boardEntity.url
                        )
                    }
                }
                .onStart {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.localizedMessage ?: "不明なエラー"
                        )
                    }
                }
                .collect { infos ->
                    _uiState.update {
                        it.copy(
                            boards = infos,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }
}
