package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import com.websarva.wings.android.bbsviewer.data.datasource.remote.DatRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient
) : DatRemoteDataSource {
    override suspend fun fetchDatString(datUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(datUrl)
                // 必要であればUser-Agentなどのヘッダーを追加
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()?.let { bytes ->
                        String(bytes, Charset.forName("Shift_JIS"))
                    }
                } else {
                    // エラーログなどをここに追加しても良い
                    null
                }
            }
        } catch (e: IOException) {
            // エラーログなどをここに追加しても良い
            null
        } catch (e: Exception) {
            // その他の予期せぬエラー
            null
        }
    }
}
