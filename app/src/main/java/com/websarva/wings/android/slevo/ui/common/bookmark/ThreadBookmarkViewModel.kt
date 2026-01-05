package com.websarva.wings.android.slevo.ui.common.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
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
 * スレッド向けのブックマーク操作と状態を管理する ViewModel。
 *
 * グループ編集は共通コントローラに委譲し、スレッド固有の保存/解除のみを担当する。
 */
class ThreadBookmarkViewModel @AssistedInject constructor(
    private val bookmarkRepository: ThreadBookmarkRepository,
    @Assisted private val boardInfo: BoardInfo,
    @Assisted private val threadInfo: ThreadInfo,
) : ViewModel(), BookmarkActions {

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
            loadDeleteDialogData = loadDeleteDialogData@{ _, groupId ->
                val group = bookmarkRepository.observeSortedGroupsWithThreadBookmarks().first()
                    .firstOrNull { it.group.groupId == groupId }
                    ?: return@loadDeleteDialogData null
                GroupDialogController.DeleteDialogData(
                    groupName = group.group.name,
                    items = group.threads.map { it.title },
                )
            },
        ),
    )

    init {
        observeGroups()
        observeThreadBookmark()
    }

    /**
     * グループ一覧を監視して UI 状態へ反映する。
     */
    private fun observeGroups() {
        viewModelScope.launch {
            bookmarkRepository.observeAllGroups().collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
    }

    /**
     * スレッドのブックマーク状態を監視して UI 状態へ反映する。
     */
    private fun observeThreadBookmark() {
        viewModelScope.launch {
            bookmarkRepository.getBookmarkWithGroup(threadInfo.key, threadInfo.url)
                .collect { threadWithBookmark ->
                    _uiState.update {
                        it.copy(
                            isBookmarked = threadWithBookmark != null,
                            selectedGroup = threadWithBookmark?.group,
                        )
                    }
                }
        }
    }

    /**
     * スレッドを指定グループに保存し、ボトムシートを閉じる。
     */
    override fun saveBookmark(groupId: Long) {
        viewModelScope.launch {
            bookmarkRepository.insertBookmark(
                BookmarkThreadEntity(
                    threadKey = threadInfo.key,
                    boardUrl = boardInfo.url,
                    boardId = boardInfo.boardId,
                    groupId = groupId,
                    title = threadInfo.title,
                    boardName = boardInfo.name,
                    resCount = threadInfo.resCount,
                ),
            )
            closeBookmarkSheet()
        }
    }

    /**
     * スレッドのブックマークを解除し、ボトムシートを閉じる。
     */
    override fun unbookmarkBoard() {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(threadInfo.key, threadInfo.url)
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

    override fun openAddGroupDialog() = groupDialogController.openAddGroupDialog(false)
    override fun openEditGroupDialog(group: com.websarva.wings.android.slevo.data.model.Groupable) =
        groupDialogController.openEditGroupDialog(group, false)
    override fun closeAddGroupDialog() = groupDialogController.closeAddGroupDialog()
    override fun setEnteredGroupName(name: String) = groupDialogController.setEnteredGroupName(name)
    override fun setSelectedColor(color: String) = groupDialogController.setSelectedColor(color)
    override fun confirmGroup() = groupDialogController.confirmGroup()
    override fun requestDeleteGroup() = groupDialogController.requestDeleteGroup()
    override fun confirmDeleteGroup() = groupDialogController.confirmDeleteGroup()
    override fun closeDeleteGroupDialog() = groupDialogController.closeDeleteGroupDialog()
}

/**
 * スレッド向けブックマーク ViewModel を生成する Assisted Factory。
 */
@AssistedFactory
interface ThreadBookmarkViewModelFactory {
    fun create(boardInfo: BoardInfo, threadInfo: ThreadInfo): ThreadBookmarkViewModel
}
