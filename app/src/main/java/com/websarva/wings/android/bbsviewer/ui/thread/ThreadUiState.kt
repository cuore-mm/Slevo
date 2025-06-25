package com.websarva.wings.android.bbsviewer.ui.thread

import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.ui.board.BoardUiState
import com.websarva.wings.android.bbsviewer.ui.common.BaseUiState

data class ThreadUiState(
    val threadInfo: ThreadInfo = ThreadInfo(),
    val posts: List<ReplyInfo>? = null,
    val loadProgress: Float = 0f,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val postDialog: Boolean = false,
    val postFormState: PostFormState = PostFormState(),
    val isPosting: Boolean = false,
    val postConfirmation: ConfirmationData? = null,
    val isConfirmationScreen: Boolean = false,

    // スレッドお気に入り関連のUI状態
    val isBookmarked: Boolean = false,
    val currentThreadGroup: ThreadBookmarkGroupEntity? = null,
    val availableThreadGroups: List<ThreadBookmarkGroupEntity> = emptyList(),
    val showThreadGroupSelector: Boolean = false,

    override val isLoading: Boolean = false,
    override val showAddGroupDialog: Boolean = false,
    override val selectedColor: String? = "#FF0000",
    override val enteredGroupName: String = "",
    override val showTabListSheet: Boolean = false,
) : BaseUiState<ThreadUiState> { // 自分自身の型を渡す
    override fun copyState(
        isLoading: Boolean,
        showAddGroupDialog: Boolean,
        enteredGroupName: String,
        selectedColor: String?,
        showTabListSheet: Boolean
    ): ThreadUiState {
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

data class ReplyInfo(
    val name: String,
    val email: String,
    val date: String,
    val id: String,
    val content: String
)

data class PostFormState(
    val name: String = "",
    val mail: String = "",
    val message: String = ""
)

