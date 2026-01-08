package com.websarva.wings.android.slevo.ui.util

import androidx.core.net.toUri

/**
 * Represents a parsed Deep Link target.
 */
sealed interface DeepLinkTarget {
    /**
     * Represents a board/thread extracted from itest URLs.
     */
    data class Itest(
        val boardKey: String,
        val threadKey: String?
    ) : DeepLinkTarget

    /**
     * Represents a thread target extracted from a thread URL.
     */
    data class Thread(
        val host: String,
        val boardKey: String,
        val threadKey: String
    ) : DeepLinkTarget

    /**
     * Represents a board target extracted from a board URL.
     */
    data class Board(
        val host: String,
        val boardKey: String
    ) : DeepLinkTarget
}

/**
 * Normalizes a Deep Link URL to https.
 */
fun normalizeDeepLinkUrl(url: String): String {
    val uri = url.toUri()
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http") {
        return url
    }
    return uri.buildUpon().scheme("https").build().toString()
}

/**
 * Extracts a board or thread target from a Deep Link URL.
 */
fun parseDeepLinkTarget(url: String): DeepLinkTarget? {
    val uri = url.toUri()
    val host = uri.host ?: return null // Skip when host is missing.
    val normalizedHost = host.lowercase()
    if (!isAllowedDeepLinkHost(normalizedHost)) {
        return null // Ignore unsupported domains.
    }
    if (isDatThreadUrl(uri)) {
        return null // dat URLs are not supported as Deep Links.
    }

    parseItestUrl(url)?.let { info ->
        return DeepLinkTarget.Itest(
            boardKey = info.boardKey,
            threadKey = info.threadKey
        )
    }

    parseThreadUrl(url)?.let { (threadHost, boardKey, threadKey) ->
        return DeepLinkTarget.Thread(
            host = threadHost,
            boardKey = boardKey,
            threadKey = threadKey
        )
    }

    parseBoardUrl(url)?.let { (boardHost, boardKey) ->
        return DeepLinkTarget.Board(
            host = boardHost,
            boardKey = boardKey
        )
    }

    return null
}

/**
 * Checks if the host is allowed for Deep Links.
 */
private fun isAllowedDeepLinkHost(host: String): Boolean {
    return ALLOWED_HOST_SUFFIXES.any { suffix ->
        host == suffix || host.endsWith(".$suffix")
    }
}

/**
 * Checks whether the URL uses the dat thread format.
 */
private fun isDatThreadUrl(uri: android.net.Uri): Boolean {
    val segments = uri.pathSegments.filter { it.isNotEmpty() }
    return segments.size >= 3 && segments[1] == "dat" && segments.last().endsWith(".dat")
}

private val ALLOWED_HOST_SUFFIXES = listOf(
    "bbspink.com",
    "5ch.net",
    "2ch.sc"
)
