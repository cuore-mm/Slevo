package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.model.ThreadDate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.max

/**
 * スレッドキーから導出できる表示用派生情報（作成日時・勢い）を計算するユーティリティ。
 *
 * thread key が UNIX 秒由来の epoch 値の場合に、Asia/Tokyo 基準の作成日時と
 * レス数から勢いを導出する。無効な key では安全にデフォルト値を返す。
 */
object ThreadInfoDerivedCalculator {

    /**
     * thread key が有効な epoch thread key か判定する。
     */
    fun isEpochThreadKey(threadKey: String): Boolean {
        val keyLong = threadKey.toLongOrNull() ?: return false
        return keyLong in 1 until THREAD_KEY_THRESHOLD
    }

    /**
     * thread key からスレッド作成日時を導出する。
     * 無効な key の場合はデフォルト日付を返す。
     */
    fun calculateDate(threadKey: String): ThreadDate {
        val keyLong = threadKey.toLongOrNull() ?: return defaultDate()
        if (keyLong !in 1 until THREAD_KEY_THRESHOLD) {
            return defaultDate()
        }
        return calculateDateFromEpoch(keyLong)
    }

    /**
     * thread key とレス数から勢いを導出する。
     *
     * @param nowSeconds 時刻基準となる UNIX 秒。省略時は現在時刻。
     */
    fun calculateMomentum(
        threadKey: String,
        resCount: Int,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): Double {
        val keyLong = threadKey.toLongOrNull() ?: return 0.0
        if (keyLong !in 1 until THREAD_KEY_THRESHOLD || resCount <= 0) {
            return 0.0
        }
        val elapsedSeconds = max(1L, nowSeconds - keyLong)
        val elapsedDays = elapsedSeconds / 86400.0
        return if (elapsedDays > 0) resCount / elapsedDays else 0.0
    }

    /**
     * thread key とレス数から、作成日時と勢いをまとめて導出する。
     *
     * @param nowSeconds 時刻基準となる UNIX 秒。省略時は現在時刻。
     */
    fun calculate(
        threadKey: String,
        resCount: Int,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): ThreadDerivedInfo {
        return ThreadDerivedInfo(
            date = calculateDate(threadKey),
            momentum = calculateMomentum(threadKey, resCount, nowSeconds),
        )
    }

    private fun defaultDate(): ThreadDate =
        ThreadDate(0, 0, 0, 0, 0, "")

    private fun calculateDateFromEpoch(epochSeconds: Long): ThreadDate {
        val date = Date(epochSeconds * 1000L)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply { time = date }
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "月"
            Calendar.TUESDAY -> "火"
            Calendar.WEDNESDAY -> "水"
            Calendar.THURSDAY -> "木"
            Calendar.FRIDAY -> "金"
            Calendar.SATURDAY -> "土"
            else -> "日"
        }
        return ThreadDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            dayOfWeek = dayOfWeek,
        )
    }
}

/**
 * スレッドキーから導出した表示用派生情報。
 */
data class ThreadDerivedInfo(
    val date: ThreadDate,
    val momentum: Double,
)
