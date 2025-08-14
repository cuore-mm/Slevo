package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import com.websarva.wings.android.bbsviewer.data.datasource.remote.ImageDownloadRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.ImageDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageDownloadRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient
) : ImageDownloadRemoteDataSource {
    override fun downloadImage(url: String): Flow<ImageDownloadState> = flow {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: throw IOException("Empty body")
                val total = body.contentLength()
                emit(ImageDownloadState.Progress(0, total))
                val stream = body.byteStream()
                val buffer = ByteArray(8 * 1024)
                val output = ByteArrayOutputStream()
                var downloaded = 0L
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    emit(ImageDownloadState.Progress(downloaded, total))
                }
                emit(ImageDownloadState.Success(output.toByteArray()))
            }
        } catch (e: Exception) {
            emit(ImageDownloadState.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}
