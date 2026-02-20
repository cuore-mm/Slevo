package com.websarva.wings.android.slevo.ui.util

/**
 * 画像読み込み失敗の分類を表す。
 *
 * 表示UIで再試行導線と説明文を分岐するために利用する。
 */
enum class ImageLoadFailureType {
    UNKNOWN,
    HTTP_404,
    HTTP_410,
}

/**
 * 例外情報から画像読み込み失敗種別を判定する。
 */
fun Throwable?.toImageLoadFailureType(): ImageLoadFailureType {
    val statusCode = this.findHttpStatusCodeInChain() ?: return ImageLoadFailureType.UNKNOWN
    return when (statusCode) {
        404 -> ImageLoadFailureType.HTTP_404
        410 -> ImageLoadFailureType.HTTP_410
        else -> ImageLoadFailureType.UNKNOWN
    }
}

/**
 * 例外チェーン内のメッセージからHTTPステータスコードを抽出する。
 */
private fun Throwable?.findHttpStatusCodeInChain(): Int? {
    var current = this
    var depth = 0
    while (current != null && depth < 6) {
        val code = STATUS_CODE_REGEX.find(current.message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (code != null) {
            return code
        }
        current = current.cause
        depth += 1
    }
    return null
}

private val STATUS_CODE_REGEX = Regex("\\b(404|410)\\b")
