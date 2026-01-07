package com.websarva.wings.android.slevo.ui.common.postdialog

import android.content.Context
import android.net.Uri
import com.websarva.wings.android.slevo.data.repository.ImageUploadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PostDialog向けの画像アップロードを共通化するヘルパー。
 *
 * 画像取得とアップロードを行い、成功時にURLを返す。
 */
class PostDialogImageUploader @AssistedInject constructor(
    private val imageUploadRepository: ImageUploadRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val dispatcher: CoroutineDispatcher,
) {
    /**
     * 画像をアップロードし、成功時にURLをコールバックで渡す。
     */
    fun uploadImage(
        context: Context,
        uri: Uri,
        onUploaded: (String) -> Unit,
    ) {
        scope.launch {
            // --- IO ---
            val bytes = withContext(dispatcher) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            // 画像取得失敗時は何もしない。
            bytes?.let {
                val url = imageUploadRepository.uploadImage(it)
                if (url != null) {
                    onUploaded(url)
                }
            }
        }
    }

    /**
     * PostDialogImageUploader を生成するためのファクトリ。
     */
    @AssistedFactory
    interface Factory {
        fun create(
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher,
        ): PostDialogImageUploader
    }
}
