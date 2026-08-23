package com.websarva.wings.android.slevo.data.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 自分の投稿照合に使うサーバー時刻とdat日時の変換を担当するparser。
 *
 * datの日時は5chの表示規約に合わせてAsia/Tokyoで解釈し、失敗時に現在時刻へfallback
 * しない。ミリ秒より細かい小数はepoch millisへ変換する際に切り捨てる。
 */
object OwnPostDateParser {
    /** 投稿成功時刻とdat日時の照合許容差。 */
    const val MATCH_TOLERANCE_MILLIS = 1_000L

    private val datDatePattern =
        Regex("""^(\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?$""")

    /** datの投稿日時をAsia/Tokyoのepoch millisへ変換する。 */
    fun parseDatDate(value: String): Long? {
        val withoutWeekday = value.trim()
            .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
            .trim()
        val match = datDatePattern.matchEntire(withoutWeekday) ?: return null
        val baseMillis = parseBaseDate(match.groupValues[1]) ?: return null
        val fractionMillis = try {
            match.groupValues[2].takeIf { it.isNotEmpty() }?.let { fraction ->
                BigDecimal("0.$fraction")
                    .multiply(MILLIS_PER_SECOND)
                    .setScale(0, RoundingMode.DOWN)
                    .longValueExact()
            } ?: 0L
        } catch (_: ArithmeticException) {
            return null
        }
        return try {
            Math.addExact(baseMillis, fractionMillis)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** 2つのepoch millisが照合許容差内にあるか判定する。 */
    fun isWithinTolerance(actualMillis: Long, expectedMillis: Long): Boolean =
        try {
            Math.abs(Math.subtractExact(actualMillis, expectedMillis)) <= MATCH_TOLERANCE_MILLIS
        } catch (_: ArithmeticException) {
            false
        }

    /** JST固定・非寛容な日時parserで秒単位の日時を変換する。 */
    private fun parseBaseDate(value: String): Long? {
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
            timeZone = TOKYO_TIME_ZONE
            isLenient = false
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null
        // 入力全体の形式は呼び出し元の正規表現で検証済みのため、実装差のある終端index判定に依存しない。
        return parsed.time
    }

    private val TOKYO_TIME_ZONE = TimeZone.getTimeZone("Asia/Tokyo")
    private val MILLIS_PER_SECOND = BigDecimal("1000")
}
