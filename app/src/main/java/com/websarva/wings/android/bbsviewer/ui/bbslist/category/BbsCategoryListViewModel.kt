package com.websarva.wings.android.bbsviewer.ui.bbslist.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * カテゴリ一覧画面の ViewModel
 */
@HiltViewModel
class BbsCategoryViewModel @Inject constructor(
    private val repository: BbsServiceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BbsCategoryListUiState(
            serviceName = savedStateHandle.get<String>("serviceName")!!
        )
    )
    val uiState: StateFlow<BbsCategoryListUiState> = _uiState.asStateFlow()

    init {
        // SavedStateHandle から serviceId（domain）を取得してロード
        savedStateHandle.get<String>("serviceId")?.let { domain ->
            loadCategoryInfo(domain)
        }
    }

    /**
     * 引数の domain でカテゴリ情報をロードし、uiState を更新
     */
    fun loadCategoryInfo(domain: String) {
        viewModelScope.launch {
            repository.getCategoryCounts(domain)
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
                .collect { list ->
                    val infos = list.map { cwc ->
                        CategoryInfo(name = cwc.name, boardCount = cwc.boardCount)
                    }
                    _uiState.update {
                        it.copy(categories = infos, isLoading = false, errorMessage = null)
                    }
                }
        }
    }
}

data class BbsCategoryListUiState(
    val serviceName: String = "",
    val categories: List<CategoryInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class CategoryInfo(
    val name: String,
    val boardCount: Int
)
