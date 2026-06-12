package com.websarva.wings.android.slevo.ui.util

/**
 * 検索対象文字列とクエリの一致判定を共通化するユーティリティ。
 *
 * 既存のスレ内検索・板内検索と同じく、かな表記差分は `toHiragana()` で吸収し、
 * 大文字小文字は `ignoreCase = true` で無視して部分一致を判定する。
 */
fun matchesSearchQuery(content: String, query: String): Boolean {
    // 空クエリは全件一致として扱い、呼び出し側の分岐を単純化する。
    if (query.isBlank()) return true
    return content.toHiragana().contains(query.toHiragana(), ignoreCase = true)
}
