package com.websarva.wings.android.slevo.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
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
        assertEquals(true, tab.hasHistory)
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
        assertEquals(false, tab.hasHistory)
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

    /** Board bulk DELETE が901件を900件chunkに分けても対象行だけを削除することを確認する。 */
    @Test
    fun deleteOpenBoardTabs_deletesChunkedTargetsAndPreservesOtherRows() = runBlocking {
        val pinnedUrl = "https://example.com/pinned/"
        val targetUrls = (0 until 901).map { "https://example.com/board-$it/" }
        db.openBoardTabDao().upsertAll(
            (targetUrls + pinnedUrl).mapIndexed { index, url ->
                OpenBoardTabEntity(
                    boardUrl = url,
                    boardId = index.toLong(),
                    boardName = url,
                    serviceName = "example.com",
                    sortOrder = index,
                    isPinned = url == pinnedUrl,
                )
            }
        )

        assertEquals(TabMutationResult.Success, repository.deleteOpenBoardTabs(targetUrls))

        val remaining = db.openBoardTabDao().getAll()
        assertEquals(listOf(pinnedUrl), remaining.map { it.boardUrl })
    }

    /** Thread bulk DELETE が1252件をchunk化し、固定された対象外行を維持することを確認する。 */
    @Test
    fun deleteOpenThreadTabs_deletesLargeTargetSetAndPreservesOtherRows() = runBlocking {
        val pinnedId = ThreadId.of("example.com", "test", "pinned")
        val targetIds = (0 until 1_252).map { ThreadId.of("example.com", "test", "thread-$it") }
        db.openThreadTabDao().upsertAll(
            (targetIds + pinnedId).mapIndexed { index, threadId ->
                OpenThreadTabEntity(
                    threadId = threadId,
                    sortOrder = index,
                    isPinned = threadId == pinnedId,
                    firstVisibleItemIndex = index,
                    firstVisibleItemScrollOffset = index + 1,
                )
            }
        )

        assertEquals(true, repository.deleteOpenThreadTabs(targetIds))

        val remaining = db.openThreadTabDao().getAll()
        assertEquals(listOf(pinnedId), remaining.map { it.threadId })
    }

    /** bulk DELETE の空集合はDB、GCともに変更しないNoOpになることを確認する。 */
    @Test
    fun deleteOpenTabs_emptyTargets_areNoOp() = runBlocking {
        assertEquals(TabMutationResult.NoOp, repository.deleteOpenBoardTabs(emptyList()))
        assertEquals(false, repository.deleteOpenThreadTabs(emptyList()))
        assertEquals(0, db.openBoardTabDao().getAll().size)
        assertEquals(0, db.openThreadTabDao().getAll().size)
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

    /** 1,252 件の既存行に対する対象行単位の追加・削除・固定操作が、対象外行を変更しない。 */
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

        repository.updateThreadTabScrollPosition(
            threadId = addedId,
            firstVisibleItemIndex = 77,
            firstVisibleItemScrollOffset = 88,
        )
        repository.updateThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = addedId,
                boardId = 9L,
                boardUrl = "https://example.com/test/",
                boardName = "Updated board",
                title = "Updated title",
                latestResCount = 777,
            )
        )
        val afterTargetedUpdates = repository.observeOpenThreadTabs().first()
        val updatedTarget = afterTargetedUpdates.single { it.id == addedId }
        assertEquals("Updated title", updatedTarget.title)
        assertEquals("Updated board", updatedTarget.boardName)
        assertEquals(9L, updatedTarget.boardId)
        assertEquals(777, updatedTarget.resCount)
        assertEquals(77, updatedTarget.firstVisibleItemIndex)
        assertEquals(88, updatedTarget.firstVisibleItemScrollOffset)
        assertEquals(true, updatedTarget.isPinned)
        afterAdd.filterNot { it.id == addedId }.forEach { expected ->
            assertEquals(expected, afterTargetedUpdates.single { actual -> actual.id == expected.id })
        }

        repository.deleteOpenThreadTab(addedId)
        val afterDelete = repository.observeOpenThreadTabs().first()
        assertEquals(1_252, afterDelete.size)
        assertEquals(initialTabs.map { it.id }.toSet(), afterDelete.map { it.id }.toSet())
    }

    /** 既存の解決済みメタデータへプレースホルダーを再 ensure しても、正規値を保持する。 */
    @Test
    fun ensureExistingTab_placeholderMetadataPreservesCanonicalStateAndTabFields() = runBlocking {
        val targetId = ThreadId.of("example.com", "test", "target")
        val otherId = ThreadId.of("example.com", "test", "other")
        insertOpenThreadTab(
            threadId = targetId,
            scrollIndex = 7,
            scrollOffset = 30,
            sortOrder = 1,
            isPinned = true,
        )
        insertOpenThreadTab(threadId = otherId, scrollIndex = 2, scrollOffset = 4, sortOrder = 0)
        val stateRepository = ThreadStateRepository(db.threadStateDao())
        stateRepository.saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = targetId,
                boardId = 42L,
                boardUrl = "https://example.com/test/",
                boardName = "Resolved board",
                title = "Resolved title",
                latestResCount = 120,
            )
        )
        stateRepository.saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = otherId,
                boardId = 7L,
                boardUrl = "https://example.com/test/",
                boardName = "Other board",
                title = "Other title",
                latestResCount = 15,
            )
        )
        db.threadHistoryDao().insert(
            ThreadHistoryEntity(
                threadId = targetId,
                boardUrl = "https://example.com/test/",
                boardId = 42L,
                boardName = "Resolved board",
                title = "Resolved title",
                resCount = 120,
                readState = ThreadReadState(
                    prevResCount = 100,
                    lastReadResNo = 105,
                    firstNewResNo = null,
                ),
            )
        )

        val placeholderRequest = ThreadTabInfo(
            id = targetId,
            title = "https://example.com/test/read.cgi/test/target/",
            boardName = "https://other.example/wrong/",
            boardUrl = "https://other.example/wrong/",
            boardId = 0L,
            resCount = 80,
        )
        repository.ensureOpenThreadTab(placeholderRequest)

        val afterPlaceholder = repository.observeOpenThreadTabs().first()
        assertEquals(listOf(otherId, targetId), afterPlaceholder.map { it.id })
        val preserved = afterPlaceholder.single { it.id == targetId }
        assertEquals(42L, preserved.boardId)
        assertEquals("Resolved board", preserved.boardName)
        assertEquals("https://example.com/test/", preserved.boardUrl)
        assertEquals("Resolved title", preserved.title)
        assertEquals(120, preserved.resCount)
        assertEquals(100, preserved.prevResCount)
        assertEquals(105, preserved.lastReadResNo)
        assertEquals(null, preserved.firstNewResNo)
        assertEquals(true, preserved.hasHistory)
        assertEquals(7, preserved.firstVisibleItemIndex)
        assertEquals(30, preserved.firstVisibleItemScrollOffset)
        assertEquals(true, preserved.isPinned)
        assertEquals("Other title", afterPlaceholder.single { it.id == otherId }.title)

        repository.ensureOpenThreadTab(
            placeholderRequest.copy(
                title = "Updated title",
                boardName = "Updated board",
                boardUrl = "https://example.com/test/",
                boardId = 43L,
                resCount = 130,
            )
        )

        val afterResolvedUpdate = repository.observeOpenThreadTabs().first()
        val updated = afterResolvedUpdate.single { it.id == targetId }
        assertEquals(43L, updated.boardId)
        assertEquals("Updated board", updated.boardName)
        assertEquals("https://example.com/test/", updated.boardUrl)
        assertEquals("Updated title", updated.title)
        assertEquals(130, updated.resCount)
        assertEquals(7, updated.firstVisibleItemIndex)
        assertEquals(30, updated.firstVisibleItemScrollOffset)
        assertEquals(true, updated.isPinned)
        assertEquals(
            afterPlaceholder.single { it.id == otherId },
            afterResolvedUpdate.single { it.id == otherId },
        )
    }

    /**
     * テスト用の開いているスレッドタブを保存する。
     * Phase 3 ではタブ固有状態だけを保存し、タイトルやレス数は `thread_states` 側で用意する。
     */
    private suspend fun insertOpenThreadTab(
        threadId: ThreadId,
        scrollIndex: Int,
        scrollOffset: Int,
        sortOrder: Int = 0,
        isPinned: Boolean = false,
    ) {
        db.openThreadTabDao().upsertAll(
            listOf(
                OpenThreadTabEntity(
                    threadId = threadId,
                    sortOrder = sortOrder,
                    isPinned = isPinned,
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
