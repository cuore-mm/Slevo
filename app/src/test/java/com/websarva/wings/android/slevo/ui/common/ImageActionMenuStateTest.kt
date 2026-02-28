package com.websarva.wings.android.slevo.ui.common

import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveImageActionMenuState] の表示グループ判定を検証するテスト。
 */
class ImageActionMenuStateTest {
    /**
     * 読み込み中URLは失敗情報より優先してLOADINGとなることを確認する。
     */
    @Test
    fun resolveImageActionMenuState_prioritizesLoadingOverFailure() {
        val url = "https://example.com/image.jpg"
        val state = resolveImageActionMenuState(
            imageUrl = url,
            imageUrls = listOf(url, "https://example.com/image2.jpg"),
            imageLoadFailureByUrl = mapOf(url to ImageLoadFailureType.HTTP_404),
            loadingImageUrls = setOf(url),
        )

        assertEquals(ImageActionMenuGroup.LOADING, state.group)
        assertEquals(2, state.saveAllImageCount)
    }

    /**
     * 404失敗時は FAIL_404_410 に分類されることを確認する。
     */
    @Test
    fun resolveImageActionMenuState_returnsFail404410ForHttp404() {
        val url = "https://example.com/image.jpg"
        val state = resolveImageActionMenuState(
            imageUrl = url,
            imageUrls = listOf(url),
            imageLoadFailureByUrl = mapOf(url to ImageLoadFailureType.HTTP_404),
            loadingImageUrls = emptySet(),
        )

        assertEquals(ImageActionMenuGroup.FAIL_404_410, state.group)
    }

    /**
     * 空URLは成功扱いで既存メニューを維持することを確認する。
     */
    @Test
    fun resolveImageActionMenuState_returnsSuccessForBlankUrl() {
        val state = resolveImageActionMenuState(
            imageUrl = "",
            imageUrls = listOf("https://example.com/image.jpg"),
            imageLoadFailureByUrl = emptyMap(),
            loadingImageUrls = emptySet(),
        )

        assertEquals(ImageActionMenuGroup.SUCCESS, state.group)
    }

    /**
     * 失敗も読み込み中もない場合は成功扱いになることを確認する。
     */
    @Test
    fun resolveImageActionMenuState_returnsSuccessWhenNoFailure() {
        val url = "https://example.com/image.jpg"
        val state = resolveImageActionMenuState(
            imageUrl = url,
            imageUrls = listOf(url),
            imageLoadFailureByUrl = emptyMap(),
            loadingImageUrls = emptySet(),
        )

        assertEquals(ImageActionMenuGroup.SUCCESS, state.group)
    }
}
