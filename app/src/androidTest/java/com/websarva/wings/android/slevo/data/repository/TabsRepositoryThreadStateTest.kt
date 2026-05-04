package com.websarva.wings.android.slevo.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `TabsRepository` がタブ固有状態、共通客観状態、履歴既読状態を合成することを検証する。
 * Phase 2 のタブ一覧表示に必要な新着レス数と履歴なしスクロール位置を確認する。
 */
@RunWith(AndroidJUnit4::class)
class TabsRepositoryThreadStateTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TabsRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TabsRepository(
            boardDao = db.openBoardTabDao(),
            threadDao = db.openThreadTabDao(),
            tabsLocalDataSource = FakeTabsLocalDataSource(),
            threadStateRepository = ThreadStateRepository(db.threadStateDao()),
            db = db,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeOpenThreadTabs_derivesNewResCountFromThreadStateAndHistory() = runBlocking {
        val threadId = ThreadId.of("example.com", "test", "123")
        insertOpenThreadTab(threadId, scrollIndex = 7, scrollOffset = 30)
        ThreadStateRepository(db.threadStateDao()).saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = threadId,
                boardId = 1,
                boardUrl = "https://example.com/test/",
                boardName = "Test Board",
                title = "Thread State Title",
                latestResCount = 110,
            )
        )
        db.threadHistoryDao().insert(
            ThreadHistoryEntity(
                threadId = threadId,
                boardUrl = "https://example.com/test/",
                boardId = 1,
                boardName = "Test Board",
                title = "History Title",
                resCount = 90,
                readState = ThreadReadState(
                    prevResCount = 90,
                    lastReadResNo = 100,
                    firstNewResNo = null,
                ),
            )
        )

        val tab = repository.observeOpenThreadTabs().first().single()

        assertEquals("Thread State Title", tab.title)
        assertEquals(110, tab.resCount)
        assertEquals(10, tab.newResCount)
        assertEquals(7, tab.firstVisibleItemIndex)
        assertEquals(30, tab.firstVisibleItemScrollOffset)
    }

    @Test
    fun observeOpenThreadTabs_resetsScrollPosition_whenHistoryDoesNotExist() = runBlocking {
        val threadId = ThreadId.of("example.com", "test", "456")
        insertOpenThreadTab(threadId, scrollIndex = 5, scrollOffset = 20)
        ThreadStateRepository(db.threadStateDao()).saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = threadId,
                boardId = 1,
                boardUrl = "https://example.com/test/",
                boardName = "Test Board",
                title = "Thread State Title",
                latestResCount = 90,
            )
        )

        val tab = repository.observeOpenThreadTabs().first().single()

        assertEquals(0, tab.newResCount)
        assertEquals(0, tab.firstVisibleItemIndex)
        assertEquals(0, tab.firstVisibleItemScrollOffset)
    }

    /**
     * テスト用の開いているスレッドタブを保存する。
     * Phase 3 ではタブ固有状態だけを保存し、タイトルやレス数は `thread_states` 側で用意する。
     */
    private suspend fun insertOpenThreadTab(
        threadId: ThreadId,
        scrollIndex: Int,
        scrollOffset: Int,
    ) {
        db.openThreadTabDao().upsertAll(
            listOf(
                OpenThreadTabEntity(
                    threadId = threadId,
                    sortOrder = 0,
                    firstVisibleItemIndex = scrollIndex,
                    firstVisibleItemScrollOffset = scrollOffset,
                )
            )
        )
    }

    /**
     * タブ選択ページだけを返すテスト用 DataSource。
     * DB 合成テストでは選択ページの永続化を使用しない。
     */
    private class FakeTabsLocalDataSource : TabsLocalDataSource {
        override fun observeLastSelectedPage(): Flow<Int> = flowOf(0)

        override suspend fun setLastSelectedPage(page: Int) = Unit
    }
}
