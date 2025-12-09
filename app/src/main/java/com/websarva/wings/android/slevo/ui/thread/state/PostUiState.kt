package com.websarva.wings.android.slevo.ui.thread.state

import com.websarva.wings.android.slevo.data.repository.ConfirmationData

/**
 * 投稿機能用のUI状態を保持するデータクラス
 */
data class PostUiState(
    val postDialog: Boolean = false,
    val postFormState: PostFormState = PostFormState(),
    val nameHistory: List<String> = emptyList(),
    val mailHistory: List<String> = emptyList(),
    val isPosting: Boolean = false,
    val postConfirmation: ConfirmationData? = null,
    val isConfirmationScreen: Boolean = false,
    val showErrorWebView: Boolean = false,
    val errorHtmlContent: String = "",
    val postResultMessage: String? = null,
)

/**
 * 投稿フォームの入力状態
 */
data class PostFormState(
    val name: String = "",
    val mail: String = "",
    val title: String = "",
    val message: String = "",
)

sealed class PostDialogAction {
    data class ChangeName(val value: String) : PostDialogAction()
    data class ChangeMail(val value: String) : PostDialogAction()
    data class ChangeTitle(val value: String) : PostDialogAction()
    data class ChangeMessage(val value: String) : PostDialogAction()
    data class SelectNameHistory(val value: String) : PostDialogAction()
    data class SelectMailHistory(val value: String) : PostDialogAction()
    data class DeleteNameHistory(val value: String) : PostDialogAction()
    data class DeleteMailHistory(val value: String) : PostDialogAction()
    object Post : PostDialogAction()
}
