package com.websarva.wings.android.slevo.ui.thread.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ThreadViewModel に投稿機能を拡張するための関数群。
 */
fun ThreadViewModel.showPostDialog() {
    postDialogController.showDialog()
}

fun ThreadViewModel.showReplyDialog(resNum: Int) {
    postDialogController.showReplyDialog(resNum)
}

fun ThreadViewModel.hidePostDialog() {
    postDialogController.hideDialog()
}

fun ThreadViewModel.hideConfirmationScreen() {
    postDialogController.hideConfirmationScreen()
}

fun ThreadViewModel.updatePostName(name: String) {
    postDialogController.updateName(name)
}

fun ThreadViewModel.updatePostMail(mail: String) {
    postDialogController.updateMail(mail)
}

fun ThreadViewModel.updatePostMessage(message: String) {
    postDialogController.updateMessage(message)
}

fun ThreadViewModel.hideErrorWebView() {
    postDialogController.hideErrorWebView()
}

fun ThreadViewModel.postFirstPhase(
    host: String,
    board: String,
    threadKey: String,
) {
    postDialogController.postFirstPhase(host, board, threadKey)
}

fun ThreadViewModel.postTo5chSecondPhase(
    host: String,
    board: String,
    threadKey: String,
    confirmationData: ConfirmationData,
) {
    postDialogController.postSecondPhase(host, board, threadKey, confirmationData)
}

/**
 * 画像をアップロードし、成功時に本文へURLを挿入する。
 */
fun ThreadViewModel.uploadImage(context: Context, uri: Uri) {
    viewModelScope.launch {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
        bytes?.let {
            val url = imageUploadRepository.uploadImage(it)
            if (url != null) {
                val msg = postUiState.value.postFormState.message
                _postUiState.update { current ->
                    current.copy(
                        postFormState = current.postFormState.copy(message = msg + "\n" + url),
                    )
                }
            }
        }
    }
}

fun ThreadViewModel.clearPostResultMessage() {
    postDialogController.clearPostResultMessage()
}

fun ThreadViewModel.selectPostNameHistory(name: String) {
    postDialogController.selectNameHistory(name)
}

fun ThreadViewModel.selectPostMailHistory(mail: String) {
    postDialogController.selectMailHistory(mail)
}

fun ThreadViewModel.deletePostNameHistory(name: String) {
    postDialogController.deleteNameHistory(name)
}

fun ThreadViewModel.deletePostMailHistory(mail: String) {
    postDialogController.deleteMailHistory(mail)
}
