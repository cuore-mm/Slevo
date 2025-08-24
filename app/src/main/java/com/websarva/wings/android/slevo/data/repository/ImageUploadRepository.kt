package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.remote.ImageUploadRemoteDataSource
import javax.inject.Inject

class ImageUploadRepository @Inject constructor(
    private val remote: ImageUploadRemoteDataSource
) {
    suspend fun uploadImage(imageData: ByteArray): String? =
        remote.uploadImage(imageData)
}
