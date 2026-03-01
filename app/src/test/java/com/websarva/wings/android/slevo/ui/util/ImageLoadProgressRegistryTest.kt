package com.websarva.wings.android.slevo.ui.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ImageLoadProgressRegistry] の request 単位進捗集約を検証するテスト。
 */
class ImageLoadProgressRegistryTest {
    /**
     * テスト間で進捗レジストリ状態を初期化する。
     */
    @After
    fun tearDown() {
        ImageLoadProgressRegistry.clearForTest()
    }

    /**
     * 同一 URL の並行 request で片方が完了しても、残りの進捗状態が維持されることを確認する。
     */
    @Test
    fun finishOneRequestKeepsOtherRequestProgressForSameUrl() {
        val url = "https://example.com/image.gif"
        val requestId1 = ImageLoadProgressRegistry.createRequestId()
        val requestId2 = ImageLoadProgressRegistry.createRequestId()

        ImageLoadProgressRegistry.start(requestId = requestId1, url = url)
        ImageLoadProgressRegistry.start(requestId = requestId2, url = url)
        ImageLoadProgressRegistry.update(
            requestId = requestId1,
            url = url,
            bytesRead = 50,
            contentLength = 100,
        )
        ImageLoadProgressRegistry.update(
            requestId = requestId2,
            url = url,
            bytesRead = 80,
            contentLength = 100,
        )

        ImageLoadProgressRegistry.finish(requestId1)

        val afterOneFinished = ImageLoadProgressRegistry.progressByUrl.value[url]
        assertTrue(afterOneFinished is ImageLoadProgressState.Determinate)
        assertEquals(0.8f, (afterOneFinished as ImageLoadProgressState.Determinate).progress, 0.0001f)

        ImageLoadProgressRegistry.finish(requestId2)

        assertTrue(ImageLoadProgressRegistry.progressByUrl.value[url] == null)
    }

    /**
     * 同一 URL に無段階 request が存在する間は URL 集約結果が無段階になることを確認する。
     */
    @Test
    fun indeterminateStateTakesPriorityWhileAnyRequestIsIndeterminate() {
        val url = "https://example.com/image.png"
        val determinateRequestId = ImageLoadProgressRegistry.createRequestId()
        val indeterminateRequestId = ImageLoadProgressRegistry.createRequestId()

        ImageLoadProgressRegistry.start(requestId = determinateRequestId, url = url)
        ImageLoadProgressRegistry.update(
            requestId = determinateRequestId,
            url = url,
            bytesRead = 70,
            contentLength = 100,
        )
        ImageLoadProgressRegistry.start(requestId = indeterminateRequestId, url = url)

        val mixedState = ImageLoadProgressRegistry.progressByUrl.value[url]
        assertTrue(mixedState is ImageLoadProgressState.Indeterminate)

        ImageLoadProgressRegistry.finish(indeterminateRequestId)

        val onlyDeterminateState = ImageLoadProgressRegistry.progressByUrl.value[url]
        assertTrue(onlyDeterminateState is ImageLoadProgressState.Determinate)
        assertEquals(
            0.7f,
            (onlyDeterminateState as ImageLoadProgressState.Determinate).progress,
            0.0001f,
        )
    }
}
