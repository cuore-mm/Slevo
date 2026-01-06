package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ブックマークシートの状態と操作をまとめて扱うステートホルダー。
 *
 * シートの開閉状態とtargetsのリストに対する同一処理を管理する。
 */
class BookmarkBottomSheetStateHolder(
    private val boardRepo: BookmarkBoardRepository,
    private val threadRepo: ThreadBookmarkRepository,
    parentScope: CoroutineScope,
) {

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(parentScope.coroutineContext + scopeJob)
    private var targets: List<BookmarkTarget> = emptyList()
    private var groupsJob: Job? = null

    private val _uiState = MutableStateFlow(BookmarkSheetUiState())
    val uiState: StateFlow<BookmarkSheetUiState> = _uiState.asStateFlow()

    /**
     * シートを開いてtargetsを設定する。
     */
    fun open(targets: List<BookmarkTarget>) {
        if (targets.isEmpty()) {
            // 空の場合はシートを開かない。
            return
        }
        require(!hasMixedTargets(targets)) { "Board and thread targets must not be mixed." }
        this.targets = targets
        val isBoardTargets = isBoardTargets(targets)
        _uiState.update {
            it.copy(
                isVisible = true,
                groups = emptyList(),
                selectedGroupId = resolveSelectedGroupId(targets),
                showAddGroupDialog = false,
                enteredGroupName = "",
                selectedColor = BookmarkColor.RED.value,
                editingGroupId = null,
                showDeleteGroupDialog = false,
                deleteGroupName = "",
                deleteGroupItems = emptyList(),
                deleteGroupIsBoard = isBoardTargets
            )
        }
        startObservingGroups(isBoardTargets)
    }

    /**
     * シートを閉じて一時状態をリセットする。
     */
    fun close() {
        if (!_uiState.value.isVisible) {
            // すでに閉じている場合は更新しない。
            return
        }
        groupsJob?.cancel()
        groupsJob = null
        targets = emptyList()
        _uiState.update { BookmarkSheetUiState() }
    }

    /**
     * targetsへ指定グループを適用する。
     */
    fun applyGroup(groupId: Long) {
        val targetsSnapshot = targets
        if (targetsSnapshot.isEmpty()) {
            // 空の場合は処理を行わない。
            return
        }

        val isBoardTargets = isBoardTargets(targetsSnapshot)
        scope.launch {
            if (isBoardTargets) {
                // --- Persistence ---
                targetsSnapshot.filterIsInstance<BoardTarget>().forEach { target ->
                    boardRepo.upsertBookmark(target.boardInfo, groupId)
                }
            } else {
                // --- Persistence ---
                targetsSnapshot.filterIsInstance<ThreadTarget>().forEach { target ->
                    // --- Mapping ---
                    // ThreadInfo と BoardInfo を BookmarkThreadEntity に変換する。
                    threadRepo.insertBookmark(
                        BookmarkThreadEntity(
                            threadKey = target.threadInfo.key,
                            boardUrl = target.boardInfo.url,
                            boardId = target.boardInfo.boardId,
                            groupId = groupId,
                            title = target.threadInfo.title,
                            boardName = target.boardInfo.name,
                            resCount = target.threadInfo.resCount
                        )
                    )
                }
            }

            _uiState.update { it.copy(selectedGroupId = groupId) }
        }
    }

    /**
     * targetsのブックマークを解除する。
     */
    fun unbookmarkTargets() {
        val targetsSnapshot = targets
        if (targetsSnapshot.isEmpty()) {
            // 空の場合は処理を行わない。
            return
        }

        val isBoardTargets = isBoardTargets(targetsSnapshot)
        scope.launch {
            if (isBoardTargets) {
                targetsSnapshot.filterIsInstance<BoardTarget>().forEach { target ->
                    if (target.boardInfo.boardId == 0L) {
                        // 未登録のboardIdは削除できないためスキップする。
                        return@forEach
                    }
                    boardRepo.deleteBookmark(target.boardInfo.boardId)
                }
            } else {
                targetsSnapshot.filterIsInstance<ThreadTarget>().forEach { target ->
                    threadRepo.deleteBookmark(target.threadInfo.key, target.boardInfo.url)
                }
            }

            _uiState.update { it.copy(selectedGroupId = null) }
        }
    }

    /**
     * グループ追加ダイアログを開く。
     */
    fun openAddGroupDialog() {
        _uiState.update {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = "",
                selectedColor = BookmarkColor.RED.value,
                editingGroupId = null
            )
        }
    }

    /**
     * グループ編集ダイアログを開く。
     */
    fun openEditGroupDialog(group: Groupable) {
        _uiState.update {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = group.name,
                selectedColor = group.colorName,
                editingGroupId = group.id
            )
        }
    }

    /**
     * グループ追加/編集ダイアログを閉じる。
     */
    fun closeAddGroupDialog() {
        _uiState.update {
            it.copy(
                showAddGroupDialog = false,
                enteredGroupName = "",
                selectedColor = BookmarkColor.RED.value,
                editingGroupId = null
            )
        }
    }

    /**
     * 入力中のグループ名を更新する。
     */
    fun setEnteredGroupName(name: String) {
        _uiState.update { it.copy(enteredGroupName = name) }
    }

    /**
     * 入力中のグループ色を更新する。
     */
    fun setSelectedColor(color: String) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    /**
     * 入力中のグループ内容を保存する。
     */
    fun confirmGroup() {
        val name = _uiState.value.enteredGroupName
        if (name.isBlank()) {
            // 空入力は確定しない。
            return
        }
        val color = _uiState.value.selectedColor
        val editId = _uiState.value.editingGroupId
        scope.launch {
            if (editId == null) {
                addGroup(name, color)
            } else {
                updateGroup(editId, name, color)
            }
            closeAddGroupDialog()
        }
    }

    /**
     * グループ削除ダイアログを開く。
     */
    fun requestDeleteGroup() {
        // 編集対象が未設定の場合は開かない。
        val groupId = _uiState.value.editingGroupId ?: return

        scope.launch {
            // --- Load ---
            val (groupName, items) = if (isBoardTargets()) {
                val group = boardRepo.observeGroupsWithBoards().first()
                    .firstOrNull { it.group.groupId == groupId }
                    ?: return@launch
                val itemNames = group.boards.map { it.name }
                group.group.name to itemNames
            } else {
                val group = threadRepo.observeSortedGroupsWithThreadBookmarks().first()
                    .firstOrNull { it.group.groupId == groupId }
                    ?: return@launch
                val itemNames = group.threads.map { it.title }
                group.group.name to itemNames
            }

            _uiState.update {
                it.copy(
                    showDeleteGroupDialog = true,
                    deleteGroupName = groupName,
                    deleteGroupItems = items,
                    deleteGroupIsBoard = isBoardTargets()
                )
            }
        }
    }

    /**
     * グループ削除を確定する。
     */
    fun confirmDeleteGroup() {
        // 編集対象が未設定の場合は削除しない。
        val groupId = _uiState.value.editingGroupId ?: return
        scope.launch {
            if (isBoardTargets()) {
                boardRepo.deleteGroup(groupId)
            } else {
                threadRepo.deleteGroup(groupId)
            }
            _uiState.update { it.copy(showDeleteGroupDialog = false) }
            closeAddGroupDialog()
        }
    }

    /**
     * グループ削除ダイアログを閉じる。
     */
    fun closeDeleteGroupDialog() {
        _uiState.update { it.copy(showDeleteGroupDialog = false) }
    }

    /**
     * ステートホルダーの購読とジョブを破棄する。
     */
    fun dispose() {
        groupsJob?.cancel()
        scopeJob.cancel()
    }

    /**
     * 対象種別に応じたグループ一覧を購読してUI状態に反映する。
     */
    private fun startObservingGroups(isBoardTargets: Boolean) {
        groupsJob?.cancel()
        groupsJob = scope.launch {
            val groupsFlow = if (isBoardTargets) {
                boardRepo.observeGroups()
            } else {
                threadRepo.observeAllGroups()
            }
            groupsFlow.collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
    }

    /**
     * targetsから共通のグループIDを抽出する。
     */
    private fun resolveSelectedGroupId(targets: List<BookmarkTarget>): Long? {
        val groupIds = targets.mapNotNull { it.currentGroupId }.distinct()
        return if (groupIds.size == 1) groupIds.first() else null
    }

    /**
     * targetsが板対象かどうかを判定する。
     */
    private fun isBoardTargets(targets: List<BookmarkTarget>): Boolean = targets.firstOrNull() is BoardTarget

    /**
     * 現在のtargetsが板対象かどうかを判定する。
     */
    private fun isBoardTargets(): Boolean = isBoardTargets(targets)

    /**
     * 板とスレのtargetsが混在しているかを判定する。
     */
    private fun hasMixedTargets(targets: List<BookmarkTarget>): Boolean {
        val hasBoard = targets.any { it is BoardTarget }
        val hasThread = targets.any { it is ThreadTarget }
        return hasBoard && hasThread
    }

    /**
     * グループを追加する。
     */
    private suspend fun addGroup(name: String, color: String) {
        if (isBoardTargets()) {
            boardRepo.addGroupAtEnd(name, color)
        } else {
            threadRepo.addGroupAtEnd(name, color)
        }
    }

    /**
     * グループを更新する。
     */
    private suspend fun updateGroup(id: Long, name: String, color: String) {
        if (isBoardTargets()) {
            boardRepo.updateGroup(id, name, color)
        } else {
            threadRepo.updateGroup(id, name, color)
        }
    }
}

/**
 * BookmarkBottomSheetStateHolder を生成するためのファクトリ。
 */
class BookmarkBottomSheetStateHolderFactory @Inject constructor(
    private val boardRepo: BookmarkBoardRepository,
    private val threadRepo: ThreadBookmarkRepository,
) {

    /**
     * ステートホルダーを生成する。
     */
    fun create(
        parentScope: CoroutineScope,
    ): BookmarkBottomSheetStateHolder {
        return BookmarkBottomSheetStateHolder(
            boardRepo = boardRepo,
            threadRepo = threadRepo,
            parentScope = parentScope,
        )
    }
}
