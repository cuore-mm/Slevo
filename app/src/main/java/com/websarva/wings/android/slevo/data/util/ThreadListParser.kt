package com.websarva.wings.android.slevo.data.util

import androidx.core.text.HtmlCompat
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadInfo

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

                    val derived = ThreadInfoDerivedCalculator.calculate(
                        threadKey = key,
                        resCount = resCount,
                        nowSeconds = currentUnixTime,
                    )

                    threads.add(
                        ThreadInfo(
                            title = title,
                            key = key,
                            resCount = resCount,
                            date = derived.date,
                            momentum = derived.momentum
                        )
                    )
                }
            }
        }
        return threads
    }

    /**
     * スレッドキーからスレ作成日を計算する。
     *
     * 既存コードからの呼び出し互換のため残す。内部では
     * [ThreadInfoDerivedCalculator.calculateDate] に委譲する。
     */
    fun calculateThreadDate(threadKey: String): ThreadDate {
        return ThreadInfoDerivedCalculator.calculateDate(threadKey)
    }
}
