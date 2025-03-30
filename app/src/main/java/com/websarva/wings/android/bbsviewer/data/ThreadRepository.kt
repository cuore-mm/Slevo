package com.websarva.wings.android.bbsviewer.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.ui.threadlist.ThreadDate
import com.websarva.wings.android.bbsviewer.ui.threadlist.ThreadInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

class ThreadRepository @Inject constructor(
    private val client: OkHttpClient
) {
    @Throws(IOException::class)
    fun fetchSubjectTxt(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            // Shift_JISでデコード
            val bytes = response.body?.bytes() ?: throw IOException("Empty body")
            return bytes.toString(Charset.forName("Shift_JIS"))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun parseSubjectTxt(text: String): List<ThreadInfo> {
        val threads = mutableListOf<ThreadInfo>()
        val regex = Regex("""^(\d+)\.dat<>(.+?)\s+\((\d+)\)$""")
        text.split("\n").forEach { line ->
            if (line.isNotBlank()) {
                val trimmedLine = line.trim()
                val match = regex.find(trimmedLine)
                if (match != null) {
                    val (key, title, resCountStr) = match.destructured
                    val resCount = resCountStr.toIntOrNull() ?: 0
                    threads.add(
                        ThreadInfo(
                            title = title,
                            key = key,
                            resCount = resCount,
                            date = calculateThreadDate(key)
                        )
                    )
                }
            }
        }
        return threads
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateThreadDate(threadKey: String): ThreadDate {
        // 数値に変換（スレッドキーはepochからの秒差）
        val epochSeconds =
            threadKey.toLongOrNull() ?: throw IllegalArgumentException("Invalid thread key")
        // Japan Standard Time (Asia/Tokyo) を利用して日時に変換
        val localDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("Asia/Tokyo"))
        return ThreadDate(
            year = localDateTime.year,
            month = localDateTime.monthValue,
            day = localDateTime.dayOfMonth,
            hour = localDateTime.hour,
            minute = localDateTime.minute
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getThreadList(url: String): List<ThreadInfo> {
        val subjectText = fetchSubjectTxt(url)
        val threads = parseSubjectTxt(subjectText)
        return threads
    }
}

