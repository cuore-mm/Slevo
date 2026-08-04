package com.websarva.wings.android.slevo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [OwnPostThreadScope] のprovider・板・スレッド分離を検証する。 */
class OwnPostThreadScopeTest {
    @Test
    fun from_validBoardUrl_returnsStableScope() {
        val scope = OwnPostThreadScope.from(
            boardUrl = "https://agree.5ch.net/operate/",
            threadKey = "1234567890",
        )

        assertEquals(
            OwnPostThreadScope("5ch.net", "operate", "1234567890"),
            scope,
        )
    }

    @Test
    fun from_invalidOrIncompleteInput_returnsNull() {
        assertNull(OwnPostThreadScope.from("not a url", "123"))
        assertNull(OwnPostThreadScope.from("https://example.com/board/", ""))
        assertNull(OwnPostThreadScope.from("https://example.com/", "123"))
    }
}
