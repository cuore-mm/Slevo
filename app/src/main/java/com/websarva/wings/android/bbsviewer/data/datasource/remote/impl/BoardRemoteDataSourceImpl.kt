package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import android.util.Log
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BoardRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BoardRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient,
    @Named("UserAgent") private val userAgent: String
) : BoardRemoteDataSource {

    override suspend fun fetchSubjectTxt(url: String, forceRefresh: Boolean): String? =
        withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)

            if (forceRefresh) {
                requestBuilder.cacheControl(CacheControl.Builder().noCache().build())
            }

            val request = requestBuilder.build()

            try {
                val response = client.newCall(request).execute()

                // 詳細ログ（開発中に役立ちます）
                val networkResponse = response.networkResponse
                val cacheResponse = response.cacheResponse
                Log.d("BoardRemoteDataSource", "URL: $url, forceRefresh: $forceRefresh")
                Log.d("BoardRemoteDataSource", "Final Response Code: ${response.code}")
                if (networkResponse != null) {
                    Log.d(
                        "BoardRemoteDataSource",
                        "Network Response: code=${networkResponse.code}, headers=${networkResponse.headers}"
                    )
                } else {
                    Log.d(
                        "BoardRemoteDataSource",
                        "Network Response: null (no network call or not applicable)"
                    )
                }
                if (cacheResponse != null) {
                    Log.d(
                        "BoardRemoteDataSource",
                        "Cache Response: code=${cacheResponse.code}, headers=${cacheResponse.headers}"
                    )
                } else {
                    Log.d(
                        "BoardRemoteDataSource",
                        "Cache Response: null (no cache hit or not applicable)"
                    )
                }
                Log.d("BoardRemoteDataSource", "Response Headers (final): ${response.headers}")


                // ネットワークレスポンスが304であれば、内容に変更なしと判断
                if (networkResponse?.code == 304) {
                    Log.i(
                        "BoardRemoteDataSource",
                        "Network returned 304 for $url. Content not modified."
                    )
                    return@withContext null // データ更新なし
                }

                return@withContext when (response.code) {
                    200 -> response.use {
                        Log.i("BoardRemoteDataSource", "Received 200 for $url. Processing body.")
                        it.body?.bytes()?.toString(Charset.forName("Shift_JIS"))
                            ?: run {
                                Log.e(
                                    "BoardRemoteDataSource",
                                    "Response body was null for 200 response on $url"
                                )
                                null
                            }
                    }
                    // OkHttpがキャッシュヒットし、かつネットワーク検証なしで response.code が直接304を返す可能性は低いが念のため
                    304 -> {
                        Log.i(
                            "BoardRemoteDataSource",
                            "Final response.code was 304 for $url. Content not modified."
                        )
                        null
                    }

                    else -> {
                        Log.e(
                            "BoardRemoteDataSource",
                            "Unexpected final response code ${response.code} for $url"
                        )
                        null
                    }
                }
            } catch (e: IOException) {
                Log.e("BoardRemoteDataSource", "IOException for $url: ${e.message}", e)
                null
            } catch (e: Exception) { // その他の予期せぬ例外
                Log.e("BoardRemoteDataSource", "Exception for $url: ${e.message}", e)
                null
            }
        }
}
