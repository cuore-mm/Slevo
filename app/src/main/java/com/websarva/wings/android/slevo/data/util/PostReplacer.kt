package com.websarva.wings.android.slevo.data.util

import java.nio.charset.Charset

/**
 * Converts post form fields to a Shift_JIS-safe representation.
 *
 * This replacer keeps encodable text unchanged and converts unsupported
 * grapheme clusters (such as emoji) into numeric character references.
 */
object PostReplacer {
    private val graphemeClusterRegex = Regex("\\X")

    /**
     * Replaces Shift_JIS-unsupported grapheme clusters with numeric character references.
     *
     * For clusters made of multiple code points, each code point is converted to
     * `&#<codePoint>;` in order, so the original sequence is preserved.
     */
    fun replaceEmojisWithNCR(input: String): String {
        val shiftJisEncoder = Charset.forName("Shift_JIS").newEncoder()

        return buildString {
            graphemeClusterRegex.findAll(input).forEach { matchResult ->
                val graphemeCluster = matchResult.value
                if (shiftJisEncoder.canEncode(graphemeCluster)) {
                    append(graphemeCluster)
                    return@forEach
                }

                // Non-encodable clusters are expanded to per-code-point NCR values.
                graphemeCluster.codePoints().forEach { codePoint ->
                    append("&#$codePoint;")
                }
            }
        }
    }
}
