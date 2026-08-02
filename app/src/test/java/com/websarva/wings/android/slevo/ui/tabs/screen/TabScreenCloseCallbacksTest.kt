package com.websarva.wings.android.slevo.ui.tabs.screen

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * タブ一覧のスレッドタブ close callback が retained API へ委譲する契約を検証するテスト。
 */
class TabScreenCloseCallbacksTest {
    /**
     * close callback が識別子を一度だけ渡し、Composition 所有の suspend API を呼ばないことを確認する。
     */
    @Test
    fun closeHandler_delegatesToRetainedCloseOnly() {
        val tabSessionStore = mockk<TabSessionStore>(relaxed = true)
        val tab = ThreadTabInfo(
            id = ThreadId.of("example.com", "board", "1234567890"),
            title = "スレッド",
            boardName = "board",
            boardUrl = "https://example.com/board/",
            boardId = 1L,
        )
        val closeHandler = createThreadTabCloseHandler(tabSessionStore)

        closeHandler(tab)

        verify(exactly = 1) {
            tabSessionStore.requestCloseThreadTab(tab.threadKey, tab.boardUrl)
        }
        coVerify(exactly = 0) { tabSessionStore.closeThreadTab(tab) }
    }
}
