package com.websarva.wings.android.bbsviewer.ui.board

import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.ui.common.BaseUiState

data class BoardUiState(
    val threads: List<ThreadInfo>? = null,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val isBookmarked: Boolean = false,
    val groups: List<BoardBookmarkGroupEntity> = emptyList(),
    val selectedGroup: BoardBookmarkGroupEntity? = null,
    val showBookmarkSheet: Boolean = false,
    val showSortSheet: Boolean = false,

    val serviceName: String = "",
    val showInfoDialog: Boolean = false,

    val currentSortKey: ThreadSortKey = ThreadSortKey.DEFAULT,
    val isSortAscending: Boolean = false, // falseが降順、trueが昇順 (デフォルト降順)
    val sortKeys: List<ThreadSortKey> = ThreadSortKey.entries,

    val isSearchActive: Boolean = false, // 検索モードか
    val searchQuery: String = "", // 検索クエリ

    override val isLoading: Boolean = false,
    override val showAddGroupDialog: Boolean = false,
    override val selectedColor: String? = null,
    override val enteredGroupName: String = "",
    override val showTabListSheet: Boolean = false,
) : BaseUiState<BoardUiState> { // 自分自身の型を渡す
    override fun copyState(
        isLoading: Boolean,
        showAddGroupDialog: Boolean,
        enteredGroupName: String,
        selectedColor: String?,
        showTabListSheet: Boolean
    ): BoardUiState {
        // data classのcopyメソッドを呼び出して返す
        return this.copy(
            isLoading = isLoading,
            showAddGroupDialog = showAddGroupDialog,
            enteredGroupName = enteredGroupName,
            selectedColor = selectedColor,
            showTabListSheet = showTabListSheet
        )
    }
}

// 並び替え基準の定義
enum class ThreadSortKey(val displayName: String) {
    DEFAULT("デフォルト"), // サーバーから返ってきた順
    MOMENTUM("勢い"),
    RES_COUNT("レス数"),
    DATE_CREATED("作成日時") // スレッドキー順
}
