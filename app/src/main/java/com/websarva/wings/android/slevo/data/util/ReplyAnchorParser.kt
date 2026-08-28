package com.websarva.wings.android.slevo.data.util

/**
 * レス本文に含まれる単一番号形式の返信アンカーを解析するオブジェクト。
 *
 * 既存表示と通知で同じ `>>n` 規則を使い、範囲表記は展開せず数字部分だけを扱う。
 */
object ReplyAnchorParser {
    /** 本文中の返信アンカーを表す正規表現。 */
    const val ANCHOR_PATTERN = ">>(\\d+)"

    private val anchorRegex = Regex(ANCHOR_PATTERN)

    /** 本文中の正のレス番号を出現順かつ重複なしで返す。 */
    fun extractReferencedNumbers(content: String): List<Int> = anchorRegex
        .findAll(content)
        .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
        .filter { number -> number > 0 }
        .distinct()
        .toList()
}
