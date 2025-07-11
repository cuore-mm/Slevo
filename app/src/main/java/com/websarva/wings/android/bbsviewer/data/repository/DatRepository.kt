package com.websarva.wings.android.bbsviewer.data.repository

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.data.datasource.remote.DatRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.util.parseDat
import com.websarva.wings.android.bbsviewer.ui.thread.ReplyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class DatRepository @Inject constructor(
    private val remoteDataSource: DatRemoteDataSource
) {
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getThread(
        datUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Pair<List<ReplyInfo>, String?>? = withContext(Dispatchers.Default) {
        val datContent = remoteDataSource.fetchDatString(datUrl, onProgress)
        if (datContent != null) {
            try {
                val (parsedReplies, title) = parseDat(datContent)
                // 勢いを計算する処理を呼び出す
                val repliesWithMomentum = calculateMomentum(parsedReplies)
                Pair(repliesWithMomentum, title)
            } catch (e: Exception) {
                Log.i("DatRepository", "Failed to parse DAT content: ${e.message}")
                null
            }
        } else {
            Log.i("DatRepository", "Failed to fetch DAT content from $datUrl")
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
        Log.w("DatRepository", "Failed to parse date string: $dateStr")
        return null
    }

    /**
     * 投稿リストを受け取り、各投稿の勢いを計算して返す
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculateMomentum(replies: List<ReplyInfo>): List<ReplyInfo> {
        if (replies.size < 2) return replies

        val repliesWithMomentum = mutableListOf<ReplyInfo>()
        // 最初の投稿は比較対象がないため勢い0
        repliesWithMomentum.add(replies.first().copy(momentum = 0.0f))

        for (i in 1 until replies.size) {
            val previousReply = replies[i - 1]
            val currentReply = replies[i]

            val previousTime = parseDateString(previousReply.date)
            val currentTime = parseDateString(currentReply.date)
            var momentum = 0.0f

            if (previousTime != null && currentTime != null) {
                val diffSeconds = ChronoUnit.SECONDS.between(previousTime, currentTime)

                // 勢いの計算ロジック
                val maxMomentumDuration = 1L  // 1秒以内なら勢い最大
                val minMomentumDuration = 30L // 30秒以上なら勢いゼロ

                if (diffSeconds <= maxMomentumDuration) {
                    momentum = 1.0f
                } else if (diffSeconds < minMomentumDuration) {
                    // 非線形に減少させる
                    val normalizedDiff = (diffSeconds - maxMomentumDuration).toFloat() / (minMomentumDuration - maxMomentumDuration)
                    val remainingMomentum = 1.0f - normalizedDiff
                    momentum = remainingMomentum * remainingMomentum // 2乗して急なカーブに
                }
            }
            repliesWithMomentum.add(currentReply.copy(momentum = momentum))
        }
        return repliesWithMomentum
    }
}
