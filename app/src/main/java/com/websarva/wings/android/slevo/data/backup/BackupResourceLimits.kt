package com.websarva.wings.android.slevo.data.backup

/**
 * バックアップ展開resourceのエントリ別・合計・エントリ数の上限ポリシー。
 *
 * restore側の実測展開byte数とexport側の書き込み予定byte数の両方を制限し、
 * ZIP bomb・ストレージ枯渇・ヒープ枯渇から保護する。
 * Hilt へは [BackupModule] の [Provides] で default instance を提供する。
 *
 * @property manifestBytes `manifest.json`の展開後上限（64 KiB）。
 * @property databaseBytes `database/slevo.db`の展開後上限（256 MiB）。
 * @property settingsBytes `datastore/settings.json`の展開後上限（1 MiB）。
 * @property tabsBytes `datastore/tabs.json`の展開後上限（64 KiB）。
 * @property cookiesBytes `datastore/cookies.json`の展開後上限（8 MiB）。
 * @property totalBytes 全エントリ合計の展開後上限（272 MiB）。
 * @property entryCount fileとdirectoryを含むZIPエントリ数の上限（7）。
 */
data class BackupResourceLimits(
    val manifestBytes: Long = DEFAULT_MANIFEST_BYTES,
    val databaseBytes: Long = DEFAULT_DATABASE_BYTES,
    val settingsBytes: Long = DEFAULT_SETTINGS_BYTES,
    val tabsBytes: Long = DEFAULT_TABS_BYTES,
    val cookiesBytes: Long = DEFAULT_COOKIES_BYTES,
    val totalBytes: Long = DEFAULT_TOTAL_BYTES,
    val entryCount: Int = DEFAULT_ENTRY_COUNT,
) {
    /**
     * 既知fileエントリ名に対するエントリ別上限を返す。
     *
     * 戻り値の意味:
     * - 正の値: 対応する上限（バイト）。
     * - `null`: エントリ名が既知でない（呼び出し側は既存path validationへ委譲する）。
     *
     * @param entryName ZIPエントリ名（例: `"manifest.json"`）。
     */
    fun limitForEntry(entryName: String): Long? = when (entryName) {
        "manifest.json" -> manifestBytes
        "database/slevo.db" -> databaseBytes
        "datastore/settings.json" -> settingsBytes
        "datastore/tabs.json" -> tabsBytes
        "datastore/cookies.json" -> cookiesBytes
        else -> null
    }

    companion object {
        /** 1 Kiバイト = 1024 bytes。 */
        private const val KIB = 1024L
        /** 1 Miバイト = 1024×1024 bytes。 */
        private const val MIB = 1024L * 1024L

        const val DEFAULT_MANIFEST_BYTES = 64L * KIB
        const val DEFAULT_DATABASE_BYTES = 256L * MIB
        const val DEFAULT_SETTINGS_BYTES = 1L * MIB
        const val DEFAULT_TABS_BYTES = 64L * KIB
        const val DEFAULT_COOKIES_BYTES = 8L * MIB
        const val DEFAULT_TOTAL_BYTES = 272L * MIB
        const val DEFAULT_ENTRY_COUNT = 7
    }
}

/**
 * バックアップ展開または出力が [BackupResourceLimits] を超過したことを表す例外。
 *
 * @property entryName 超過が検出されたエントリの名前。total超過時は `null`。
 * @property actual 実際のbyte数または検出値。不明時は `-1`。
 * @property limit 超過した上限値。
 * @property target 超過種別（例: `"entry"`、`"total"`、`"entry-count"`）。
 */
class BackupResourceLimitExceededException(
    val entryName: String?,
    val actual: Long,
    val limit: Long,
    val target: String,
    message: String? = null,
) : RuntimeException(
    message ?: buildMessage(entryName, actual, limit, target),
) {
    companion object {
        private fun buildMessage(
            entryName: String?,
            actual: Long,
            limit: Long,
            target: String,
        ): String {
            val prefix = if (entryName != null) "entry $entryName: " else ""
            return "$prefix${target} exceeded: actual=$actual, limit=$limit"
        }
    }
}
