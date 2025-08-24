package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.PostResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object PostParser {
    fun parseWriteResponse(html: String): PostResult {
        val doc = Jsoup.parse(html)
        val title = doc.title()
        val xTag = doc.select("html").outerHtml()
            .substringAfter("", "")
            .trim()

        return when {
            // 成功
            title.contains("書きこみました") || xTag == "true" -> PostResult.Success()
            // 成功（警告あり）
            xTag == "false" -> PostResult.Success()
            // 確認
            title.contains("書き込み確認") || xTag == "cookie" -> {
                val hiddenParams = extractHiddenParams(doc)
                PostResult.Confirm(ConfirmationData(html, hiddenParams))
            }
            // 過負荷
            title.contains("お茶でも") -> PostResult.Error(html, "サーバーが混み合っています。")
            // エラー
            title.contains("ＥＲＲＯＲ") || xTag == "error" -> PostResult.Error(html, "書き込みエラーが発生しました。")
            // 書き込み警告
            xTag == "check" -> PostResult.Error(html, "書き込み警告。")
            // その他
            else -> PostResult.Error(html, "不明なレスポンスです。")
        }
    }

    private fun extractHiddenParams(doc: Document): Map<String, String> {
        return doc.select("form input[type=hidden]")
            .associate { it.attr("name") to it.attr("value") }
            .filterKeys { it.isNotEmpty() }
    }
}
