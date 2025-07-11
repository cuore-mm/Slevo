package com.websarva.wings.android.bbsviewer.ui.util

import androidx.core.net.toUri

fun keyToDatUrl(boardUrl:String, key: String): String {
    return "${boardUrl}dat/${key}.dat"
}

fun keyToOysterUrl(boardUrl: String, key: String): String {
    val prefix = if (key.length >= 4) key.substring(0, 4) else key
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
    val uri = url.toUri()
    val host = uri.host ?: return null
    // PathSegments: ["operate"] のように、最初の要素が板名
    val segments = uri.pathSegments
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
        val host = url.toUri().host ?: return ""
        val parts = host.split(".")
        if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    } catch (e: Exception) {
        ""
    }
}
