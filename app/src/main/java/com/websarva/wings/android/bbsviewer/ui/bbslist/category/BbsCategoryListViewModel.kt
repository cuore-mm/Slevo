package com.websarva.wings.android.bbsviewer.ui.bbslist.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class BbsCategoryListUiState(
    val categories: List<CategoryInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class CategoryInfo(
    val name: String,
    val boardCount: Int
)

@HiltViewModel
class BbsCategoryViewModel @Inject constructor(
    private val repository: BbsServiceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BbsCategoryListUiState())
    val uiState: StateFlow<BbsCategoryListUiState> = _uiState.asStateFlow()

    fun loadCategoryInfo(serviceId: String) {
        viewModelScope.launch {
            repository.getCategoriesForService(serviceId)
                .map { list ->
                    list.map { cwb ->
                        CategoryInfo(
                            name = cwb.category.name,
                            boardCount = cwb.boards.size
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
                            categories = infos,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }
}
