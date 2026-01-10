package com.websarva.wings.android.slevo.ui.util

import java.net.URI

/**
 * Resolves a Deep Link URL using the common resolver and allowlist.
 */
fun resolveDeepLinkUrl(url: String): ResolvedUrl? {
    // --- Common resolve ---
    val resolved = resolveUrl(url)
    if (resolved is ResolvedUrl.Unknown) {
        // Reject URLs that do not match supported patterns.
        return null
    }

    // --- Host allowlist ---
    val host = when (resolved) {
        is ResolvedUrl.Board -> resolved.host
        is ResolvedUrl.Thread -> resolved.host
        is ResolvedUrl.ItestBoard -> extractHost(url)
        is ResolvedUrl.Unknown -> null
    } ?: return null // Host is required for allowlist check.

    if (!isAllowedDeepLinkHost(host.lowercase())) {
        // Reject disallowed host suffixes.
        return null
    }
    return resolved
}

/**
 * Extracts host from URL string if available.
 */
private fun extractHost(url: String): String? {
    return parseUriOrNull(url)?.host
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
 * Safely parses a URL string into a URI.
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
