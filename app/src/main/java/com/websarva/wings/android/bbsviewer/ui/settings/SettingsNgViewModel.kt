package com.websarva.wings.android.bbsviewer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.bbsviewer.data.model.NgType
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.NgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsNgViewModel @Inject constructor(
    private val repository: NgRepository,
    bookmarkBoardRepository: BookmarkBoardRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsNgUiState())
    val uiState: StateFlow<SettingsNgUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeNgs().collect { ngs ->
                _uiState.update { it.copy(ngs = ngs) }
            }
        }
        viewModelScope.launch {
            bookmarkBoardRepository.observeAllBoards().collect { boards ->
                _uiState.update { state ->
                    state.copy(boardNames = boards.associate { it.boardId to it.name })
                }
            }
        }
    }

    fun selectTab(type: NgType) {
        _uiState.update { it.copy(selectedTab = type) }
    }

    fun startEdit(ng: NgEntity) {
        _uiState.update { it.copy(editingNg = ng) }
    }

    fun endEdit() {
        _uiState.update { it.copy(editingNg = null) }
    }

    fun toggleSelectMode(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectMode = enabled,
                selected = if (enabled) state.selected else emptySet()
            )
        }
    }

    fun toggleSelect(id: Long) {
        _uiState.update { state ->
            val next = state.selected.toMutableSet().apply { if (!add(id)) remove(id) }
            state.copy(selected = next)
        }
    }

    fun removeSelected() {
        viewModelScope.launch {
            val ids = _uiState.value.selected.toList()
            repository.remove(ids)
            _uiState.update { it.copy(selectMode = false, selected = emptySet()) }
        }
    }
}

data class SettingsNgUiState(
    val ngs: List<NgEntity> = emptyList(),
    val selectedTab: NgType = NgType.USER_ID,
    val editingNg: NgEntity? = null,
    val boardNames: Map<Long, String> = emptyMap(),
    val selectMode: Boolean = false,
    val selected: Set<Long> = emptySet(),
)

