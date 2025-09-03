package com.websarva.wings.android.slevo.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.websarva.wings.android.slevo.data.datasource.remote.DatRemoteDataSource
import com.websarva.wings.android.slevo.data.util.parseDat
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import com.websarva.wings.android.slevo.ui.util.keyToDatUrl
import com.websarva.wings.android.slevo.ui.util.keyToOysterUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

class DatRepository @Inject constructor(
    private val remoteDataSource: DatRemoteDataSource
) {
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getThread(
        boardUrl: String,
        threadKey: String,
        onProgress: (Float) -> Unit = {}
    ): Pair<List<ReplyInfo>, String?>? = withContext(Dispatchers.Default) {
        val primaryUrl = keyToDatUrl(boardUrl, threadKey)
        var datContent = remoteDataSource.fetchDatString(primaryUrl, onProgress)
        val oysterUrl = if (datContent == null) keyToOysterUrl(boardUrl, threadKey) else null
        if (datContent == null && oysterUrl != null) {
            datContent = remoteDataSource.fetchDatString(oysterUrl, onProgress)
        }
        if (datContent != null) {
            try {
                val (parsedReplies, title) = parseDat(datContent)
                // 勢いを計算する処理を呼び出す
                val repliesWithMomentum = calculateMomentum(parsedReplies)
                Pair(repliesWithMomentum, title)
            } catch (e: Exception) {
                Timber.i("Failed to parse DAT content: ${e.message}")
                null
            }
        } else {
            Timber.i("Failed to fetch DAT content from $primaryUrl and $oysterUrl")
            null
        }
    }

    /**
     * 日付文字列 (例: "2025/07/09(水) 19:40:25.769") をパースする
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDateString(dateStr: String): LocalDateTime? {
        // "(水)" のような曜日部分を削除し、前後の空白も除去
        val cleanDateStr = dateStr.replace(Regex("""\s*\([日月火水木金土]\)\s*"""), " ").trim()
        // 考えられる日付フォーマットのリスト
        val patterns = listOf(
            "yyyy/MM/dd HH:mm:ss.SSS",
            "yyyy/MM/dd HH:mm:ss.SS",
            "yyyy/MM/dd HH:mm:ss.S",
            "yyyy/MM/dd HH:mm:ss"
        )

        // パターンを順番に試す
        for (pattern in patterns) {
            try {
                return LocalDateTime.parse(cleanDateStr, DateTimeFormatter.ofPattern(pattern))
            } catch (e: DateTimeParseException) {
                // パースに失敗した場合は次のパターンを試す
            }
        }
        // すべてのパターンで失敗した場合
        Timber.w("Failed to parse date string: $dateStr")
        return null
    }

    /**
     * 投稿リストを受け取り、各投稿の勢いを計算して返す
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateMomentum(
        replies: List<ReplyInfo>,
        // --- 調整パラメータ ---
        targetCountInWindow: Int = 12,  // 窓内に入れたい目標件数（実況なら12〜16、過疎なら8〜12）
        minWindowMin: Int = 3,          // Wの下限
        maxWindowMin: Int = 20,         // Wの上限
        lowQuantile: Float = 0.10f,     // 正規化の下端（p10）
        highQuantile: Float = 0.95f,    // 正規化の上端（p95）
        useLogCompression: Boolean = true, // 非線形圧縮にlog1pを使う（falseならsqrt）
        emaHalfLifeSec: Int = 0         // 遅れを最小にしたいのでデフォ0（必要なら60〜120）
    ): List<ReplyInfo> {
        if (replies.isEmpty()) return replies

        // 1) epochMillis 化（単調性を保証）
        val zone = java.time.ZoneId.of("Asia/Tokyo")
        val t = LongArray(replies.size)
        var last = 0L
        replies.forEachIndexed { i, r ->
            val ms = parseDateString(r.date)?.atZone(zone)?.toInstant()?.toEpochMilli() ?: last
            t[i] = if (i == 0) ms else maxOf(ms, last + 1) // 逆行補正
            last = t[i]
        }

        // 2) スレ平均密度から可変 W を決める（窓内の期待件数 ≒ targetCountInWindow）
        val spanMs = (t.last() - t.first()).coerceAtLeast(1L)
        val avgRatePerMin = (replies.size - 1).coerceAtLeast(1) * 60_000f / spanMs.toFloat()
        val autoWmin = (targetCountInWindow / avgRatePerMin).let {
            it.coerceIn(
                minWindowMin.toFloat(),
                maxWindowMin.toFloat()
            )
        }.toInt()

        val windowMs = autoWmin * 60_000L

        // 3) 片側（過去W分）の件数を二ポインタで O(n)
        val count = IntArray(t.size)
        var left = 0
        for (i in t.indices) {
            val ti = t[i]
            while (left < t.size && t[left] <= ti - windowMs) left++
            count[i] = i - left + 1
        }

        // 4) 任意の時間ベースEMA（遅れを抑えたいのでデフォ0）
        val series = FloatArray(count.size)
        if (emaHalfLifeSec > 0) {
            val tau = emaHalfLifeSec * 1000.0 / kotlin.math.ln(2.0) // ms
            series[0] = count[0].toFloat()
            for (i in 1 until count.size) {
                val dt = (t[i] - t[i - 1]).coerceAtLeast(1L).toDouble()
                val alpha = (1.0 - kotlin.math.exp(-dt / tau)).toFloat()
                series[i] = series[i - 1] + alpha * (count[i] - series[i - 1])
            }
        } else {
            for (i in count.indices) series[i] = count[i].toFloat()
        }

        // 5) rate[件/分]
        val rate = FloatArray(series.size) { i -> series[i] / autoWmin.toFloat() }

        // 6) 分位スケーリング（スレ内の分布で基準を自動化）
        val tmp = rate.copyOf().sorted()
        fun q(p: Float): Float {
            if (tmp.isEmpty()) return 0f
            val idx = ((tmp.lastIndex) * p).toInt().coerceIn(0, tmp.lastIndex)
            return tmp[idx]
        }

        val qLow = q(lowQuantile)
        val qHigh = maxOf(q(highQuantile), qLow + 1e-3f)

        // 7) 0..1 正規化 + 非線形圧縮
        val norm = FloatArray(rate.size) { i ->
            val x = ((rate[i] - qLow) / (qHigh - qLow)).coerceIn(0f, 1f)
            if (useLogCompression) {
                // log1p で弱い所も残しつつ山の伸び過ぎを抑える
                val y = kotlin.math.ln(1f + 3f * x) / kotlin.math.ln(4f) // 係数3は好みで 2〜4
                y.coerceIn(0f, 1f)
            } else {
                kotlin.math.sqrt(x)
            }
        }

        // 8) momentum 反映
        return List(replies.size) { i -> replies[i].copy(momentum = norm[i]) }
    }
}
