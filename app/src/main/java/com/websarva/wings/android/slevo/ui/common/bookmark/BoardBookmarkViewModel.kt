package com.websarva.wings.android.slevo.ui.common.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 板向けのブックマーク操作と状態を管理する ViewModel。
 *
 * グループ編集は共通ヘルパーに委譲し、板固有の保存/解除のみを担当する。
 */
class BoardBookmarkViewModel @AssistedInject constructor(
    private val bookmarkRepository: BookmarkBoardRepository,
    @Assisted initialBoardInfo: BoardInfo,
) : ViewModel(), BookmarkActions {

    private var boardInfo: BoardInfo = initialBoardInfo

    private val _uiState = MutableStateFlow(SingleBookmarkState())
    override val bookmarkState: StateFlow<SingleBookmarkState> = _uiState.asStateFlow()

    private val groupEditor = BookmarkGroupEditor(
        scope = viewModelScope,
        state = _uiState,
        config = BookmarkGroupEditor.Config(
            isBoard = true,
            observeGroups = { bookmarkRepository.observeGroups() },
            addGroup = { name, color -> bookmarkRepository.addGroupAtEnd(name, color) },
            updateGroup = { id, name, color -> bookmarkRepository.updateGroup(id, name, color) },
            deleteGroup = { id -> bookmarkRepository.deleteGroup(id) },
            loadDeleteItems = { groupId ->
                bookmarkRepository.observeGroupsWithBoards().first()
                    .firstOrNull { it.group.groupId == groupId }
                    ?.boards
                    ?.map { it.name }
                    ?: emptyList()
            },
        ),
    )

    init {
        observeBoardBookmark()
    }

    /**
     * 板のブックマーク状態を監視して UI 状態へ反映する。
     */
    private fun observeBoardBookmark() {
        viewModelScope.launch {
            bookmarkRepository.getBoardWithBookmarkAndGroupByUrlFlow(boardInfo.url)
                .collect { boardWithBookmark ->
                    _uiState.update {
                        it.copy(
                            isBookmarked = boardWithBookmark?.bookmarkWithGroup != null,
                            selectedGroup = boardWithBookmark?.bookmarkWithGroup?.group,
                        )
                    }
                }
        }
    }

    /**
     * 板を指定グループに保存し、ボトムシートを閉じる。
     */
    override fun saveBookmark(groupId: Long) {
        viewModelScope.launch {
            val id = bookmarkRepository.upsertBookmark(boardInfo, groupId)
            boardInfo = boardInfo.copy(boardId = id)
            closeBookmarkSheet()
        }
    }

    /**
     * 板のブックマークを解除し、ボトムシートを閉じる。
     */
    override fun unbookmarkBoard() {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(boardInfo.boardId)
            closeBookmarkSheet()
        }
    }

    /** ブックマークシートを開く。 */
    override fun openBookmarkSheet() {
        _uiState.update { it.copy(showBookmarkSheet = true) }
    }

    /** ブックマークシートを閉じる。 */
    override fun closeBookmarkSheet() {
        _uiState.update { it.copy(showBookmarkSheet = false) }
    }

    override fun openAddGroupDialog() = groupEditor.openAddGroupDialog()
    override fun openEditGroupDialog(group: Groupable) = groupEditor.openEditGroupDialog(group)
    override fun closeAddGroupDialog() = groupEditor.closeAddGroupDialog()
    override fun setEnteredGroupName(name: String) = groupEditor.setEnteredGroupName(name)
    override fun setSelectedColor(color: String) = groupEditor.setSelectedColor(color)
    override fun confirmGroup() = groupEditor.confirmGroup()
    override fun requestDeleteGroup() = groupEditor.requestDeleteGroup()
    override fun confirmDeleteGroup() = groupEditor.confirmDeleteGroup()
    override fun closeDeleteGroupDialog() = groupEditor.closeDeleteGroupDialog()
}

/**
 * 板向けブックマーク ViewModel を生成する Assisted Factory。
 */
@AssistedFactory
interface BoardBookmarkViewModelFactory {
    fun create(boardInfo: BoardInfo): BoardBookmarkViewModel
}
