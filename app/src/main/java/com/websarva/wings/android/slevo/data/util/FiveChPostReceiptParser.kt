package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.model.PostReceipt
import okhttp3.Headers
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * 5ch互換の投稿成功ヘッダーを解析するparser。
 *
 * ヘッダーは未公開かつ任意であるため、未知の形式や欠落を投稿失敗として扱わず、
 * 解釈できた証拠だけを返す。`X-Regioninfo` は個人情報のため読み取らない。
 */
class FiveChPostReceiptParser @Inject constructor() : PostReceiptParser {
    /** 投稿成功ヘッダーを、照合に利用できる値だけへ変換する。 */
    override fun parse(headers: Headers, expectedPostPlace: String?): PostReceipt {
        val resNum = parsePositiveInt(headers.valueOf("X-Resnum"))
        val postPlace = headers.valueOf("X-Postplace")
        val confirmedResNum = resNum?.takeIf {
            expectedPostPlace == null || postPlace.isNullOrBlank() ||
                normalizePostPlace(postPlace) == normalizePostPlace(expectedPostPlace)
        }
        return PostReceipt(
            confirmedResNum = confirmedResNum,
            serverPostDateMillis = parseUnixSecondsMillis(headers.valueOf("X-Postdate")),
            posterIdHint = headers.valueOf("X-Posterid")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** HTTPヘッダー名を大文字小文字非依存で取得する。 */
    private fun Headers.valueOf(name: String): String? =
        names().firstOrNull { it.equals(name, ignoreCase = true) }?.let { this[it] }

    /** レスポンス番号を正の十進整数として検証する。 */
    private fun parsePositiveInt(value: String?): Int? {
        val normalized = value?.trim() ?: return null
        if (!normalized.matches(DECIMAL_INTEGER_REGEX)) return null
        return normalized.toIntOrNull()?.takeIf { it > 0 }
    }

    /** UNIX秒の十進表記を、浮動小数点の丸めなしでepoch millisへ変換する。 */
    private fun parseUnixSecondsMillis(value: String?): Long? {
        val normalized = value?.trim() ?: return null
        return try {
            val seconds = BigDecimal(normalized)
            if (seconds.signum() < 0) {
                null
            } else {
                seconds
                    .multiply(MILLIS_PER_SECOND)
                    .setScale(0, RoundingMode.DOWN)
                    .longValueExact()
            }
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** 投稿先表記の余分な空白と端のslashを除去して比較する。 */
    private fun normalizePostPlace(value: String?): String? =
        value?.trim()?.trim('/')?.takeIf { it.isNotEmpty() }

    private companion object {
        val DECIMAL_INTEGER_REGEX = Regex("\\d+")
        val MILLIS_PER_SECOND = BigDecimal("1000")
    }
}
