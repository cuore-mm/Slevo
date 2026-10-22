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

    /** 展開上限までに得た一意の番号と、上限を超える候補があるかを表す。 */
    data class LimitedReferencedNumbers(
        val numbers: List<Int>,
        val hasMore: Boolean,
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

    /** 取得済み状態に関係なく安価番号を出現順に重複除去し、正の上限まで展開する。 */
    fun extractReferencedNumbersWithLimit(
        content: String,
        limit: Int,
    ): LimitedReferencedNumbers {
        require(limit > 0) { "limit must be positive" }

        val numbers = LinkedHashSet<Int>()
        for (token in parseAnchors(content)) {
            for (range in token.ranges) {
                var number = range.first.toLong()
                val last = range.last.toLong()
                while (number <= last) {
                    val candidate = number.toInt()
                    if (candidate !in numbers) {
                        if (numbers.size == limit) {
                            return LimitedReferencedNumbers(numbers.toList(), hasMore = true)
                        }
                        numbers += candidate
                    }
                    number++
                }
            }
        }
        return LimitedReferencedNumbers(numbers.toList(), hasMore = false)
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
