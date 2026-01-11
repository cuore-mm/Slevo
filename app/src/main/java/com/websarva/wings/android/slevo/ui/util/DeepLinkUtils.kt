package com.websarva.wings.android.slevo.ui.util

import java.net.URI

/**
 * Deep Link のURLを共通リゾルバと許可ドメインで解決する。
 */
fun resolveDeepLinkUrl(url: String): ResolvedUrl? {
    // --- Common resolve ---
    val resolved = resolveUrl(url)
    if (resolved is ResolvedUrl.Unknown) {
        // 対応パターンに一致しないURLは対象外とする。
        return null
    }

    // --- Original host check ---
    val originalHost = extractHost(url)?.lowercase() ?: return null // 判定には入力ホストが必要。

    // --- Host allowlist ---
    val host = when (resolved) {
        is ResolvedUrl.Board -> resolved.host
        is ResolvedUrl.Thread -> resolved.host
        is ResolvedUrl.ItestBoard -> originalHost
        is ResolvedUrl.Unknown -> null
    } ?: return null // 許可判定にはhostが必要。

    if (!isAllowedDeepLinkHost(host.lowercase())) {
        // 許可サフィックス外は対象外とする。
        return null
    }

    // --- itest domain restriction ---
    if (originalHost.startsWith(ITEST_HOST_PREFIX) && !isAllowedItestHost(originalHost)) {
        // itest対応ドメイン外は対象外とする。
        return null
    }
    return resolved
}

/**
 * URL文字列からhostを抽出する。
 */
private fun extractHost(url: String): String? {
    return parseUriOrNull(url)?.host
}

/**
 * Deep Link許可ドメインかを判定する。
 */
private fun isAllowedDeepLinkHost(host: String): Boolean {
    return ALLOWED_HOST_SUFFIXES.any { suffix ->
        host == suffix || host.endsWith(".$suffix")
    }
}

/**
 * itestで許可されるホストかを判定する。
 */
private fun isAllowedItestHost(host: String): Boolean {
    return ALLOWED_ITEST_SUFFIXES.any { suffix ->
        host == "$ITEST_HOST_PREFIX$suffix"
    }
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

private val ALLOWED_HOST_SUFFIXES = listOf(
    "bbspink.com",
    "5ch.net",
    "2ch.sc"
)

private val ALLOWED_ITEST_SUFFIXES = listOf(
    "bbspink.com",
    "5ch.net"
)

private const val ITEST_HOST_PREFIX = "itest."
