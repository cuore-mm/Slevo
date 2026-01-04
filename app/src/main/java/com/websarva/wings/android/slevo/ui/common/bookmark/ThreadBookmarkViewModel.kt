package com.websarva.wings.android.slevo.ui.common.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
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
 * グループ編集は共通ヘルパーに委譲し、スレッド固有の保存/解除のみを担当する。
 */
class ThreadBookmarkViewModel @AssistedInject constructor(
    private val bookmarkRepository: ThreadBookmarkRepository,
    @Assisted private val boardInfo: BoardInfo,
    @Assisted private val threadInfo: ThreadInfo,
) : ViewModel(), BookmarkActions {

    private val _uiState = MutableStateFlow(SingleBookmarkState())
    override val bookmarkState: StateFlow<SingleBookmarkState> = _uiState.asStateFlow()

    private val groupEditor = BookmarkGroupEditor(
        scope = viewModelScope,
        state = _uiState,
        config = BookmarkGroupEditor.Config(
            isBoard = false,
            observeGroups = { bookmarkRepository.observeAllGroups() },
            addGroup = { name, color -> bookmarkRepository.addGroupAtEnd(name, color) },
            updateGroup = { id, name, color -> bookmarkRepository.updateGroup(id, name, color) },
            deleteGroup = { id -> bookmarkRepository.deleteGroup(id) },
            loadDeleteItems = { groupId ->
                bookmarkRepository.observeSortedGroupsWithThreadBookmarks().first()
                    .firstOrNull { it.group.groupId == groupId }
                    ?.threads
                    ?.map { it.title }
                    ?: emptyList()
            },
        ),
    )

    init {
        observeThreadBookmark()
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
 * スレッド向けブックマーク ViewModel を生成する Assisted Factory。
 */
@AssistedFactory
interface ThreadBookmarkViewModelFactory {
    fun create(boardInfo: BoardInfo, threadInfo: ThreadInfo): ThreadBookmarkViewModel
}
