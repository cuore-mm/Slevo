package com.websarva.wings.android.bbsviewer.ui.board

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: BoardRepository,
    private val bookmarkRepo: BookmarkBoardRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val boardUrl = savedStateHandle.get<String>("boardUrl")
        ?: error("boardUrl is required")
    private val boardName = savedStateHandle.get<String>("boardName")
        ?: error("boardName is required")

    private val _uiState = MutableStateFlow(
        BoardUiState(
            boardInfo = BoardInfo(
                boardId = savedStateHandle.get<Long>("boardId") ?: 0,
                name = boardName,
                url = boardUrl
            )
        )
    )
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init {
        // 初期化時に一度だけ subject.txt をロード
        loadThreadList()

        // お気に入り状態を監視し、UI state に反映
        viewModelScope.launch {
            bookmarkRepo
                .getBookmarkWithGroupByUrl(boardUrl)
                .collect { bg ->
                    _uiState.update {
                        it.copy(
                            isBookmarked = (bg?.bookmark != null),
                            groupName = bg?.group?.name,
                            groupColorHex = bg?.group?.colorHex
                        )
                    }
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadThreadList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val threads = repository.getThreadList("$boardUrl/subject.txt")
                _uiState.update { it.copy(threads = threads) }
            } catch (e: Exception) {
                // 必要に応じて errorMessage を入れる
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

//    /** 単純なお気に入り登録／解除 */
//    fun toggleBookmark() {
//        viewModelScope.launch {
//            bookmarkRepo.toggleByUrl(boardUrl)
//        }
//    }
//
//    /**
//     * お気に入り登録時に同時にグループ名＋色を登録
//     * @param groupName  グループ名称
//     * @param colorHex   カラーコード (#RRGGBB)
//     */
//    fun addBookmarkWithGroup(groupName: String, colorHex: String) {
//        viewModelScope.launch {
//            bookmarkRepo.addWithGroupByUrl(
//                boardUrl,
//                GroupEntity(name = groupName, colorHex = colorHex)
//            )
//        }
//    }
}

data class BoardUiState(
    val threads: List<ThreadInfo>? = null,
    val isLoading: Boolean = false,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val isBookmarked: Boolean = false,
    val groupName: String? = null,
    val groupColorHex: String? = null
)
