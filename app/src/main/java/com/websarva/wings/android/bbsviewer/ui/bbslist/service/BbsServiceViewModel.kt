package com.websarva.wings.android.bbsviewer.ui.bbslist.service

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryWithCount
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.repository.BbsServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the BBS service screen.
 * - サービス一覧の取得・表示
 * - サービス（menuUrl）追加
 * - サービス削除
 * - カテゴリ／ボード情報のリフレッシュ
 */
@HiltViewModel
class BbsServiceViewModel @Inject constructor(
    private val repository: BbsServiceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BbsServiceViewModel"
    }

    private val _uiState = MutableStateFlow(BbsServiceUiState())
    val uiState: StateFlow<BbsServiceUiState> = _uiState.asStateFlow()

    init {
        loadServiceInfo()
    }

    /**
     * ServiceWithBoardCount を ServiceInfo にマッピングして UI に流す
     */
    fun loadServiceInfo() {
        viewModelScope.launch {
            repository.getAllServicesWithCount()
                .map { list ->
                    list.map { swc ->
                        ServiceInfo(
                            domain = swc.service.domain,
                            name = swc.service.displayName ?: swc.service.domain,
                            menuUrl = swc.service.menuUrl,
                            boardCount = swc.boardCount
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
                            services = infos,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    /**
     * menuUrl を受け取り、リモートからカテゴリ・ボードを取得して一括保存
     * @param menuUrl JSON/HTML のエンドポイント
     */
    fun addService(menuUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.addService(menuUrl)
            } catch (e: Exception) {
                Log.e(TAG, "サービス追加に失敗しました: $menuUrl", e)
                _uiState.update { it.copy(errorMessage = "サービス追加に失敗しました") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

//    /**
//     * 単一ボードURL を用いたサービス追加・更新
//     * @param boardUrl 単一板の URL
//     */
//    fun addServiceByBoardUrl(boardUrl: String) {
//        viewModelScope.launch {
//            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
//            try {
//                // domain は URL 解析で算出
//                val uri = boardUrl.toUri()
//                val host = uri.host ?: throw IllegalArgumentException("Invalid URL: $boardUrl")
//                val parts = host.split('.')
//                val domain = if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
//
//                // BoardUrl のみ持つエンティティを作成
//                val service = BbsServiceEntity(
//                    domain      = domain,
//                    displayName = uri.pathSegments.firstOrNull(), // またはドメイン
//                    menuUrl     = null
//                )
//                repository.addServiceByBoardUrl(service)
//            } catch (e: Exception) {
//                Log.e(TAG, "単一ボードサービス追加に失敗しました: $boardUrl", e)
//                _uiState.update { it.copy(errorMessage = "ボード追加に失敗しました") }
//            } finally {
//                _uiState.update { it.copy(isLoading = false) }
//            }
//        }
//    }

    /**
     * selected に含まれるドメインのサービスをまとめて削除する
     */
    fun removeService() {
        viewModelScope.launch(Dispatchers.IO) {
            val toRemove = _uiState.value.selected.toList()
            viewModelScope.launch {
                repository.removeService(toRemove)
                _uiState.update { it.copy(selectMode = false, selected = emptySet()) }
            }

            // 終わったら選択状態をクリアしてモードも解除
            _uiState.update { it.copy(selectMode = false, selected = emptySet()) }

        }
    }

    /**
     * 指定ドメインのカテゴリ件数を取得
     */
    fun getCategoryCounts(domain: String): Flow<List<CategoryWithCount>> =
        repository.getCategoryCounts(domain)

    /**
     * キャッシュをリフレッシュ（リモート再取得→ローカル一括置換）
     */
    fun refreshCategories(domain: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.refreshCategories(domain)
            }
        }
    }

//    /**
//     * カテゴリをまたいだ単一ボードの追加
//     */
//    fun addBoardToService(board: BoardEntity) {
//        viewModelScope.launch {
//            repository.addBoardToService(board)
//        }
//    }

    /** ダイアログ表示／非表示 */
    fun toggleAddBBSDialog(show: Boolean) {
        _uiState.update { it.copy(showAddBBSDialog = show) }
    }

    /** 削除ダイアログ表示／非表示 */
    fun toggleDeleteBBSDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteBBSDialog = show) }
    }

    /** 入力 URL 更新 */
    fun updateEnteredUrl(url: String) {
        _uiState.update { it.copy(enteredUrl = url) }
    }

    /** 選択モードの ON/OFF 切り替え */
    fun toggleSelectMode(enabled: Boolean) {
        _uiState.update { it.copy(selectMode = enabled, selected = if (enabled) it.selected else emptySet()) }
    }

    /** ドメイン単位で選択／解除 */
    fun toggleSelect(domain: String) {
        _uiState.update { state ->
            val next = state.selected.toMutableSet().apply {
                if (!add(domain)) remove(domain)
            }
            state.copy(selected = next)
        }
    }
}

/** BBSサービス画面用 UI ステート */
data class BbsServiceUiState(
    val services: List<ServiceInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    val showAddBBSDialog: Boolean = false,
    val enteredUrl: String = "",
    val showDeleteBBSDialog: Boolean = false
)

/** UI 表示用に整形したサービス情報 */
data class ServiceInfo(
    val domain: String,
    val name: String,
    val menuUrl: String? = null,
    val boardCount: Int
)
