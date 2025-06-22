package com.websarva.wings.android.bbsviewer.ui.thread

import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData

data class ThreadUiState(
    val threadInfo: ThreadInfo = ThreadInfo(),
    val posts: List<ReplyInfo>? = null,
    val isLoading: Boolean = false,
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
    val showAddGroupDialog: Boolean = false,
    val enteredNewGroupName: String = "",
    val selectedColorForNewGroup: String? = "#FF0000", // デフォルト色など適当に設定

    // タブ一覧ボトムシートの表示状態
    val showTabListSheet: Boolean = false,
)

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

