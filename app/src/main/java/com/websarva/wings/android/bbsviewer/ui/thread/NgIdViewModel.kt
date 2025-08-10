package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NgIdViewModel @Inject constructor(
    repository: BookmarkBoardRepository
) : ViewModel() {
    val boards: StateFlow<List<BoardInfo>> = repository.observeAllBoards()
        .map { list -> list.map { BoardInfo(it.boardId, it.name, it.url) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
