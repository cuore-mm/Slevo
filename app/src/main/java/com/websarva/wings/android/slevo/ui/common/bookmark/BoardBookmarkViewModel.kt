package com.websarva.wings.android.slevo.ui.common.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
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
 * グループ編集は共通コントローラに委譲し、板固有の保存/解除のみを担当する。
 */
class BoardBookmarkViewModel @AssistedInject constructor(
    private val bookmarkRepository: BookmarkBoardRepository,
    @Assisted initialBoardInfo: BoardInfo,
) : ViewModel(), BookmarkActions {

    private var boardInfo: BoardInfo = initialBoardInfo

    private val _uiState = MutableStateFlow(SingleBookmarkState())
    override val bookmarkState: StateFlow<SingleBookmarkState> = _uiState.asStateFlow()

    private val groupDialogController = GroupDialogController(
        scope = viewModelScope,
        state = _uiState,
        getDialogState = { it.groupDialogState },
        setDialogState = { state, dialog -> state.copy(groupDialogState = dialog) },
        config = GroupDialogController.Config(
            addGroup = { _, name, color -> bookmarkRepository.addGroupAtEnd(name, color) },
            updateGroup = { _, id, name, color -> bookmarkRepository.updateGroup(id, name, color) },
            deleteGroup = { _, id -> bookmarkRepository.deleteGroup(id) },
            loadDeleteDialogData = { _, groupId ->
                val group = bookmarkRepository.observeGroupsWithBoards().first()
                    .firstOrNull { it.group.groupId == groupId }
                    ?: return@loadDeleteDialogData null
                GroupDialogController.DeleteDialogData(
                    groupName = group.group.name,
                    items = group.boards.map { it.name },
                )
            },
        ),
    )

    init {
        observeGroups()
        observeBoardBookmark()
    }

    /**
     * グループ一覧を監視して UI 状態へ反映する。
     */
    private fun observeGroups() {
        viewModelScope.launch {
            bookmarkRepository.observeGroups().collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
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

    override fun openAddGroupDialog() = groupDialogController.openAddGroupDialog(true)
    override fun openEditGroupDialog(group: com.websarva.wings.android.slevo.data.model.Groupable) =
        groupDialogController.openEditGroupDialog(group, true)
    override fun closeAddGroupDialog() = groupDialogController.closeAddGroupDialog()
    override fun setEnteredGroupName(name: String) = groupDialogController.setEnteredGroupName(name)
    override fun setSelectedColor(color: String) = groupDialogController.setSelectedColor(color)
    override fun confirmGroup() = groupDialogController.confirmGroup()
    override fun requestDeleteGroup() = groupDialogController.requestDeleteGroup()
    override fun confirmDeleteGroup() = groupDialogController.confirmDeleteGroup()
    override fun closeDeleteGroupDialog() = groupDialogController.closeDeleteGroupDialog()
}

/**
 * 板向けブックマーク ViewModel を生成する Assisted Factory。
 */
@AssistedFactory
interface BoardBookmarkViewModelFactory {
    fun create(boardInfo: BoardInfo): BoardBookmarkViewModel
}
