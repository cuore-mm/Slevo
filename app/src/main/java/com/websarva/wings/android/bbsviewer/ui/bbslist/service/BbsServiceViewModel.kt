package com.websarva.wings.android.bbsviewer.ui.bbslist.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.repository.BbsServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BBSサービス画面の UI 状態を表すデータクラス
 */
data class BbsServiceUiState(
    val services: List<ServiceInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editMode: Boolean = false,
    val addBBSDialog: Boolean = false,
    val enteredUrl: String = "",
)

data class ServiceInfo(
    val serviceId: String,
    val name: String,
    val menuUrl: String? = null,
    val boardCount: Int? = null,
    val boardUrl: String? = null
)

@HiltViewModel
class BbsServiceViewModel @Inject constructor(
    private val repository: BbsServiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BbsServiceUiState())
    val uiState: StateFlow<BbsServiceUiState> = _uiState.asStateFlow()

    init {
        loadServiceInfo()
    }

    /** サービス一覧＋板数をまとめて取得 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadServiceInfo() {
        viewModelScope.launch {
            repository.getAllServices()
                // List<BbsServiceWithCategories> → Flow<List<ServiceInfo>>
                .flatMapLatest { svcList ->
                    // サービスごとに Flow<ServiceInfo> を作る
                    val infoFlows: List<Flow<ServiceInfo>> = svcList.map { swc ->
                        val svc = swc.service
                        if (svc.menuUrl != null) {
                            // menuUrl があればカテゴリ＋板を取得して合計数を計算
                            repository.getCategoriesForService(svc.serviceId)
                                .map { catsWithBoards ->
                                    val totalBoards = catsWithBoards.sumOf { it.boards.size }
                                    ServiceInfo(
                                        serviceId  = svc.serviceId,
                                        name       = svc.displayName,
                                        menuUrl    = svc.menuUrl,
                                        boardCount = totalBoards,
                                        boardUrl   = svc.boardUrl
                                    )
                                }
                        } else {
                            // menuUrl が null の場合は板数も null
                            flowOf(
                                ServiceInfo(
                                    serviceId  = svc.serviceId,
                                    name       = svc.displayName,
                                    menuUrl    = null,
                                    boardCount = null,
                                    boardUrl   = svc.boardUrl
                                )
                            )
                        }
                    }
                    // infoFlows の List<Flow<ServiceInfo>> を一つの Flow<List<ServiceInfo>> にまとめる
                    if (infoFlows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(infoFlows) { infos -> infos.toList() }
                    }
                }
                .onStart {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            errorMessage = e.localizedMessage ?: "不明なエラー"
                        )
                    }
                }
                .collect { serviceInfos ->
                    _uiState.update {
                        it.copy(
                            services     = serviceInfos,
                            isLoading    = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    /**
     * 新規サービス登録
     */
    fun addService(
        serviceId: String,
        displayName: String,
        menuUrl: String? = null,
        boardUrl: String? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                repository.addService(
                    BbsServiceEntity(
                        serviceId = serviceId,
                        displayName = displayName,
                        menuUrl = menuUrl,
                        boardUrl = boardUrl
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * サービス削除
     */
    fun removeService(service: BbsServiceEntity) {
        viewModelScope.launch {
            repository.removeService(service)
        }
    }

    /**
     * カテゴリ・板キャッシュ更新
     */
    fun refreshCategories(serviceId: String) {
        viewModelScope.launch {
            repository.refreshCategories(serviceId)
        }
    }

    /**
     * 単一板サービスへの板追加
     */
    fun addBoardToService(board: BoardEntity) {
        viewModelScope.launch {
            repository.addBoardToService(board)
        }
    }

    /**
     * 掲示板一覧の編集モードを切り替える
     */
    fun toggleEditMode(boolean: Boolean) {
        _uiState.update { current ->
            current.copy(editMode = boolean)
        }
    }

    fun updateEnteredUrl(url: String) {
        _uiState.update { it.copy(enteredUrl = url) }
    }

    fun toggleAddBBSDialog(boolean: Boolean) {
        _uiState.update { it.copy(addBBSDialog = boolean) }
    }
}
