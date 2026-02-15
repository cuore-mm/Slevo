package com.websarva.wings.android.slevo.ui.util

import android.webkit.MimeTypeMap
import java.util.LinkedHashMap
import java.util.Locale

/**
 * 共有系アクションで再利用する画像キャッシュ情報を保持するレジストリ。
 *
 * 画像URLと表示成功時に得られた disk cache key / 拡張子 / MIME を関連付け、
 * 共有/外部起動前処理で参照する。
 */
object ImageActionReuseRegistry {
    private const val MAX_ENTRIES = 300
    private const val ENTRY_TTL_MILLIS = 30L * 60L * 1000L
    private const val DEFAULT_EXTENSION = "jpg"
    private const val DEFAULT_MIME_TYPE = "image/jpeg"

    /**
     * 共有前処理で参照する再利用メタデータ。
     */
    data class ReuseEntry(
        val diskCacheKey: String,
        val extension: String,
        val mimeType: String,
        val recordedAtMillis: Long,
    )

    private val entries = LinkedHashMap<String, ReuseEntry>(MAX_ENTRIES, 0.75f, true)

    /**
     * 画像URLに対する再利用メタデータを登録する。
     */
    @Synchronized
    fun register(
        url: String,
        diskCacheKey: String,
        extension: String? = null,
        mimeType: String? = null,
    ) {
        if (url.isBlank() || diskCacheKey.isBlank()) {
            // Guard: 空URL/空キーは登録対象にしない。
            return
        }

        // --- Type normalization ---
        val normalizedExtension = normalizeExtension(
            extension = extension,
            mimeType = mimeType,
            url = url,
        )
        val normalizedMimeType = normalizeMimeType(mimeType, normalizedExtension)

        val now = System.currentTimeMillis()
        entries[url] = ReuseEntry(
            diskCacheKey = diskCacheKey,
            extension = normalizedExtension,
            mimeType = normalizedMimeType,
            recordedAtMillis = now,
        )
        prune(now)
    }

    /**
     * 画像URLに紐づく再利用メタデータを取得する。
     */
    @Synchronized
    fun get(url: String): ReuseEntry? {
        if (url.isBlank()) {
            // Guard: 空URLは参照対象にしない。
            return null
        }
        val now = System.currentTimeMillis()
        prune(now)
        return entries[url]
    }

    /**
     * 保持件数とTTLに基づいて不要エントリを削除する。
     */
    private fun prune(nowMillis: Long) {
        // --- TTL cleanup ---
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value.recordedAtMillis > ENTRY_TTL_MILLIS) {
                iterator.remove()
            }
        }

        // --- Size cleanup ---
        while (entries.size > MAX_ENTRIES) {
            val eldestKey = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldestKey)
        }
    }

    /**
     * 再利用メタデータへ保存する拡張子を正規化する。
     */
    private fun normalizeExtension(extension: String?, mimeType: String?, url: String): String {
        val fromArgument = extension
            ?.trim()
            ?.trimStart('.')
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { isImageExtension(it) }
        if (fromArgument != null) {
            return fromArgument
        }

        val fromMime = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.startsWith("image/") }
            ?.substringAfter('/')
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { isImageExtension(it) }
        if (fromMime != null) {
            return fromMime
        }

        val fromUrl = url.substringAfterLast('.', "")
            .trim()
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
            ?.takeIf { isImageExtension(it) }
        return fromUrl ?: DEFAULT_EXTENSION
    }

    /**
     * 再利用メタデータへ保存するMIMEタイプを正規化する。
     */
    private fun normalizeMimeType(mimeType: String?, extension: String): String {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.startsWith("image/") }
        if (normalized != null) {
            return normalized
        }

        val mimeFromExt = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase(Locale.US))
            ?.lowercase(Locale.US)
            ?.takeIf { it.startsWith("image/") }
        return mimeFromExt ?: DEFAULT_MIME_TYPE
    }

    /**
     * 画像拡張子として扱える値かを判定する。
     */
    private fun isImageExtension(extension: String): Boolean {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase(Locale.US))
            ?.lowercase(Locale.US)
        return mime?.startsWith("image/") == true
    }
}
