package com.websarva.wings.android.slevo.ui.board.viewmodel

import android.content.Context
import android.net.Uri
import com.websarva.wings.android.slevo.data.repository.ImageUploadRepository
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BoardImageUploader @AssistedInject constructor(
    private val imageUploadRepository: ImageUploadRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val dispatcher: CoroutineDispatcher,
    @Assisted private val updateState: ((BoardUiState) -> BoardUiState) -> Unit,
) {

    fun uploadImage(context: Context, uri: Uri) {
        scope.launch {
            val bytes = withContext(dispatcher) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            bytes?.let {
                val url = imageUploadRepository.uploadImage(it)
                if (url != null) {
                    updateState { current ->
                        val currentMessage = current.createFormState.message
                        val appended = currentMessage + "\n" + url
                        current.copy(
                            createFormState = current.createFormState.copy(message = appended),
                        )
                    }
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher,
            updateState: ((BoardUiState) -> BoardUiState) -> Unit,
        ): BoardImageUploader
    }
}
