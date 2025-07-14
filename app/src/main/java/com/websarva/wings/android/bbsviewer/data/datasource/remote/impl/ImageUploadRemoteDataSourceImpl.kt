package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import com.websarva.wings.android.bbsviewer.BuildConfig
import com.websarva.wings.android.bbsviewer.data.datasource.remote.ImageUploadRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.ImgBbResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageUploadRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient,
    moshi: Moshi
) : ImageUploadRemoteDataSource {

    private val adapter = moshi.adapter(ImgBbResponse::class.java)

    override suspend fun uploadImage(imageData: ByteArray): String? = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("key", BuildConfig.IMGBB_API_KEY)
            .addFormDataPart(
                "image",
                "image.jpg",
                imageData.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.imgbb.com/1/upload")
            .post(body)
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = response.body?.string() ?: return@use null
                val res = adapter.fromJson(json)
                res?.data?.url
            }
        } catch (e: Exception) {
            null
        }
    }
}
