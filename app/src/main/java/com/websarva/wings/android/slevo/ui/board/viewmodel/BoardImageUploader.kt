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

/**
 * 板画面の投稿フォーム向けに画像アップロードを行うヘルパー。
 */
class BoardImageUploader @AssistedInject constructor(
    private val imageUploadRepository: ImageUploadRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val dispatcher: CoroutineDispatcher,
    @Assisted private val updateState: ((BoardUiState) -> BoardUiState) -> Unit,
) {

    /**
     * 画像をアップロードし、成功時に投稿本文へURLを追記する。
     */
    fun uploadImage(context: Context, uri: Uri) {
        scope.launch {
            val bytes = withContext(dispatcher) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            // 画像取得失敗時は何もしない。
            bytes?.let {
                val url = imageUploadRepository.uploadImage(it)
                if (url != null) {
                    updateState { current ->
                        val currentMessage = current.postDialogState.formState.message
                        val appended = currentMessage + "\n" + url
                        current.copy(
                            postDialogState = current.postDialogState.copy(
                                formState = current.postDialogState.formState.copy(message = appended),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * BoardImageUploader を生成するためのファクトリ。
     */
    @AssistedFactory
    interface Factory {
        fun create(
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher,
            updateState: ((BoardUiState) -> BoardUiState) -> Unit,
        ): BoardImageUploader
    }
}
