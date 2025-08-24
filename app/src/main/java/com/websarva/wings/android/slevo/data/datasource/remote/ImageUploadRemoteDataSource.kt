package com.websarva.wings.android.slevo.data.datasource.remote

interface ImageUploadRemoteDataSource {
    suspend fun uploadImage(imageData: ByteArray): String?
}
