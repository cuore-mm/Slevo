package com.websarva.wings.android.slevo.ui.viewer

import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ImageViewerViewModel] の画像読み込み失敗状態管理を検証するテスト。
 */
class ImageViewerViewModelTest {
    /**
     * サムネイル成功が本体失敗状態を解除しないことを確認する。
     */
    @Test
    fun thumbnailSuccessDoesNotClearViewerFailure() {
        val viewModel = ImageViewerViewModel()
        val url = "https://example.com/image.jpg"

        viewModel.onViewerImageLoadError(url, ImageLoadFailureType.UNKNOWN)
        viewModel.onThumbnailImageLoadSuccess(url)

        val state = viewModel.uiState.value
        assertEquals(ImageLoadFailureType.UNKNOWN, state.viewerImageLoadFailureByUrl[url])
        assertTrue(url !in state.thumbnailImageLoadFailureByUrl)
    }

    /**
     * URL同期時に本体とサムネイルの失敗状態が同時に整合されることを確認する。
     */
    @Test
    fun synchronizeFailedImageUrlsFiltersBothFailureMaps() {
        val viewModel = ImageViewerViewModel()
        val activeUrl = "https://example.com/active.jpg"
        val staleUrl = "https://example.com/stale.jpg"

        viewModel.onViewerImageLoadError(activeUrl, ImageLoadFailureType.UNKNOWN)
        viewModel.onViewerImageLoadError(staleUrl, ImageLoadFailureType.UNKNOWN)
        viewModel.onThumbnailImageLoadError(activeUrl, ImageLoadFailureType.HTTP_404)
        viewModel.onThumbnailImageLoadError(staleUrl, ImageLoadFailureType.HTTP_410)

        viewModel.synchronizeFailedImageUrls(listOf(activeUrl))

        val state = viewModel.uiState.value
        assertTrue(activeUrl in state.viewerImageLoadFailureByUrl)
        assertTrue(activeUrl in state.thumbnailImageLoadFailureByUrl)
        assertTrue(staleUrl !in state.viewerImageLoadFailureByUrl)
        assertTrue(staleUrl !in state.thumbnailImageLoadFailureByUrl)
    }

    /**
     * 本体読み込み開始中URLが同期処理で表示対象URLへ絞り込まれることを確認する。
     */
    @Test
    fun synchronizeFailedImageUrlsFiltersViewerLoadingUrls() {
        val viewModel = ImageViewerViewModel()
        val activeUrl = "https://example.com/active.jpg"
        val staleUrl = "https://example.com/stale.jpg"

        viewModel.onViewerImageLoadStart(activeUrl)
        viewModel.onViewerImageLoadStart(staleUrl)

        viewModel.synchronizeFailedImageUrls(listOf(activeUrl))

        val state = viewModel.uiState.value
        assertTrue(activeUrl in state.viewerImageLoadingUrls)
        assertTrue(staleUrl !in state.viewerImageLoadingUrls)
    }

    /**
     * 本体再試行で本体失敗とサムネイル失敗が同時に解除されることを確認する。
     */
    @Test
    fun viewerRetryClearsViewerAndThumbnailFailures() {
        val viewModel = ImageViewerViewModel()
        val url = "https://example.com/image.jpg"

        viewModel.onViewerImageLoadError(url, ImageLoadFailureType.UNKNOWN)
        viewModel.onThumbnailImageLoadError(url, ImageLoadFailureType.UNKNOWN)

        viewModel.onViewerImageRetry(url)

        val state = viewModel.uiState.value
        assertTrue(url !in state.viewerImageLoadFailureByUrl)
        assertTrue(url !in state.thumbnailImageLoadFailureByUrl)
    }

    /**
     * 本体再試行でサムネイル再取得nonceがインクリメントされることを確認する。
     */
    @Test
    fun viewerRetryIncrementsThumbnailRetryNonce() {
        val viewModel = ImageViewerViewModel()
        val url = "https://example.com/image.jpg"

        viewModel.onViewerImageRetry(url)
        viewModel.onViewerImageRetry(url)

        val state = viewModel.uiState.value
        assertEquals(2, state.thumbnailRetryNonceByUrl[url])
    }

    /**
     * URL同期時にサムネイル再取得nonceも表示対象URLへ絞り込まれることを確認する。
     */
    @Test
    fun synchronizeFailedImageUrlsFiltersThumbnailRetryNonce() {
        val viewModel = ImageViewerViewModel()
        val activeUrl = "https://example.com/active.jpg"
        val staleUrl = "https://example.com/stale.jpg"

        viewModel.onViewerImageRetry(activeUrl)
        viewModel.onViewerImageRetry(staleUrl)

        viewModel.synchronizeFailedImageUrls(listOf(activeUrl))

        val state = viewModel.uiState.value
        assertEquals(1, state.thumbnailRetryNonceByUrl[activeUrl])
        assertTrue(staleUrl !in state.thumbnailRetryNonceByUrl)
    }
}
