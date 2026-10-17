package com.websarva.wings.android.slevo.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 画像ビューア route が全画像・初期index・表示文脈を保持することを検証する。 */
class ImageViewerRouteFactoryTest {
    /** 先頭サムネイルから開いても、元レスの全画像を同じ namespace で渡す。 */
    @Test
    fun buildImageViewerRouteKeepsAllImagesAndTransitionNamespace() {
        val urls = listOf(
            "https://example.com/first.jpg",
            "https://example.com/second.png",
            "https://example.com/third.gif",
        )

        val route = buildImageViewerRoute(
            imageUrls = urls,
            tappedIndex = 0,
            transitionNamespace = "thread-post-20-reply-target-reply-12",
        )

        assertEquals(
            AppRoute.ImageViewer(
                imageUrls = urls.map { java.net.URLEncoder.encode(it, "UTF-8") },
                initialIndex = 0,
                transitionNamespace = "thread-post-20-reply-target-reply-12",
            ),
            route,
        )
    }

    /** URL一覧が空ならビューア遷移を作らない。 */
    @Test
    fun buildImageViewerRouteReturnsNullForNoImages() {
        assertNull(buildImageViewerRoute(emptyList(), tappedIndex = 0, transitionNamespace = "preview"))
    }
}
