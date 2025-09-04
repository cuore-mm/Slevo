package com.websarva.wings.android.slevo.ui.thread.state

import com.websarva.wings.android.slevo.data.repository.ConfirmationData

/**
 * 投稿機能用のUI状態を保持するデータクラス
 */
data class PostUiState(
    val postDialog: Boolean = false,
    val postFormState: PostFormState = PostFormState(),
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
    val message: String = "",
)
