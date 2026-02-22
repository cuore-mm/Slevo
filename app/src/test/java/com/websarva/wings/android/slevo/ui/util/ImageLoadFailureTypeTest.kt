package com.websarva.wings.android.slevo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [toImageLoadFailureType] の失敗分類を検証するテスト。
 */
class ImageLoadFailureTypeTest {
    /**
     * 非HTTP例外メッセージに 404 を含んでも UNKNOWN を返すことを確認する。
     */
    @Test
    fun nonHttpExceptionMessageWith404ReturnsUnknown() {
        val throwable = IllegalStateException("failed to decode image at /storage/404/file.jpg")

        val actual = throwable.toImageLoadFailureType()

        assertEquals(ImageLoadFailureType.UNKNOWN, actual)
    }

    /**
     * cause 側メッセージに 410 を含む非HTTP例外でも UNKNOWN を返すことを確認する。
     */
    @Test
    fun nonHttpCauseMessageWith410ReturnsUnknown() {
        val cause = RuntimeException("temporary parser error for id=410")
        val throwable = IllegalArgumentException("top level wrapper", cause)

        val actual = throwable.toImageLoadFailureType()

        assertEquals(ImageLoadFailureType.UNKNOWN, actual)
    }
}
