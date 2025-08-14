package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import com.websarva.wings.android.bbsviewer.data.datasource.remote.ImageDownloadRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.ImageDownloadState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

class ImageDownloadRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient
) : ImageDownloadRemoteDataSource {
    override fun downloadImage(url: String): Flow<ImageDownloadState> = flow {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IllegalStateException("Response body is null")
            val total = body.contentLength()
            val stream = body.byteStream()
            val buffer = ByteArray(8 * 1024)
            val output = ByteArrayOutputStream()
            var bytesRead: Int
            var downloaded = 0L
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                emit(ImageDownloadState.Progress(downloaded, total))
            }
            emit(ImageDownloadState.Success(output.toByteArray()))
        }
    }.catch { e -> emit(ImageDownloadState.Error(e)) }
}

