package com.websarva.wings.android.slevo.ui.util

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

/**
 * Coil の画像リクエストに対してダウンロード進捗を通知する OkHttp interceptor。
 *
 * レスポンスボディをラップし、受信バイト数を `ImageLoadProgressRegistry` へ中継する。
 */
class ImageLoadProgressInterceptor : Interceptor {
    /**
     * レスポンスボディの読み取りをフックして、URL単位の進捗を更新する。
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        ImageLoadProgressRegistry.start(url)

        val response = try {
            chain.proceed(request)
        } catch (exception: IOException) {
            // Fallback: 通信失敗時は進捗状態を破棄する。
            ImageLoadProgressRegistry.finish(url)
            throw exception
        }

        val body = response.body ?: run {
            // Guard: ボディなしレスポンスは進捗管理を終了する。
            ImageLoadProgressRegistry.finish(url)
            return response
        }

        val wrappedBody = ProgressResponseBody(
            origin = body,
            url = url,
        )
        return response.newBuilder().body(wrappedBody).build()
    }
}

/**
 * レスポンスボディ読み取り時に進捗通知を行うラッパー。
 */
private class ProgressResponseBody(
    private val origin: ResponseBody,
    private val url: String,
) : ResponseBody() {
    private val contentLength = origin.contentLength()
    private var bufferedSource: BufferedSource? = null

    /**
     * 元レスポンスのMIME typeを返す。
     */
    override fun contentType() = origin.contentType()

    /**
     * 元レスポンスのContent-Lengthを返す。
     */
    override fun contentLength() = contentLength

    /**
     * 進捗通知付きのソースを返す。
     */
    override fun source(): BufferedSource {
        val currentSource = bufferedSource
        if (currentSource != null) {
            return currentSource
        }
        val progressSource = ProgressSource(
            delegate = origin.source(),
            url = url,
            contentLength = contentLength,
        )
        val nextSource = progressSource.buffer()
        bufferedSource = nextSource
        return nextSource
    }
}

/**
 * 読み取りバイト数を監視し、進捗レジストリへ中継するソース。
 */
private class ProgressSource(
    delegate: Source,
    private val url: String,
    private val contentLength: Long,
) : ForwardingSource(delegate) {
    private var totalBytesRead: Long = 0L

    /**
     * 読み取りごとに累積バイト数を更新し、進捗を通知する。
     */
    @Throws(IOException::class)
    override fun read(sink: okio.Buffer, byteCount: Long): Long {
        return try {
            val bytesRead = super.read(sink, byteCount)
            if (bytesRead == -1L) {
                // Guard: 読み取り終端で進捗状態を破棄する。
                ImageLoadProgressRegistry.finish(url)
                return -1L
            }
            totalBytesRead += bytesRead
            ImageLoadProgressRegistry.update(
                url = url,
                bytesRead = totalBytesRead,
                contentLength = contentLength,
            )
            bytesRead
        } catch (exception: IOException) {
            // Fallback: 読み取り失敗時は進捗状態を破棄する。
            ImageLoadProgressRegistry.finish(url)
            throw exception
        }
    }
}
