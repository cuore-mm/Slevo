package com.websarva.wings.android.slevo.data.util

import androidx.core.text.HtmlCompat
import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.max

object ThreadListParser {
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
                    val title =
                        HtmlCompat.fromHtml(titleHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                    val resCount = resCountStr.toIntOrNull() ?: 0
                    val threadEpochSeconds = key.toLongOrNull() ?: 0L

                    val momentum =
                        if (threadEpochSeconds in 1 until THREAD_KEY_THRESHOLD && resCount > 0) {
                            val elapsedTimeSeconds =
                                max(1, currentUnixTime - threadEpochSeconds) // 経過時間（秒）、最低1秒
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

    // スレッドキーからスレ作成日を計算（java.time を使わず互換性を保つ）
    fun calculateThreadDate(threadKey: String): ThreadDate {
        val epochSeconds =
            threadKey.toLongOrNull() ?: throw IllegalArgumentException("Invalid thread key")
        val date = Date(epochSeconds * 1000L)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply { time = date }
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "月"
            Calendar.TUESDAY -> "火"
            Calendar.WEDNESDAY -> "水"
            Calendar.THURSDAY -> "木"
            Calendar.FRIDAY -> "金"
            Calendar.SATURDAY -> "土"
            else -> "日" // Calendar.SUNDAY
        }
        return ThreadDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            dayOfWeek = dayOfWeek
        )
    }
}
