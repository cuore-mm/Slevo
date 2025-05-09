package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkRepository
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val repository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarkUiState())
    val uiState: StateFlow<BookmarkUiState> = _uiState.asStateFlow()

    // 初期化時にお気に入りリストを監視
    init {
        viewModelScope.launch {
            repository.getAllBookmarks().collect { bookmarks ->
                _uiState.update { currentState ->
                    currentState.copy(bookmarks = bookmarks)
                }
            }
        }
    }

    fun addBookmark(bookmark: BookmarkThreadEntity) {
        viewModelScope.launch {
            repository.insertBookmark(bookmark)
        }
    }

    fun removeBookmark(bookmark: BookmarkThreadEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

}

data class BookmarkUiState(
    val bookmarks: List<BookmarkThreadEntity>? = null,
    val isLoading: Boolean = false
)
