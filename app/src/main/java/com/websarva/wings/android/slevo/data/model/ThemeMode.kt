package com.websarva.wings.android.slevo.data.model

/**
 * アプリ全体の配色モードを表す設定値。
 *
 * `LIGHT` / `DARK` は明示テーマ、`SYSTEM` はOS設定へ追従する。
 */
enum class ThemeMode(val storageValue: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    /**
     * 現在のシステムダークテーマ状態を加味して実際に適用する dark 値を返す。
     */
    fun resolveDarkTheme(isSystemDark: Boolean): Boolean {
        return when (this) {
            LIGHT -> false
            DARK -> true
            SYSTEM -> isSystemDark
        }
    }

    companion object {
        /**
         * 永続化文字列から [ThemeMode] へ変換する。
         * 不正値または未設定値は `SYSTEM` にフォールバックする。
         */
        fun fromStorageValue(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}
