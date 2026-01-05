package com.websarva.wings.android.slevo.ui.bookmarklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.GroupWithBoards
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.GroupWithThreadBookmarks
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.common.bookmark.BoardTarget
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkTarget
import com.websarva.wings.android.slevo.ui.common.bookmark.ThreadTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ブックマーク一覧画面のUI状態を管理するViewModel。
 *
 * 一覧の選択状態とブックマークシートの操作を扱う。
 */
@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val boardRepo: BookmarkBoardRepository,
    private val threadBookmarkRepo: ThreadBookmarkRepository,
    private val bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarkUiState())
    val uiState: StateFlow<BookmarkUiState> = _uiState.asStateFlow()

    private var bookmarkSheetHolder: BookmarkBottomSheetStateHolder? = null
    private var bookmarkSheetJob: Job? = null

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
        if (!enabled) {
            closeEditSheet()
        }
    }

    /**
     * 板の選択状態を切り替える。
     */
    fun toggleBoardSelect(boardId: Long) {
        _uiState.update { state ->
            val next = state.selectedBoards.toMutableSet().apply {
                if (!add(boardId)) remove(boardId)
            }
            // 板とスレの混在選択を避けるため、スレ選択はクリアする。
            val clearedThreads = if (state.selectedThreads.isNotEmpty()) emptySet() else state.selectedThreads
            state.copy(selectedBoards = next, selectedThreads = clearedThreads)
        }
    }

    /**
     * スレッドの選択状態を切り替える。
     */
    fun toggleThreadSelect(id: String) {
        _uiState.update { state ->
            val next = state.selectedThreads.toMutableSet().apply {
                if (!add(id)) remove(id)
            }
            // 板とスレの混在選択を避けるため、板選択はクリアする。
            val clearedBoards = if (state.selectedBoards.isNotEmpty()) emptySet() else state.selectedBoards
            state.copy(selectedBoards = clearedBoards, selectedThreads = next)
        }
    }

    /**
     * 選択中の対象を元にブックマークシートを開く。
     */
    fun openEditSheet() {
        val targets = buildTargetsForSelection()
        if (targets.isEmpty()) {
            // 選択が空の場合は開かない。
            return
        }

        bookmarkSheetHolder?.dispose()
        bookmarkSheetJob?.cancel()

        val holder = bookmarkSheetStateHolderFactory.create(viewModelScope, targets)
        bookmarkSheetHolder = holder
        bookmarkSheetJob = viewModelScope.launch {
            holder.uiState.collect { sheetState ->
                _uiState.update { it.copy(bookmarkSheetState = sheetState) }
            }
        }
        _uiState.update {
            it.copy(
                showBookmarkSheet = true,
                bookmarkSheetState = holder.uiState.value
            )
        }
    }

    /**
     * ブックマークシートを閉じてステートホルダーを破棄する。
     */
    fun closeEditSheet() {
        _uiState.update {
            it.copy(
                showBookmarkSheet = false,
                bookmarkSheetState = BookmarkSheetUiState()
            )
        }
        bookmarkSheetJob?.cancel()
        bookmarkSheetHolder?.dispose()
        bookmarkSheetJob = null
        bookmarkSheetHolder = null
    }

    /**
     * 選択対象にグループを適用する。
     */
    fun applyGroupToSelection(groupId: Long) {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.applyGroup(groupId)
            resetSelectionAndSheet()
        }
    }

    /**
     * 選択対象のブックマークを解除する。
     */
    fun unbookmarkSelection() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.unbookmarkTargets()
            resetSelectionAndSheet()
        }
    }

    /**
     * 選択中の対象からブックマークシート用targetsを組み立てる。
     */
    private fun buildTargetsForSelection(): List<BookmarkTarget> {
        val state = _uiState.value
        if (state.selectedBoards.isNotEmpty() && state.selectedThreads.isNotEmpty()) {
            // 板とスレの混在選択は許可しない。
            return emptyList()
        }
        if (state.selectedBoards.isNotEmpty()) {
            return state.selectedBoards.mapNotNull { id -> buildBoardTarget(id) }
        }
        if (state.selectedThreads.isNotEmpty()) {
            return state.selectedThreads.mapNotNull { key -> buildThreadTarget(key) }
        }
        return emptyList()
    }

    /**
     * 板選択からtargetを生成する。
     */
    private fun buildBoardTarget(boardId: Long): BookmarkTarget? {
        val board = findBoardEntity(boardId) ?: return null
        val groupId = findBoardGroupId(boardId)
        return BoardTarget(
            boardInfo = BoardInfo(
                boardId = board.boardId,
                name = board.name,
                url = board.url
            ),
            currentGroupId = groupId
        )
    }

    /**
     * スレ選択からtargetを生成する。
     */
    private fun buildThreadTarget(key: String): BookmarkTarget? {
        val thread = findThreadEntity(key) ?: return null
        return ThreadTarget(
            boardInfo = BoardInfo(
                boardId = thread.boardId,
                name = thread.boardName,
                url = thread.boardUrl
            ),
            threadInfo = ThreadInfo(
                title = thread.title,
                key = thread.threadKey,
                resCount = thread.resCount
            ),
            currentGroupId = thread.groupId
        )
    }

    /**
     * 指定したboardIdに一致するBoardEntityを検索する。
     */
    private fun findBoardEntity(boardId: Long): BoardEntity? {
        _uiState.value.boardList.forEach { g ->
            g.boards.firstOrNull { it.boardId == boardId }?.let { return it }
        }
        return null
    }

    /**
     * 指定したboardIdに紐づくグループIDを取得する。
     */
    private fun findBoardGroupId(boardId: Long): Long? {
        _uiState.value.boardList.forEach { g ->
            if (g.boards.any { it.boardId == boardId }) return g.group.groupId
        }
        return null
    }

    /**
     * 指定したキーに一致するスレッドブックマークを検索する。
     */
    private fun findThreadEntity(key: String): BookmarkThreadEntity? {
        _uiState.value.groupedThreadBookmarks.forEach { g ->
            g.threads.forEach { t ->
                if (t.threadKey + t.boardUrl == key) return t
            }
        }
        return null
    }

    /**
     * 選択状態とシート表示をリセットする。
     */
    private fun resetSelectionAndSheet() {
        closeEditSheet()
        _uiState.update {
            it.copy(
                selectMode = false,
                selectedBoards = emptySet(),
                selectedThreads = emptySet()
            )
        }
    }

    fun openAddGroupDialog() {
        bookmarkSheetHolder?.openAddGroupDialog()
    }

    fun openEditGroupDialog(group: Groupable) {
        bookmarkSheetHolder?.openEditGroupDialog(group)
    }

    fun closeAddGroupDialog() {
        bookmarkSheetHolder?.closeAddGroupDialog()
    }

    fun setEnteredGroupName(name: String) {
        bookmarkSheetHolder?.setEnteredGroupName(name)
    }

    fun setSelectedColor(color: String) {
        bookmarkSheetHolder?.setSelectedColor(color)
    }

    /**
     * グループ追加/編集を確定する。
     */
    fun confirmGroup() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.confirmGroup()
        }
    }

    /**
     * グループ削除確認ダイアログを開く。
     */
    fun requestDeleteGroup() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.requestDeleteGroup()
        }
    }

    /**
     * グループ削除を確定する。
     */
    fun confirmDeleteGroup() {
        val holder = bookmarkSheetHolder ?: return
        viewModelScope.launch {
            holder.confirmDeleteGroup()
        }
    }

    fun closeDeleteGroupDialog() {
        bookmarkSheetHolder?.closeDeleteGroupDialog()
    }
}

/**
 * ブックマーク一覧画面のUI状態。
 *
 * 一覧表示とシート表示の状態を保持する。
 */
data class BookmarkUiState(
    val isLoading: Boolean = false,
    val boardList: List<GroupWithBoards> = emptyList(),
    val groupedThreadBookmarks: List<GroupWithThreadBookmarks> = emptyList(),
    val selectMode: Boolean = false,
    val selectedBoards: Set<Long> = emptySet(),
    val selectedThreads: Set<String> = emptySet(),
    val showBookmarkSheet: Boolean = false,
    val bookmarkSheetState: BookmarkSheetUiState = BookmarkSheetUiState(),
)
