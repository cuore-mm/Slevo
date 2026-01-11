package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.repository.ConfirmationData

/**
 * PostDialogの共通的な表示・投稿状態を表すデータ。
 *
 * Thread/BoardのUI状態から必要な項目を抽出し、コントローラが扱う単位にまとめる。
 */
data class PostDialogState(
    val isDialogVisible: Boolean = false,
    val formState: PostFormState = PostFormState(),
    val namePlaceholder: String = "",
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
 * 投稿フォームの入力状態。
 *
 * PostDialogState内の入力値をまとめて保持する。
 */
data class PostFormState(
    val name: String = "",
    val mail: String = "",
    val title: String = "",
    val message: String = "",
)
