package com.websarva.wings.android.slevo.ui.util

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * itest URLから抽出した板/スレ情報を保持する。
 *
 * threadKey が null の場合は板URLとして扱う。
 */
data class ItestUrlInfo(
    val boardKey: String,
    val threadKey: String?
)

fun keyToDatUrl(boardUrl:String, key: String): String {
    return "${boardUrl}dat/${key}.dat"
}

fun keyToOysterUrl(boardUrl: String, key: String): String {
    val prefix = if (key.length >= 4) key.take(4) else key
    return "${boardUrl}oyster/$prefix/${key}.dat"
}

/**
 * 5ch系の掲示板URL（例: https://agree.5ch.net/operate/）から
 * host と boardName を取り出す。
 *
 * @param url 抽出対象の URL
 * @return Pair(host, boardName) または null（URL が不正な場合）
 */
fun parseBoardUrl(url: String): Pair<String, String>? {
    val uri = parseUriOrNull(url) ?: return null
    val host = uri.host ?: return null
    // PathSegments: ["operate"] のように、最初の要素が板名
    val segments = pathSegments(uri)
    if (segments.isEmpty()) return null
    val boardKey = segments[0]
    return host to boardKey
}

/**
 * URL からサービス名（ドメイン部分）を抽出します。
 * 例: https://agree.5ch.net/operate/ -> 5ch.net
 */
fun parseServiceName(url: String): String {
    return try {
        val host = parseUriOrNull(url)?.host ?: return ""
        val parts = host.split(".")
        if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    } catch (e: Exception) {
        ""
    }
}

/**
 * スレッドURLから host, boardKey, threadKey を抽出する。
 * 対応形式: https://host/test/read.cgi/board/1234567890/
 *            https://host/board/dat/1234567890.dat
 */
fun parseThreadUrl(url: String): Triple<String, String, String>? {
    val uri = parseUriOrNull(url) ?: return null
    val host = uri.host ?: return null
    val segments = pathSegments(uri)

    if (segments.size >= 4 && segments[0] == "test" && segments[1] == "read.cgi") {
        val boardKey = segments[2]
        val threadKey = segments[3].substringBefore('/')
        return Triple(host, boardKey, threadKey)
    }

    if (segments.size >= 3 && segments[1] == "dat") {
        val boardKey = segments[0]
        val threadKey = segments[2].removeSuffix(".dat")
        return Triple(host, boardKey, threadKey)
    }

    return null
}

/**
 * itest.5ch.net のURLから板/スレ情報を抽出する。
 *
 * スレURLの場合は threadKey を含めて返し、板URLの場合は threadKey を null とする。
 */
fun parseItestUrl(url: String): ItestUrlInfo? {
    val uri = parseUriOrNull(url) ?: return null
    val host = uri.host ?: return null
    val allowedHosts = setOf("itest.5ch.net", "itest.bbspink.com")
    if (host !in allowedHosts) return null
    val segments = pathSegments(uri)
    if (segments.isEmpty()) return null

    val readIndex = segments.indexOf("read.cgi")
    if (readIndex >= 0 && readIndex + 2 < segments.size) {
        val boardKey = segments[readIndex + 1]
        val threadKey = segments[readIndex + 2]
        return ItestUrlInfo(boardKey = boardKey, threadKey = threadKey)
    }

    // subback/{boardKey} の形式は2番目のセグメントを板キーとして扱う。
    if (segments[0] == "subback" && segments.size >= 2) {
        return ItestUrlInfo(boardKey = segments[1], threadKey = null)
    }

    return ItestUrlInfo(boardKey = segments[0], threadKey = null)
}

/**
 * URL文字列をURIとして安全に解析する。
 */
private fun parseUriOrNull(url: String): URI? {
    return try {
        URI(url)
    } catch (e: Exception) {
        null
    }
}

/**
 * URIのパスをセグメント単位で取得する。
 */
private fun pathSegments(uri: URI): List<String> {
    val path = uri.path ?: return emptyList()
    return path.split("/").filter { it.isNotEmpty() }
}

/**
 * 画像URLをGoogle Lensの検索URLへ変換する。
 *
 * @param imageUrl 元の画像URL
 * @return `lens.google.com/uploadbyurl` 形式の検索URL
 */
fun buildLensSearchUrl(imageUrl: String): String {
    val encoded = URLEncoder.encode(imageUrl, StandardCharsets.UTF_8.toString())
    return "https://lens.google.com/uploadbyurl?url=$encoded"
}
