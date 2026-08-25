package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.slevo.data.util.FiveChPostReceiptParser
import com.websarva.wings.android.slevo.di.PersistentCookieJar
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PostRepository] がresponse close前に投稿証拠を抽出することを確認する。 */
class PostRepositoryTest {
    @Test
    fun postTo5chFirstPhase_returnsReceiptFromCaseInsensitiveHeaders() = runTest {
        val remote = FakePostRemoteDataSource(
            firstResponse = response(
                headers = mapOf(
                    "x-resnum" to "12",
                    "X-POSTPLACE" to "test/123",
                    "x-postdate" to "1704067200.01",
                    "x-posterid" to "ABC",
                ),
            ),
        )
        val repository = PostRepository(
            remoteDataSource = remote,
            cookieJar = mockk<PersistentCookieJar>(relaxed = true),
            logger = mockk<AppLogger>(relaxed = true),
            postReceiptParser = FiveChPostReceiptParser(),
        )

        val result = repository.postTo5chFirstPhase(
            host = "example.com",
            board = "test",
            threadKey = "123",
            name = "name",
            mail = "mail",
            message = "message",
        )

        assertTrue(result is PostResult.Success)
        val receipt = (result as PostResult.Success).receipt
        assertEquals(12, receipt.confirmedResNum)
        assertEquals(1_704_067_200_010L, receipt.serverPostDateMillis)
        assertEquals("ABC", receipt.posterIdHint)
    }

    @Test
    fun postTo5chFirstPhase_invalidHeaders_keepPostingSuccessfulWithoutEvidence() = runTest {
        val remote = FakePostRemoteDataSource(
            firstResponse = response(
                headers = mapOf("X-Resnum" to "invalid", "X-Postdate" to "-1"),
            ),
        )
        val repository = PostRepository(
            remoteDataSource = remote,
            cookieJar = mockk<PersistentCookieJar>(relaxed = true),
            logger = mockk<AppLogger>(relaxed = true),
            postReceiptParser = FiveChPostReceiptParser(),
        )

        val result = repository.postTo5chFirstPhase(
            host = "example.com",
            board = "test",
            threadKey = "123",
            name = "name",
            mail = "mail",
            message = "message",
        )

        val receipt = (result as PostResult.Success).receipt
        assertNull(receipt.confirmedResNum)
        assertNull(receipt.serverPostDateMillis)
    }

    private fun response(headers: Map<String, String>): Response {
        val request = Request.Builder().url("https://example.com/test/bbs.cgi").build()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("<html><head><title>書きこみました</title></head></html>".toResponseBody("text/html".toMediaType()))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private class FakePostRemoteDataSource(
        private val firstResponse: Response,
    ) : PostRemoteDataSource {
        override suspend fun postFirstPhase(
            host: String,
            board: String,
            threadKey: String,
            name: String,
            mail: String,
            message: String,
        ): Response = firstResponse

        override suspend fun postSecondPhase(
            host: String,
            board: String,
            threadKey: String,
            confirmationData: ConfirmationData,
        ): Response? = null
    }
}
