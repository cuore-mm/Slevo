package com.websarva.wings.android.bbsviewer.ui.bbslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.BBSMenuRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BBSListViewModel @Inject constructor(
    private val repository: BBSMenuRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BBSListUiState())
    val uiState: StateFlow<BBSListUiState> = _uiState.asStateFlow()

    fun loadBBSMenu() {
        viewModelScope.launch {
            val result = repository.fetchBBSMenu()
            if (result != null) {
                _uiState.update { currentState ->
                    currentState.copy(categories = result)
                }
            }
        }
    }

    fun updateBoards(category: Category){
        _uiState.value = _uiState.value.copy(boards = category.boards)
    }
}

