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
     * 固定状態を保存・復元できることを確認する。
     * `isPinned = true` のタブを保存し、observe で復元されたモデルに反映されることを検証する。
     */
    @Test
    fun saveAndObserve_persistsPinnedState() = runBlocking {
        val threadId = ThreadId.of("example.com", "test", "789")
        db.openThreadTabDao().upsertAll(
            listOf(
                OpenThreadTabEntity(
                    threadId = threadId,
                    sortOrder = 0,
                    isPinned = true,
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 0,
                )
            )
        )
        ThreadStateRepository(db.threadStateDao()).saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = threadId,
                boardId = 1,
                boardUrl = "https://example.com/test/",
                boardName = "Test Board",
                title = "Pinned Thread",
                latestResCount = 50,
            )
        )

        val tab = repository.observeOpenThreadTabs().first().single()

        assertEquals(true, tab.isPinned)
    }

    /**
     * 固定状態を切り替えてもタブの表示順（sortOrder）が変わらないことを確認する。
     */
    @Test
    fun replaceOpenThreadTabsForBulkOperation_maintainsSortOrderRegardlessOfPinState() = runBlocking {
        val threadId1 = ThreadId.of("example.com", "test", "a")
        val threadId2 = ThreadId.of("example.com", "test", "b")
        val tabs = listOf(
            ThreadTabInfo(
                id = threadId1,
                title = "First",
                boardName = "Board",
                boardUrl = "https://example.com/test/",
                boardId = 1,
                isPinned = true,
            ),
            ThreadTabInfo(
                id = threadId2,
                title = "Second",
                boardName = "Board",
                boardUrl = "https://example.com/test/",
                boardId = 1,
                isPinned = false,
            ),
        )

        repository.replaceOpenThreadTabsForBulkOperation(tabs)

        val observed = repository.observeOpenThreadTabs().first()
        assertEquals(2, observed.size)
        assertEquals("First", observed[0].title)
        assertEquals("Second", observed[1].title)
        assertEquals(true, observed[0].isPinned)
        assertEquals(false, observed[1].isPinned)
    }

    /**
     * スクロール位置だけ更新しても、対象タブの sortOrder と isPinned、他タブの状態は変わらない。
     */
    @Test
    fun updateThreadTabScrollPosition_updatesOnlyScrollColumns() = runBlocking {
        val threadId1 = ThreadId.of("example.com", "test", "a")
        val threadId2 = ThreadId.of("example.com", "test", "b")

        // 2 つのタブを保存
        repository.replaceOpenThreadTabsForBulkOperation(
            listOf(
                ThreadTabInfo(
                    id = threadId1,
                    title = "First",
                    boardName = "Board",
                    boardUrl = "https://example.com/test/",
                    boardId = 1,
                    isPinned = true,
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 0,
                ),
                ThreadTabInfo(
                    id = threadId2,
                    title = "Second",
                    boardName = "Board",
                    boardUrl = "https://example.com/test/",
                    boardId = 1,
                    isPinned = false,
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 0,
                ),
            )
        )

        // threadId1 のスクロール位置だけ更新
        repository.updateThreadTabScrollPosition(
            threadId = threadId1,
            firstVisibleItemIndex = 7,
            firstVisibleItemScrollOffset = 30,
        )

        val observed = repository.observeOpenThreadTabs().first()

        val tab1 = observed.find { it.id == threadId1 }!!
        val tab2 = observed.find { it.id == threadId2 }!!

        // threadId1 のスクロール位置だけが変化する
        assertEquals(7, tab1.firstVisibleItemIndex)
        assertEquals(30, tab1.firstVisibleItemScrollOffset)
        assertEquals(true, tab1.isPinned)

        // threadId2 は影響を受けない
        assertEquals(0, tab2.firstVisibleItemIndex)
        assertEquals(0, tab2.firstVisibleItemScrollOffset)
        assertEquals(false, tab2.isPinned)
    }

    /**
     * 存在しない threadId へのスクロール位置保存は no-op となり、
     * タブ一覧やスレッド状態を新規作成しない。
     */
    @Test
    fun updateThreadTabScrollPosition_noOpForMissingTab() = runBlocking {
        val existingThreadId = ThreadId.of("example.com", "test", "existing")
        val missingThreadId = ThreadId.of("example.com", "test", "missing")

        repository.replaceOpenThreadTabsForBulkOperation(
            listOf(
                ThreadTabInfo(
                    id = existingThreadId,
                    title = "Existing",
                    boardName = "Board",
                    boardUrl = "https://example.com/test/",
                    boardId = 1,
                    firstVisibleItemIndex = 0,
                    firstVisibleItemScrollOffset = 0,
                ),
            )
        )

        // 存在しない threadId へスクロール位置保存
        repository.updateThreadTabScrollPosition(
            threadId = missingThreadId,
            firstVisibleItemIndex = 5,
            firstVisibleItemScrollOffset = 20,
        )

        val observed = repository.observeOpenThreadTabs().first()

        // 既存タブはそのまま
        assertEquals(1, observed.size)
        val existingTab = observed.single()
        assertEquals("Existing", existingTab.title)
        assertEquals(0, existingTab.firstVisibleItemIndex)
        assertEquals(0, existingTab.firstVisibleItemScrollOffset)
    }

    /** 1,252 件の既存行に対する targeted add/delete/pin が対象外行を変更しない。 */
    @Test
    fun targetedMutations_preserveOtherRowsAndThreadState() = runBlocking {
        val initialTabs = (0 until 1_252).map { index ->
            ThreadTabInfo(
                id = ThreadId.of("example.com", "test", "thread-$index"),
                title = "Thread $index",
                boardName = "Board",
                boardUrl = "https://example.com/test/",
                boardId = 1L,
                isPinned = index % 2 == 0,
                firstVisibleItemIndex = index,
                firstVisibleItemScrollOffset = index + 1,
            )
        }
        repository.replaceOpenThreadTabsForBulkOperation(initialTabs)
        val addedId = ThreadId.of("example.com", "test", "added")
        repository.ensureOpenThreadTab(
            ThreadTabInfo(
                id = addedId,
                title = "Added",
                boardName = "Board",
                boardUrl = "https://example.com/test/",
                boardId = 1L,
            )
        )
        repository.setThreadTabPinned(addedId, true)
        val afterAdd = repository.observeOpenThreadTabs().first()

        assertEquals(1_253, afterAdd.size)
        assertEquals(true, afterAdd.single { it.id == addedId }.isPinned)
        initialTabs.forEach { expected ->
            val actual = afterAdd.single { it.id == expected.id }
            assertEquals(expected.isPinned, actual.isPinned)
            assertEquals(expected.firstVisibleItemIndex, actual.firstVisibleItemIndex)
            assertEquals(expected.firstVisibleItemScrollOffset, actual.firstVisibleItemScrollOffset)
            assertEquals(expected.title, actual.title)
        }

        repository.deleteOpenThreadTab(addedId)
        val afterDelete = repository.observeOpenThreadTabs().first()
        assertEquals(1_252, afterDelete.size)
        assertEquals(initialTabs.map { it.id }.toSet(), afterDelete.map { it.id }.toSet())
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
        override fun observeLastSelectedTabsPage(): Flow<Int> = flowOf(0)

        override suspend fun setLastSelectedTabsPage(page: Int) = Unit
    }
}
