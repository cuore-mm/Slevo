package com.websarva.wings.android.slevo.ui.util

import java.net.URI

/**
 * Resolved URL information for board or thread navigation.
 */
sealed interface ResolvedUrl {
    /**
     * Represents a board URL with a resolved host.
     */
    data class Board(
        val rawUrl: String,
        val host: String,
        val boardKey: String
    ) : ResolvedUrl

    /**
     * Represents an itest board URL that needs host resolution.
     */
    data class ItestBoard(
        val rawUrl: String,
        val boardKey: String,
        val host: String? = null
    ) : ResolvedUrl

    /**
     * Represents a thread URL with a resolved host.
     */
    data class Thread(
        val rawUrl: String,
        val host: String,
        val boardKey: String,
        val threadKey: String
    ) : ResolvedUrl

    /**
     * Represents an unsupported or invalid URL.
     */
    data class Unknown(
        val rawUrl: String,
        val reason: String
    ) : ResolvedUrl
}

/**
 * Resolves a URL into board/thread navigation targets based on 5ch patterns.
 */
fun resolveUrl(rawUrl: String): ResolvedUrl {
    // --- Parse URI ---
    val uri = parseUriOrNull(rawUrl) ?: return ResolvedUrl.Unknown(rawUrl, "invalid_uri") // Invalid URL.
    val scheme = uri.scheme?.lowercase()
    // Non-http(s) schemes are not supported in this resolver.
    if (scheme != null && scheme != "http" && scheme != "https") {
        // Reject unsupported schemes early.
        return ResolvedUrl.Unknown(rawUrl, "unsupported_scheme")
    }
    val host = uri.host?.lowercase()
        ?: return ResolvedUrl.Unknown(rawUrl, "missing_host") // Host is required.
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
                ?: return ResolvedUrl.Unknown(rawUrl, "invalid_itest_host") // Failed to build thread host.
            return ResolvedUrl.Thread(
                rawUrl = rawUrl,
                host = threadHost,
                boardKey = boardKey,
                threadKey = threadKey
            )
        }
        // Reject unsupported itest paths.
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
    // Reject unsupported paths.
    return ResolvedUrl.Unknown(rawUrl, "unsupported_path")
}

/**
 * Builds a thread host for itest thread URLs.
 */
private fun buildItestThreadHost(itestHost: String, server: String): String? {
    val suffix = itestHost.removePrefix(ITEST_HOST_PREFIX)
    if (suffix.isBlank()) {
        return null
    }
    return "$server.$suffix"
}

/**
 * Safely parses a URL string into a URI.
 */
private fun parseUriOrNull(url: String): URI? {
    return try {
        URI(url)
    } catch (e: Exception) {
        null
    }
}

/**
 * Splits a URI path into non-empty segments.
 */
private fun pathSegments(uri: URI): List<String> {
    val path = uri.path ?: return emptyList()
    return path.split("/").filter { it.isNotEmpty() }
}

private const val ITEST_HOST_PREFIX = "itest."
