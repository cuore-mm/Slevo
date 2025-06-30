package com.websarva.wings.android.bbsviewer.ui.bbslist.service

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.repository.BbsServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the BBS service screen.
 * - サービス一覧の取得・表示
 * - サービス（menuUrl）追加・更新
 * - サービス削除
 */
@HiltViewModel
class ServiceListViewModel @Inject constructor(
    private val repository: BbsServiceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BbsServiceViewModel"
    }

    private val _uiState = MutableStateFlow(ServiceListUiState())
    val uiState: StateFlow<ServiceListUiState> = _uiState.asStateFlow()

    // 元のサービスリストを保持
    private var originalServices: List<ServiceInfo>? = null
    private var searchJob: Job? = null

    init {
        loadServiceInfo()
    }

    /**
     * サービス一覧と板数を取得し、UI用にマッピング
     */
    fun loadServiceInfo() {
        viewModelScope.launch {
            repository.getAllServicesWithCount()
                .map { list ->
                    list.map { swc ->
                        ServiceInfo(
                            serviceId = swc.service.serviceId,
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
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "不明なエラー") }
                }
                .collect { infos ->
                    originalServices = infos
                    applyFilter()
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                }
        }
    }

    /**
     * メニューURLを受け取り、サービス追加または更新
     */
    fun addOrUpdateService(menuUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.addOrUpdateService(menuUrl)
            } catch (e: Exception) {
                Log.e(TAG, "サービス追加/更新に失敗: $menuUrl", e)
                _uiState.update { it.copy(errorMessage = "サービス追加に失敗しました") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 選択中サービスをまとめて削除
     */
    fun removeSelectedServices() {
        viewModelScope.launch(Dispatchers.IO) {
            val toRemove = _uiState.value.selected.toList()
            toRemove.forEach { id -> repository.removeService(id) }
            _uiState.update { it.copy(selectMode = false, selected = emptySet()) }
        }
    }

    /**
     * ダイアログ表示／非表示トグル
     */
    fun toggleAddDialog(show: Boolean) {
        _uiState.update { it.copy(showAddDialog = show) }
    }

    fun toggleDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteDialog = show) }
    }

    /**
     * 入力URL更新
     */
    fun updateEnteredUrl(url: String) {
        _uiState.update { it.copy(enteredUrl = url) }
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

    /**
     * 選択モード切替
     */
    fun toggleSelectMode(enabled: Boolean) {
        _uiState.update { state -> state.copy(selectMode = enabled, selected = if (enabled) state.selected else emptySet()) }
    }

    /**
     * サービスID単位で選択／解除
     */
    fun toggleSelect(serviceId: Long) {
        _uiState.update { state ->
            val next = state.selected.toMutableSet().apply { if (!add(serviceId)) remove(serviceId) }
            state.copy(selected = next)
        }
    }

    /** 現在の検索クエリでフィルタリング */
    private fun applyFilter() {
        val base = originalServices ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (_uiState.value.searchQuery.isBlank()) {
                _uiState.update { it.copy(services = base) }
            } else {
                val boards = repository.searchBoards(_uiState.value.searchQuery).first()
                val ids = boards.map { it.serviceId }.toSet()
                val filtered = base.filter { it.serviceId in ids }
                _uiState.update { it.copy(services = filtered) }
            }
        }
    }
}

/** UIステート */
data class ServiceListUiState(
    val services: List<ServiceInfo> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectMode: Boolean = false,
    val selected: Set<Long> = emptySet(),
    val showAddDialog: Boolean = false,
    val enteredUrl: String = "",
    val showDeleteDialog: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = ""
)

/** UI用サービス情報 */
data class ServiceInfo(
    val serviceId: Long,
    val domain: String,
    val name: String,
    val menuUrl: String? = null,
    val boardCount: Int
)
