package com.websarva.wings.android.slevo.ui.util

import java.util.LinkedHashMap

/**
 * 共有系アクションで再利用する画像キャッシュ情報を保持するレジストリ。
 *
 * 画像URLと表示成功時に得られた disk cache key を関連付け、共有/外部起動前処理で参照する。
 */
object ImageActionReuseRegistry {
    private const val MAX_ENTRIES = 300
    private const val ENTRY_TTL_MILLIS = 30L * 60L * 1000L

    /**
     * 共有前処理で参照する再利用メタデータ。
     */
    data class ReuseEntry(
        val diskCacheKey: String,
        val recordedAtMillis: Long,
    )

    private val entries = LinkedHashMap<String, ReuseEntry>(MAX_ENTRIES, 0.75f, true)

    /**
     * 画像URLに対する再利用メタデータを登録する。
     */
    @Synchronized
    fun register(url: String, diskCacheKey: String) {
        if (url.isBlank() || diskCacheKey.isBlank()) {
            // Guard: 空URL/空キーは登録対象にしない。
            return
        }
        val now = System.currentTimeMillis()
        entries[url] = ReuseEntry(
            diskCacheKey = diskCacheKey,
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
}
