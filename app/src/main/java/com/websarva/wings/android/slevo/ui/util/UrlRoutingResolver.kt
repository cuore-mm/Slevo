package com.websarva.wings.android.slevo.ui.util

import java.net.URI

/**
 * 板/スレ遷移に必要なURL解析結果を表す。
 */
sealed interface ResolvedUrl {
    /**
     * ホストが解決済みの板URLを表す。
     */
    data class Board(
        val rawUrl: String,
        val host: String,
        val boardKey: String
    ) : ResolvedUrl

    /**
     * ホスト解決が必要な itest 板URLを表す。
     */
    data class ItestBoard(
        val rawUrl: String,
        val boardKey: String,
        val host: String? = null
    ) : ResolvedUrl

    /**
     * ホストが解決済みのスレURLを表す。
     */
    data class Thread(
        val rawUrl: String,
        val host: String,
        val boardKey: String,
        val threadKey: String
    ) : ResolvedUrl

    /**
     * 非対応または無効なURLを表す。
     */
    data class Unknown(
        val rawUrl: String,
        val reason: String
    ) : ResolvedUrl
}

/**
 * 5ch系URLを板/スレの遷移対象として解決する。
 */
fun resolveUrl(rawUrl: String): ResolvedUrl {
    // --- Parse URI ---
    val uri = parseUriOrNull(rawUrl) ?: return ResolvedUrl.Unknown(rawUrl, "invalid_uri") // URLが不正な場合は対象外とする。
    val scheme = uri.scheme?.lowercase()
    // http(s)以外のスキームは対象外とする。
    if (scheme != null && scheme != "http" && scheme != "https") {
        // 想定外スキームは早期に除外する。
        return ResolvedUrl.Unknown(rawUrl, "unsupported_scheme")
    }
    val host = uri.host?.lowercase()
        ?: return ResolvedUrl.Unknown(rawUrl, "missing_host") // hostが無いURLは対象外とする。
    val segments = pathSegments(uri)

    // --- itest handling ---
    if (host.startsWith(ITEST_HOST_PREFIX)) {
        if (segments.size >= 2 && segments[0] == "subback") {
            return ResolvedUrl.ItestBoard(
                rawUrl = rawUrl,
                boardKey = segments[1],
                host = null
            )
        }
        if (segments.size >= 5 && segments[1] == "test" && segments[2] == "read.cgi") {
            val server = segments[0]
            val boardKey = segments[3]
            val threadKey = segments[4]
            val threadHost = buildItestThreadHost(host, server)
                ?: return ResolvedUrl.Unknown(rawUrl, "invalid_itest_host") // itestホストの構築に失敗した場合は対象外とする。
            return ResolvedUrl.Thread(
                rawUrl = rawUrl,
                host = threadHost,
                boardKey = boardKey,
                threadKey = threadKey
            )
        }
        // itestの想定パスに一致しない場合は対象外とする。
        return ResolvedUrl.Unknown(rawUrl, "unsupported_itest_path")
    }

    // --- PC handling ---
    if (segments.size >= 4 && segments[0] == "test" && segments[1] == "read.cgi") {
        return ResolvedUrl.Thread(
            rawUrl = rawUrl,
            host = host,
            boardKey = segments[2],
            threadKey = segments[3]
        )
    }
    if (segments.size == 1) {
        return ResolvedUrl.Board(
            rawUrl = rawUrl,
            host = host,
            boardKey = segments[0]
        )
    }
    // 想定パスに一致しない場合は対象外とする。
    return ResolvedUrl.Unknown(rawUrl, "unsupported_path")
}

/**
 * itestスレURLからスレ用ホストを構築する。
 */
private fun buildItestThreadHost(itestHost: String, server: String): String? {
    val suffix = itestHost.removePrefix(ITEST_HOST_PREFIX)
    if (suffix.isBlank()) {
        return null
    }
    return "$server.$suffix"
}

/**
 * URL文字列を安全にURIへ変換する。
 */
private fun parseUriOrNull(url: String): URI? {
    return try {
        URI(url)
    } catch (e: Exception) {
        null
    }
}

/**
 * URIのパスをセグメントに分割して返す。
 */
private fun pathSegments(uri: URI): List<String> {
    val path = uri.path ?: return emptyList()
    return path.split("/").filter { it.isNotEmpty() }
}

private const val ITEST_HOST_PREFIX = "itest."
