package com.websarva.wings.android.slevo.ui.thread.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityType
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.PostResult
import com.websarva.wings.android.slevo.ui.thread.state.PostFormState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ThreadViewModel に投稿機能を拡張するための関数群。
 */
fun ThreadViewModel.showPostDialog() {
    _postUiState.update { it.copy(postDialog = true) }
}

fun ThreadViewModel.showReplyDialog(resNum: Int) {
    _postUiState.update { current ->
        val message = current.postFormState.message
        val separator = if (message.isNotEmpty() && !message.endsWith("\n")) "\n" else ""
        current.copy(
            postDialog = true,
            postFormState = current.postFormState.copy(
                message = message + separator + ">>${resNum}\n"
            )
        )
    }
}

fun ThreadViewModel.hidePostDialog() {
    _postUiState.update { it.copy(postDialog = false) }
}

fun ThreadViewModel.hideConfirmationScreen() {
    _postUiState.update { it.copy(isConfirmationScreen = false) }
}

fun ThreadViewModel.updatePostName(name: String) {
    _postUiState.update { it.copy(postFormState = it.postFormState.copy(name = name)) }
    refreshPostIdentityHistory(PostIdentityType.NAME)
}

fun ThreadViewModel.updatePostMail(mail: String) {
    _postUiState.update { it.copy(postFormState = it.postFormState.copy(mail = mail)) }
    refreshPostIdentityHistory(PostIdentityType.EMAIL)
}

fun ThreadViewModel.updatePostMessage(message: String) {
    _postUiState.update { it.copy(postFormState = it.postFormState.copy(message = message)) }
}

fun ThreadViewModel.hideErrorWebView() {
    _postUiState.update { it.copy(showErrorWebView = false, errorHtmlContent = "") }
}

fun ThreadViewModel.postFirstPhase(
    host: String,
    board: String,
    threadKey: String,
    name: String,
    mail: String,
    message: String,
    onSuccess: (Int?) -> Unit,
) {
    viewModelScope.launch {
        _postUiState.update { it.copy(isPosting = true, postDialog = false) }
        val result =
            postRepository.postTo5chFirstPhase(host, board, threadKey, name, mail, message)
        _postUiState.update { it.copy(isPosting = false) }
        when (result) {
            is PostResult.Success -> {
                _postUiState.update {
                    it.copy(
                        postResultMessage = "書き込みに成功しました。",
                        postFormState = PostFormState()
                    )
                }
                onSuccess(result.resNum)
            }
            is PostResult.Confirm -> {
                _postUiState.update {
                    it.copy(postConfirmation = result.confirmationData, isConfirmationScreen = true)
                }
            }
            is PostResult.Error -> {
                _postUiState.update {
                    it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                }
            }
        }
    }
}

fun ThreadViewModel.postTo5chSecondPhase(
    host: String,
    board: String,
    threadKey: String,
    confirmationData: ConfirmationData,
    onSuccess: (Int?) -> Unit,
) {
    viewModelScope.launch {
        _postUiState.update { it.copy(isPosting = true, isConfirmationScreen = false) }
        val result = postRepository.postTo5chSecondPhase(host, board, threadKey, confirmationData)
        _postUiState.update { it.copy(isPosting = false) }
        when (result) {
            is PostResult.Success -> {
                _postUiState.update {
                    it.copy(
                        postResultMessage = "書き込みに成功しました。",
                        postFormState = PostFormState()
                    )
                }
                onSuccess(result.resNum)
            }
            is PostResult.Error -> {
                _postUiState.update {
                    it.copy(showErrorWebView = true, errorHtmlContent = result.html)
                }
            }
            is PostResult.Confirm -> {
                _postUiState.update {
                    it.copy(postConfirmation = result.confirmationData, isConfirmationScreen = true)
                }
            }
        }
    }
}

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
    _postUiState.update { it.copy(postResultMessage = null) }
}

fun ThreadViewModel.selectPostNameHistory(name: String) {
    _postUiState.update { it.copy(postFormState = it.postFormState.copy(name = name)) }
    refreshPostIdentityHistory(PostIdentityType.NAME)
}

fun ThreadViewModel.selectPostMailHistory(mail: String) {
    _postUiState.update { it.copy(postFormState = it.postFormState.copy(mail = mail)) }
    refreshPostIdentityHistory(PostIdentityType.EMAIL)
}

fun ThreadViewModel.deletePostNameHistory(name: String) {
    deletePostIdentity(PostIdentityType.NAME, name)
}

fun ThreadViewModel.deletePostMailHistory(mail: String) {
    deletePostIdentity(PostIdentityType.EMAIL, mail)
}

