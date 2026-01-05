package com.websarva.wings.android.slevo.ui.bookmarklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.GroupWithBoards
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.GroupWithThreadBookmarks
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkBoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.common.bookmark.GroupDialogController
import com.websarva.wings.android.slevo.ui.common.bookmark.GroupDialogState
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ブックマーク一覧画面の状態と選択操作を管理する ViewModel。
 *
 * グループ編集とダイアログ制御は共通コントローラへ委譲する。
 */
@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val boardRepo: BookmarkBoardRepository,
    private val threadBookmarkRepo: ThreadBookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarkUiState())
    val uiState: StateFlow<BookmarkUiState> = _uiState.asStateFlow()

    private val groupDialogController = GroupDialogController(
        scope = viewModelScope,
        state = _uiState,
        getDialogState = { it.groupDialogState },
        setDialogState = { state, dialog -> state.copy(groupDialogState = dialog) },
        config = GroupDialogController.Config(
            addGroup = { isBoard, name, color ->
                if (isBoard) {
                    boardRepo.addGroupAtEnd(name, color)
                } else {
                    threadBookmarkRepo.addGroupAtEnd(name, color)
                }
            },
            updateGroup = { isBoard, id, name, color ->
                if (isBoard) {
                    boardRepo.updateGroup(id, name, color)
                } else {
                    threadBookmarkRepo.updateGroup(id, name, color)
                }
            },
            deleteGroup = { isBoard, id ->
                if (isBoard) {
                    boardRepo.deleteGroup(id)
                } else {
                    threadBookmarkRepo.deleteGroup(id)
                }
            },
            loadDeleteDialogData = loadDeleteDialogData@{ isBoard, groupId ->
                if (isBoard) {
                    val group = boardRepo.observeGroupsWithBoards().first()
                        .firstOrNull { it.group.groupId == groupId }
                    ?: return@loadDeleteDialogData null
                    GroupDialogController.DeleteDialogData(
                        groupName = group.group.name,
                        items = group.boards.map { it.name },
                    )
                } else {
                    val group = threadBookmarkRepo.observeSortedGroupsWithThreadBookmarks().first()
                        .firstOrNull { it.group.groupId == groupId }
                    ?: return@loadDeleteDialogData null
                    GroupDialogController.DeleteDialogData(
                        groupName = group.group.name,
                        items = group.threads.map { it.title },
                    )
                }
            },
            defaultColorName = BookmarkColor.RED.value,
        ),
    )

    // 初期化時にお気に入りリストを監視
    init {

        // グループ→板のネストリストを監視して UIState.boardList に流し込む
        viewModelScope.launch {
            boardRepo.observeGroupsWithBoards()
                .collect { groupsWithBoards ->
                    _uiState.update { it.copy(boardList = groupsWithBoards) }
                }
        }

        // スレッドのお気に入り一覧を監視
        viewModelScope.launch {
            threadBookmarkRepo.observeSortedGroupsWithThreadBookmarks()
                .collect { groupedThreads ->
                    _uiState.update { it.copy(groupedThreadBookmarks = groupedThreads) }
                }
        }
    }

    /**
     * 選択モードの有効/無効を切り替える。
     */
    fun toggleSelectMode(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectMode = enabled,
                selectedBoards = if (enabled) state.selectedBoards else emptySet(),
                selectedThreads = if (enabled) state.selectedThreads else emptySet()
            )
        }
    }

    /**
     * 指定の板を選択状態に切り替える。
     */
    fun toggleBoardSelect(boardId: Long) {
        _uiState.update { state ->
            val next = state.selectedBoards.toMutableSet().apply {
                if (!add(boardId)) remove(boardId)
            }
            state.copy(selectedBoards = next)
        }
    }

    /**
     * 指定のスレッドを選択状態に切り替える。
     */
    fun toggleThreadSelect(id: String) {
        _uiState.update { state ->
            val next = state.selectedThreads.toMutableSet().apply {
                if (!add(id)) remove(id)
            }
            state.copy(selectedThreads = next)
        }
    }

    /**
     * 選択中のグループを推測して編集シートを開く。
     */
    fun openEditSheet() {
        val groupId = computeSelectedGroupId()
        _uiState.update { it.copy(showEditSheet = true, selectedGroupId = groupId) }
    }

    fun closeEditSheet() {
        _uiState.update { it.copy(showEditSheet = false, selectedGroupId = null) }
    }

    /**
     * 選択中の板/スレッドへグループを一括適用する。
     */
    fun applyGroupToSelection(groupId: Long) {
        val current = _uiState.value
        viewModelScope.launch {
            current.selectedBoards.forEach { id ->
                boardRepo.upsertBookmark(BookmarkBoardEntity(boardId = id, groupId = groupId))
            }
            current.selectedThreads.forEach { key ->
                findThreadEntity(key)?.let { thread ->
                    threadBookmarkRepo.insertBookmark(thread.copy(groupId = groupId))
                }
            }
            _uiState.update {
                it.copy(
                    selectMode = false,
                    selectedBoards = emptySet(),
                    selectedThreads = emptySet(),
                    showEditSheet = false,
                    selectedGroupId = null
                )
            }
        }
    }

    /**
     * 選択中の板/スレッドのブックマークを一括解除する。
     */
    fun unbookmarkSelection() {
        val current = _uiState.value
        viewModelScope.launch {
            current.selectedBoards.forEach { id ->
                boardRepo.deleteBookmark(id)
            }
            current.selectedThreads.forEach { key ->
                findThreadEntity(key)?.let { thread ->
                    threadBookmarkRepo.deleteBookmark(thread.threadKey, thread.boardUrl)
                }
            }
            _uiState.update {
                it.copy(
                    selectMode = false,
                    selectedBoards = emptySet(),
                    selectedThreads = emptySet(),
                    showEditSheet = false,
                    selectedGroupId = null
                )
            }
        }
    }

    /**
     * 選択中のグループが1つに定まる場合、その ID を返す。
     */
    private fun computeSelectedGroupId(): Long? {
        val state = _uiState.value
        if (state.selectedBoards.isNotEmpty()) {
            val groups = state.selectedBoards.mapNotNull { findBoardGroupId(it) }.distinct()
            return if (groups.size == 1) groups.first() else null
        }
        if (state.selectedThreads.isNotEmpty()) {
            val groups = state.selectedThreads.mapNotNull { findThreadGroupId(it) }.distinct()
            return if (groups.size == 1) groups.first() else null
        }
        return null
    }

    /**
     * 板IDから所属グループIDを探索する。
     */
    private fun findBoardGroupId(boardId: Long): Long? {
        _uiState.value.boardList.forEach { g ->
            if (g.boards.any { it.boardId == boardId }) return g.group.groupId
        }
        return null
    }

    private fun findThreadGroupId(key: String): Long? = findThreadEntity(key)?.groupId

    /**
     * UI状態内のスレッド一覧からエンティティを探索する。
     */
    private fun findThreadEntity(key: String): BookmarkThreadEntity? {
        _uiState.value.groupedThreadBookmarks.forEach { g ->
            g.threads.forEach { t ->
                if (t.threadKey + t.boardUrl == key) return t
            }
        }
        return null
    }

    fun openAddGroupDialog(isBoard: Boolean) = groupDialogController.openAddGroupDialog(isBoard)

    fun openEditGroupDialog(group: Groupable, isBoard: Boolean) =
        groupDialogController.openEditGroupDialog(group, isBoard)

    fun closeAddGroupDialog() = groupDialogController.closeAddGroupDialog()

    fun setEnteredGroupName(name: String) = groupDialogController.setEnteredGroupName(name)

    fun setSelectedColor(color: String) = groupDialogController.setSelectedColor(color)

    fun confirmGroup() = groupDialogController.confirmGroup()

    fun requestDeleteGroup() = groupDialogController.requestDeleteGroup()

    fun confirmDeleteGroup() = groupDialogController.confirmDeleteGroup()

    fun closeDeleteGroupDialog() = groupDialogController.closeDeleteGroupDialog()
}

/**
 * ブックマーク一覧画面の UI 状態。
 *
 * 一覧表示とグループ編集の状態をまとめる。
 */
data class BookmarkUiState(
    val isLoading: Boolean = false,
    val boardList: List<GroupWithBoards> = emptyList(),
    val groupedThreadBookmarks: List<GroupWithThreadBookmarks> = emptyList(),
    val selectMode: Boolean = false,
    val selectedBoards: Set<Long> = emptySet(),
    val selectedThreads: Set<String> = emptySet(),
    val showEditSheet: Boolean = false,
    val selectedGroupId: Long? = null,
    val groupDialogState: GroupDialogState = GroupDialogState(),
)
