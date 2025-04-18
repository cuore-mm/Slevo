package com.websarva.wings.android.bbsviewer.data.util

import org.jsoup.Jsoup

object PostParser {
    data class HiddenParam(val key: String, val value: String)

    fun extractHiddenParams(html: String): List<HiddenParam> {
        val doc = Jsoup.parse(html)
        return doc.select("form input[type=hidden]")
            .mapNotNull {
                val name = it.attr("name")
                val value = it.attr("value")
                if (name.isNotEmpty()) HiddenParam(name, value) else null
            }
    }

    fun extractCookies(headers: List<String>): List<String> {
        return headers
            .filter { it.startsWith("Set-Cookie:", ignoreCase = true) }
            .map { it.substringAfter("Set-Cookie:").substringBefore(";").trim() }
    }

    fun isSuccess(html: String): Boolean {
        return "書きこみました。" in html || "書きこみが終わりました。" in html
    }
}
