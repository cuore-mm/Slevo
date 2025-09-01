package com.websarva.wings.android.slevo.ui.thread.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.ImageUploadRepository
import com.websarva.wings.android.slevo.data.repository.PostRepository
import com.websarva.wings.android.slevo.data.repository.PostResult
import com.websarva.wings.android.slevo.ui.thread.state.PostUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PostViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val imageUploadRepository: ImageUploadRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostUiState())
    val uiState: StateFlow<PostUiState> = _uiState.asStateFlow()

    fun showPostDialog() {
        _uiState.update { it.copy(postDialog = true) }
    }

    fun hidePostDialog() {
        _uiState.update { it.copy(postDialog = false) }
    }

    fun hideConfirmationScreen() {
        _uiState.update { it.copy(isConfirmationScreen = false) }
    }

    fun updatePostName(name: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(name = name)) }
    }

    fun updatePostMail(mail: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(mail = mail)) }
    }

    fun updatePostMessage(message: String) {
        _uiState.update { it.copy(postFormState = it.postFormState.copy(message = message)) }
    }

    fun hideErrorWebView() {
        _uiState.update { it.copy(showErrorWebView = false, errorHtmlContent = "") }
    }

    fun postFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String,
        onSuccess: (Int?) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, postDialog = false) }
            val result =
                postRepository.postTo5chFirstPhase(host, board, threadKey, name, mail, message)

            _uiState.update { it.copy(isPosting = false) }

            when (result) {
                is PostResult.Success -> {
                    _uiState.update {
                        it.copy(
                            postResultMessage = "書き込みに成功しました。",
                        )
                    }
                    onSuccess(result.resNum)
                }

                is PostResult.Confirm -> {
                    _uiState.update {
                        it.copy(
                            postConfirmation = result.confirmationData,
                            isConfirmationScreen = true,
                        )
                    }
                }

                is PostResult.Error -> {
                    _uiState.update {
                        it.copy(
                            showErrorWebView = true,
                            errorHtmlContent = result.html,
                        )
                    }
                }
            }
        }
    }

    fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData,
        onSuccess: (Int?) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, isConfirmationScreen = false) }
            val result = postRepository.postTo5chSecondPhase(
                host,
                board,
                threadKey,
                confirmationData,
            )

            _uiState.update { it.copy(isPosting = false) }

            when (result) {
                is PostResult.Success -> {
                    _uiState.update {
                        it.copy(
                            postResultMessage = "書き込みに成功しました。",
                        )
                    }
                    onSuccess(result.resNum)
                }

                is PostResult.Error -> {
                    _uiState.update {
                        it.copy(
                            showErrorWebView = true,
                            errorHtmlContent = result.html,
                        )
                    }
                }

                is PostResult.Confirm -> {
                    _uiState.update {
                        it.copy(
                            postConfirmation = result.confirmationData,
                            isConfirmationScreen = true,
                        )
                    }
                }
            }
        }
    }

    fun uploadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            bytes?.let {
                val url = imageUploadRepository.uploadImage(it)
                if (url != null) {
                    val msg = uiState.value.postFormState.message
                    _uiState.update { current ->
                        current.copy(
                            postFormState = current.postFormState.copy(message = msg + "\n" + url),
                        )
                    }
                }
            }
        }
    }

    fun clearPostResultMessage() {
        _uiState.update { it.copy(postResultMessage = null) }
    }
}
