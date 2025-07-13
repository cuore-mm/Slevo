package com.websarva.wings.android.bbsviewer.data.datasource.remote

interface ImageUploadRemoteDataSource {
    suspend fun uploadImage(imageData: ByteArray): String?
}
