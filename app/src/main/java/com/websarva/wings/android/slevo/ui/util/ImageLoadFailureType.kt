package com.websarva.wings.android.slevo.ui.util

import coil3.network.HttpException

/**
 * 画像読み込み失敗の分類を表す。
 *
 * 読み込み中止などのUI分岐や再試行導線制御に利用する。
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
 * 例外チェーン内の型付きHTTP例外からステータスコードを抽出する。
 */
private fun Throwable?.findHttpStatusCodeInChain(): Int? {
    var current = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
        // Guard: HTTP例外として扱える型のみを分類対象にする。
        if (current is HttpException) {
            return current.response.code
        }
        current = current.cause
        depth += 1
    }
    return null
}

private const val MAX_CAUSE_CHAIN_DEPTH = 6
