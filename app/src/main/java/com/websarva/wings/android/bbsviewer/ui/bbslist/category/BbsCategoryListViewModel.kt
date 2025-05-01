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

    // サービスIDと表示名を SavedStateHandle から取得
    private val serviceId: Long = savedStateHandle.get<Long>("serviceId")
        ?: error("serviceId is required")
    private val serviceName: String = savedStateHandle.get<String>("serviceName")
        ?: ""

    private val _uiState = MutableStateFlow(
        BbsCategoryListUiState(
            serviceId = serviceId,
            serviceName = serviceName
        )
    )
    val uiState: StateFlow<BbsCategoryListUiState> = _uiState.asStateFlow()

    init {
        loadCategoryInfo()
    }

    /**
     * カテゴリ一覧と各カテゴリの板数を取得し UIState に反映
     */
    private fun loadCategoryInfo() {
        viewModelScope.launch {
            repository.getCategoriesWithCount(serviceId)
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
                        CategoryInfo(
                            categoryId = cwc.category.categoryId,
                            name = cwc.category.name,
                            boardCount = cwc.boardCount
                        )
                    }
                    _uiState.update {
                        it.copy(categories = infos, isLoading = false, errorMessage = null)
                    }
                }
        }
    }
}

/**
 * UI ステート: サービス情報＋カテゴリ一覧
 */
data class BbsCategoryListUiState(
    val serviceId: Long,
    val serviceName: String = "",
    val categories: List<CategoryInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * UI 用カテゴリ情報
 */
data class CategoryInfo(
    val categoryId: Long,
    val name: String,
    val boardCount: Int
)
