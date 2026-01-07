package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.ui.thread.state.PostFormState

/**
 * PostDialogの共通的な表示・投稿状態を表すデータ。
 *
 * Thread/BoardのUI状態から必要な項目を抽出し、コントローラが扱う単位にまとめる。
 */
data class PostDialogState(
    val isDialogVisible: Boolean,
    val formState: PostFormState,
    val nameHistory: List<String>,
    val mailHistory: List<String>,
    val isPosting: Boolean,
    val postConfirmation: ConfirmationData?,
    val isConfirmationScreen: Boolean,
    val showErrorWebView: Boolean,
    val errorHtmlContent: String,
    val postResultMessage: String?,
)
