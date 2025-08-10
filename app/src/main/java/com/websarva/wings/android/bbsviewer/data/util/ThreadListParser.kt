package com.websarva.wings.android.bbsviewer.data.util

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat
import com.websarva.wings.android.bbsviewer.data.model.ThreadDate
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import com.websarva.wings.android.bbsviewer.data.model.THREAD_KEY_THRESHOLD

object ThreadListParser {
    @RequiresApi(Build.VERSION_CODES.O)
    fun parseSubjectTxt(text: String): List<ThreadInfo> {
        val threads = mutableListOf<ThreadInfo>()
        val regex = Regex("""^(\d+)\.dat<>(.+?)\s+\((\d+)\)$""")
        val currentUnixTime = System.currentTimeMillis() / 1000 // 現在のUNIX時間（秒）

        text.split("\n").forEach { line ->
            if (line.isNotBlank()) {
                val trimmedLine = line.trim()
                val match = regex.find(trimmedLine)
                if (match != null) {
                    val (key, titleHtml, resCountStr) = match.destructured
                    val title = HtmlCompat.fromHtml(titleHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                    val resCount = resCountStr.toIntOrNull() ?: 0
                    val threadEpochSeconds = key.toLongOrNull() ?: 0L

                    val momentum = if (threadEpochSeconds in 1 until THREAD_KEY_THRESHOLD && resCount > 0) {
                        val elapsedTimeSeconds = max(1, currentUnixTime - threadEpochSeconds) // 経過時間（秒）、最低1秒
                        val elapsedTimeDays = elapsedTimeSeconds / 86400.0 // 経過時間（日）
                        if (elapsedTimeDays > 0) {
                            resCount / elapsedTimeDays
                        } else {
                            0.0 // 経過時間が0日未満の場合は勢い0（または大きな値）
                        }
                    } else {
                        0.0
                    }

                    val date = if (threadEpochSeconds in 1 until THREAD_KEY_THRESHOLD) {
                        calculateThreadDate(key)
                    } else {
                        ThreadDate(0, 0, 0, 0, 0, "")
                    }

                    threads.add(
                        ThreadInfo(
                            title = title,
                            key = key,
                            resCount = resCount,
                            date = date,
                            momentum = momentum
                        )
                    )
                }
            }
        }
        return threads
    }

    //スレッドキーからスレ作成日を計算
    @RequiresApi(Build.VERSION_CODES.O)
    fun calculateThreadDate(threadKey: String): ThreadDate {
        // 数値に変換（スレッドキーはepochからの秒差）
        val epochSeconds =
            threadKey.toLongOrNull() ?: throw IllegalArgumentException("Invalid thread key")
        // Japan Standard Time (Asia/Tokyo) を利用して日時に変換
        val localDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("Asia/Tokyo"))
        // 曜日情報を取得し、日本語表記に変換
        val dayOfWeek = when (localDateTime.dayOfWeek) {
            DayOfWeek.MONDAY -> "月"
            DayOfWeek.TUESDAY -> "火"
            DayOfWeek.WEDNESDAY -> "水"
            DayOfWeek.THURSDAY -> "木"
            DayOfWeek.FRIDAY -> "金"
            DayOfWeek.SATURDAY -> "土"
            DayOfWeek.SUNDAY -> "日"
        }
        return ThreadDate(
            year = localDateTime.year,
            month = localDateTime.monthValue,
            day = localDateTime.dayOfMonth,
            hour = localDateTime.hour,
            minute = localDateTime.minute,
            dayOfWeek = dayOfWeek
        )
    }
}
