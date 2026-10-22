package com.websarva.wings.android.slevo.data.util

/**
 * 本文中の単一番号・範囲・カンマ区切り返信アンカーを解析するオブジェクト。
 * 解析結果は元本文の位置と番号範囲を保持し、参照先の展開時には取得済みレス数で上限を設ける。
 */
object ReplyAnchorParser {
    /** 単一番号・範囲・カンマ区切りを含む返信アンカー本文の正規表現。 */
    const val ANCHOR_PATTERN = ">>(\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*)(?![-,])"

    private val anchorRegex = Regex(ANCHOR_PATTERN)

    /** 一つの安価を構成する正のレス番号区間を表す。 */
    data class NumberRange(
        val first: Int,
        val last: Int,
    )

    /** 元本文内の位置と解釈済み番号区間を保持する安価tokenを表す。 */
    data class AnchorToken(
        val start: Int,
        val end: Int,
        val rawText: String,
        val ranges: List<NumberRange>,
    )

    /** 取得済み番号の列と、未取得番号を列挙せず保持する範囲を表す。 */
    data class ReplyTargetReferences(
        val numbers: List<Int>,
        val unavailableRanges: List<NumberRange>,
    )

    /** 本文から有効な安価tokenを出現順で返し、不完全な区切りを含むtokenは無視する。 */
    fun parseAnchors(content: String): List<AnchorToken> = anchorRegex.findAll(content).mapNotNull { match ->
        val rangeParts = match.groupValues[1].split(',')
        val ranges = rangeParts.mapNotNull(::parseRange)
        if (ranges.size != rangeParts.size) {
            null
        } else {
            AnchorToken(
                start = match.range.first,
                end = match.range.last + 1,
                rawText = match.value,
                ranges = ranges,
            )
        }
    }.toList()

    /** 指定された取得済み上限までに含まれる番号を、出現順・重複なしで展開して返す。 */
    fun extractReferencedNumbers(content: String, maxPostNumber: Int): List<Int> {
        if (maxPostNumber <= 0) return emptyList()

        return parseAnchors(content)
            .flatMap { token -> token.ranges.flatMap { it.numbersWithin(maxPostNumber) } }
            .distinct()
    }

    /** 取得済み範囲だけを展開し、取得済み上限を超える部分は統合範囲として返す。 */
    fun extractReplyTargetReferences(
        content: String,
        maxPostNumber: Int,
    ): ReplyTargetReferences {
        val numbers = LinkedHashSet<Int>()
        val unavailableRanges = mutableListOf<NumberRange>()
        val availableLimit = maxPostNumber.coerceAtLeast(0)

        // --- 取得済みレスとの共通部分だけを展開 ---
        for (token in parseAnchors(content)) {
            for (range in token.ranges) {
                val availableEnd = range.last.coerceAtMost(availableLimit)
                if (range.first <= availableEnd) {
                    for (number in range.first..availableEnd) {
                        numbers += number
                    }
                }

                // --- 未取得部分は範囲のまま保持 ---
                val unavailableStart = maxOf(range.first.toLong(), availableLimit.toLong() + 1L)
                if (unavailableStart <= range.last.toLong()) {
                    unavailableRanges += NumberRange(
                        first = unavailableStart.toInt(),
                        last = range.last,
                    )
                }
            }
        }

        // --- 未取得区間の重複と隣接を統合 ---
        return ReplyTargetReferences(
            numbers = numbers.toList(),
            unavailableRanges = mergeRanges(unavailableRanges),
        )
    }

    /** 重複または隣接する区間を、番号を列挙せず昇順で統合する。 */
    private fun mergeRanges(ranges: List<NumberRange>): List<NumberRange> {
        if (ranges.isEmpty()) return emptyList()
        val sortedRanges = ranges.sortedBy(NumberRange::first)
        val merged = mutableListOf<NumberRange>()
        var first = sortedRanges.first().first
        var last = sortedRanges.first().last
        sortedRanges.drop(1).forEach { range ->
            if (range.first.toLong() <= last.toLong() + 1L) {
                last = maxOf(last, range.last)
            } else {
                merged += NumberRange(first, last)
                first = range.first
                last = range.last
            }
        }
        merged += NumberRange(first, last)
        return merged
    }

    /** 一つの数値項目または範囲項目を解析する。 */
    private fun parseRange(part: String): NumberRange? {
        val numbers = part.split('-', limit = 2)
        val first = numbers[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val last = if (numbers.size == 1) {
            first
        } else {
            numbers[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        }
        if (last < first) return null
        return NumberRange(first = first, last = last)
    }

    /** 範囲と取得済みレス番号の共通部分だけを列挙する。 */
    private fun NumberRange.numbersWithin(maxPostNumber: Int): List<Int> {
        val firstAvailable = first.coerceAtLeast(1)
        val lastAvailable = last.coerceAtMost(maxPostNumber)
        if (firstAvailable > lastAvailable) return emptyList()
        return (firstAvailable..lastAvailable).toList()
    }
}
