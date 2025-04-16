package com.websarva.wings.android.bbsviewer.data.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.data.model.ThreadDate
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object ThreadListParser {
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
