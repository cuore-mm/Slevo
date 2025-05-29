package com.websarva.wings.android.bbsviewer.ui.bbslist.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.repository.BbsServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * カテゴリ一覧画面の ViewModel
 */
@HiltViewModel
class BoardCategoryListViewModel @Inject constructor(
    private val repository: BbsServiceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // サービスIDと表示名を SavedStateHandle から取得
    private val serviceId: Long = savedStateHandle.get<Long>("serviceId")
        ?: error("serviceId is required")
    private val serviceName: String = savedStateHandle.get<String>("serviceName")
        ?: ""

    private val _uiState = MutableStateFlow(
        BoardCategoryListUiState(
            serviceId = serviceId,
            serviceName = serviceName
        )
    )
    val uiState: StateFlow<BoardCategoryListUiState> = _uiState.asStateFlow()

    init {
        loadCategoryInfo()
    }

    /**
     * カテゴリ一覧と各カテゴリの板数を取得し UIState に反映
     */
    private fun loadCategoryInfo() {
        viewModelScope.launch {
            repository.getCategoriesWithCount(serviceId)      // Room → Flow
                .flowOn(Dispatchers.IO)                       // ← DB 取得を IO で
                .map { list ->
                    list.map { cwc ->                         // ← リスト変換も別スレッド
                        CategoryInfo(
                            categoryId = cwc.category.categoryId,
                            name = cwc.category.name,
                            boardCount = cwc.boardCount
                        )
                    }
                }
                .flowOn(Dispatchers.Default)                  // ← 計算を CPU バックグラウンドへ
                .distinctUntilChanged()                       // 同内容なら無視
                .onStart {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                }
                .catch { e ->
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.localizedMessage ?: "不明なエラー"
                            )
                        }
                    }
                }
                .collectLatest { infos ->                     // ここまで BG スレッド
                    withContext(Dispatchers.Main) {           // ← UI 更新だけ Main
                        _uiState.update {
                            it.copy(categories = infos, isLoading = false, errorMessage = null)
                        }
                    }
                }
        }
    }
}

/**
 * UI ステート: サービス情報＋カテゴリ一覧
 */
data class BoardCategoryListUiState(
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
