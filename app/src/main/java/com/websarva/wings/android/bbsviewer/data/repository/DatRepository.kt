package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.util.parseDat
import com.websarva.wings.android.bbsviewer.ui.thread.ReplyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import javax.inject.Inject

class DatRepository @Inject constructor(
    private val client: OkHttpClient
) {
    private suspend fun fetchDatData(datUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(datUrl)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.bytes()?.let { bytes ->
                            String(bytes, Charset.forName("Shift_JIS"))
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getThread(url: String): Pair<List<ReplyInfo>, String?>{
        return parseDat(fetchDatData(url)!!)
    }
}
