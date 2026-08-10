package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabPendingOperation
import com.websarva.wings.android.slevo.ui.tabs.coordinator.isThreadTabOperationConfirmed
import com.websarva.wings.android.slevo.ui.tabs.coordinator.projectThreadTabs
import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution
import com.websarva.wings.android.slevo.ui.tabs.session.PendingThreadPostState
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.mergeThreadTabMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ThreadTabsCoordinator` のタブ生成方針を検証するテスト。
 *
 * URL由来でタイトル未取得のケースと、5ch.net/5ch.io の重複タブ許容挙動を確認する。
 */
class ThreadTabsCoordinatorTest {

    /**
     * タイトル未取得（null）の route でタブを作成した場合、
     * 正規化後 boardUrl と threadKey から構築したURLをタイトルとして保存することを確認する。
     */
    @Test
    fun ensureThreadTab_savesThreadUrlWhenThreadTitleIsNull() = runTest {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)

        val index = coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = null,
            )
        )

        assertEquals(0, index)
        assertEquals(1, coordinator.openThreadTabs.value.size)
        assertEquals(
            "https://medaka.5ch.io/test/read.cgi/mmominor/1723111700/",
            coordinator.openThreadTabs.value.first().title
        )
         coVerify(exactly = 0) { tabsRepository.replaceOpenThreadTabsForBulkOperation(any()) }
    }

    /**
     * host が異なる同一 board/thread は別タブとして保存されることを確認する。
     */
    @Test
    fun ensureThreadTab_createsSeparateTabsForNetAndIoHosts() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))

        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.net/mmominor/",
                boardName = "mmominor",
                threadTitle = "old",
            )
        )
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "new",
            )
        )

        assertEquals(2, coordinator.openThreadTabs.value.size)
        val threadIds = coordinator.openThreadTabs.value.map { it.id.value }.toSet()
        assertTrue(threadIds.any { it.contains("medaka.5ch.net") })
        assertTrue(threadIds.any { it.contains("medaka.5ch.io") })
    }

    /**
     * `togglePinThreadTab` で対象スレッドタブの固定状態を切り替えることを確認する。
     */
    @Test
    fun togglePinThreadTab_togglesPinnedState() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "test",
            )
        )
        val threadId = coordinator.openThreadTabs.value.first().id

        assertEquals(false, coordinator.openThreadTabs.value.first().isPinned)

        coordinator.togglePinThreadTab(threadId)

        assertEquals(true, coordinator.openThreadTabs.value.first().isPinned)

        coordinator.togglePinThreadTab(threadId)

        assertEquals(false, coordinator.openThreadTabs.value.first().isPinned)
    }

    /**
     * スレッドタブ選択時に selected key が ThreadId で更新されることを確認する。
     */
    @Test
    fun selectThreadTab_updatesSelectedThreadTabKey() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "test",
            )
        )
        val threadId = coordinator.openThreadTabs.value.first().id

        coordinator.selectThreadTab(threadId)

        assertEquals(threadId.value, coordinator.selectedThreadTabKey.value)
    }

    /** 存在しない対象の選択は失敗するが、既存の選択キーは消去しない。 */
    @Test
    fun selectThreadTab_missingTargetPreservesExistingSelection() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "existing",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "existing",
            )
        )
        val selected = coordinator.openThreadTabs.value.single().id
        assertTrue(coordinator.selectThreadTab(selected))

        val missing = com.websarva.wings.android.slevo.data.model.ThreadId.of(
            "medaka.5ch.io",
            "mmominor",
            "missing",
        )
        assertFalse(coordinator.selectThreadTab(missing))
        assertEquals(selected.value, coordinator.selectedThreadTabKey.value)
    }

    /**
     * 選択中スレッドタブを閉じた場合、selected key が隣接タブへ補正されることを確認する。
     */
    @Test
    fun closeThreadTab_updatesSelectedKeyToAdjacentTab() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "222",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "second",
            )
        )
        val first = coordinator.openThreadTabs.value.first()
        val second = coordinator.openThreadTabs.value.last()
        coordinator.selectThreadTab(first.id)

        coordinator.closeThreadTab(first)

        assertEquals(second.id.value, coordinator.selectedThreadTabKey.value)
    }

    /** Thread bulk close が固定タブを残し、逐次closeと同じ最終選択へ収束することを確認する。 */
    @Test
    fun closeThreadTabs_keepsPinnedTabAndMatchesSequentialSelection() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val pinnedRoute = AppRoute.Thread(
            threadKey = "pinned",
            boardUrl = "https://medaka.5ch.io/mmominor/",
            boardName = "mmominor",
            threadTitle = "Pinned",
        )
        val selectedRoute = pinnedRoute.copy(threadKey = "selected", threadTitle = "Selected")
        val lastRoute = pinnedRoute.copy(threadKey = "last", threadTitle = "Last")
        coordinator.ensureThreadTab(pinnedRoute)
        coordinator.ensureThreadTab(selectedRoute)
        coordinator.ensureThreadTab(lastRoute)
        val pinned = coordinator.openThreadTabs.value.first()
        val selected = coordinator.openThreadTabs.value[1]
        val last = coordinator.openThreadTabs.value[2]
        coordinator.togglePinThreadTab(pinned.id)
        coordinator.selectThreadTab(selected.id)

        coordinator.closeThreadTabs(listOf(selected, last))

        assertEquals(listOf(pinned.id), coordinator.openThreadTabs.value.map { it.id })
        assertEquals(pinned.id.value, coordinator.selectedThreadTabKey.value)
    }

    /**
     * 最後のスレッドタブを閉じた場合、selected key が null になることを確認する。
     */
    @Test
    fun closeLastThreadTab_clearsSelectedThreadTabKey() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        val tab = coordinator.openThreadTabs.value.first()
        coordinator.selectThreadTab(tab.id)

        coordinator.closeThreadTab(tab)

        assertNull(coordinator.selectedThreadTabKey.value)
    }

    /**
     * タブを閉じたときに対象タブのセッション状態だけが削除されることを確認する。
     */
    @Test
    fun closeThreadTab_removesOnlyTargetSessionState() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "222",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "second",
            )
        )
        val first = coordinator.openThreadTabs.value.first()
        val second = coordinator.openThreadTabs.value.last()
        coordinator.updateThreadSessionState(first.id) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("first"))
        }
        coordinator.updateThreadSessionState(second.id) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("second"))
        }

        coordinator.closeThreadTab(first)

        assertFalse(coordinator.threadSessionStates.value.containsKey(first.id.value))
        assertEquals("second", coordinator.getThreadSessionState(second.id).searchQuery)
    }

    /**
     * セッション状態更新が永続タブ保存を呼ばないことを確認する。
     */
    @Test
    fun updateThreadSessionState_doesNotPersistTabs() = runTest {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        val tab = coordinator.openThreadTabs.value.first()

        coordinator.updateThreadSessionState(tab.id) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("query"))
        }

        assertEquals("query", coordinator.getThreadSessionState(tab.id).searchQuery)
         coVerify(exactly = 0) { tabsRepository.replaceOpenThreadTabsForBulkOperation(any()) }
    }

    @Test
    fun closeThreadTab_removesTargetRuntimeState() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        val tab = coordinator.openThreadTabs.value.first()
        coordinator.updateThreadRuntimeState(tab.id) {
            it.copy(pendingPost = PendingThreadPostState(10, "message", "name", "mail"))
        }

        coordinator.closeThreadTab(tab)

        assertEquals(null, coordinator.threadRuntimeStates.value[tab.id.value])
    }

    /**
     * 解決済み boardId を反映しても、既存の表示メタ情報とスクロール位置を保持することを確認する。
     */
    @Test
    fun updateThreadResolvedBoardInfo_updatesBoardIdAndPreservesThreadTabFields() = runTest {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
                boardId = 0L,
                resCount = 10,
            )
        )
        val original = coordinator.openThreadTabs.value.first()
        coordinator.togglePinThreadTab(original.id)

        coordinator.updateThreadResolvedBoardInfo(
            threadId = original.id,
            boardId = 42L,
            boardName = "resolved",
        )

        val actual = coordinator.openThreadTabs.value.first()
        assertEquals(42L, actual.boardId)
        assertEquals("resolved", actual.boardName)
        assertEquals(original.title, actual.title)
        assertEquals(10, actual.resCount)
        assertEquals(true, actual.isPinned)
    }

    /** 初回 Room 通知が届くまで、1,252 件の正規状態を空一覧で上書きしない。 */
    @Test
    fun ensureThreadTab_waitsForInitialSnapshotBeforeDatabaseWrite() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 0, extraBufferCapacity = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val writeStarted = CompletableDeferred<Unit>()
        val writeRelease = CompletableDeferred<Unit>()
        val initialTabs = (0 until 1_252).map { index -> testTab("existing-$index", index) }
        val route = testRoute("new-thread")
        val addedTab = testTab("new-thread", 1_252)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } coAnswers {
            writeStarted.complete(Unit)
            writeRelease.await()
            true
        }
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        val dispatcher = StandardTestDispatcher(testScheduler)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + dispatcher))

        val ensureJob = backgroundScope.async { coordinator.ensureThreadTab(route) }
        runCurrent()
        assertFalse(writeStarted.isCompleted)
        assertEquals(0, coordinator.openThreadTabs.value.size)

        databaseFlow.emit(initialTabs)
        runCurrent()
        assertTrue(writeStarted.isCompleted)
        assertEquals(1_253, coordinator.openThreadTabs.value.size)
        writeRelease.complete(Unit)
        runCurrent()
        assertFalse(ensureJob.isCompleted)

        databaseFlow.emit(initialTabs)
        runCurrent()
        assertEquals(1_253, coordinator.openThreadTabs.value.size)
        databaseFlow.emit(initialTabs + addedTab)
        runCurrent()
        assertEquals(1_252, ensureJob.await())
        assertEquals(1_253, coordinator.openThreadTabs.value.size)
        assertEquals(1_253, coordinator.openThreadTabs.value.map { it.id }.toSet().size)
    }

    /** 古い 1,252 件の通知中も保留中の追加を再投影し、確認前に完了を返さない。 */
    @Test
    fun pendingAdd_survivesStaleSnapshotUntilCanonicalConfirmation() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val initialTabs = (0 until 1_252).map { index -> testTab("existing-$index", index) }
        val route = testRoute("added")
        val addedTab = testTab("added", 1_252)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } returns true
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(initialTabs)
        coordinator.bind(backgroundScope)
        runCurrent()

        val ensureJob = backgroundScope.async { coordinator.ensureThreadTab(route) }
        runCurrent()
        databaseFlow.emit(initialTabs)
        runCurrent()
        assertEquals(1_253, coordinator.openThreadTabs.value.size)
        assertFalse(ensureJob.isCompleted)
        databaseFlow.emit(initialTabs + addedTab)
        runCurrent()

        assertEquals(1_252, ensureJob.await())
        assertEquals(initialTabs.map { it.id }.toSet() + addedTab.id, coordinator.openThreadTabs.value.map { it.id }.toSet())
    }

    /** Ensure と Info は対象 identity、Delete は不在、Pin は要求値で確認する。 */
    @Test
    fun ensureConfirmation_usesMinimalOperationConditions() {
        val current = testTab("metadata", 3, isPinned = true, scrollIndex = 7).copy(
            title = "Old title",
            boardName = "Old board",
            boardId = 42L,
            resCount = 120,
        )
        val expected = current.copy(
            title = "New title",
            boardName = "New board",
            boardId = 43L,
            resCount = 140,
        )
        assertTrue(isThreadTabOperationConfirmed(listOf(current), ThreadTabPendingOperation.Ensure(expected)))
        assertTrue(isThreadTabOperationConfirmed(listOf(current), ThreadTabPendingOperation.Info(expected)))
        assertTrue(isThreadTabOperationConfirmed(listOf(current), ThreadTabPendingOperation.Pin(current.id, true)))
        assertTrue(isThreadTabOperationConfirmed(emptyList(), ThreadTabPendingOperation.Delete(current.id)))
        assertFalse(isThreadTabOperationConfirmed(listOf(current), ThreadTabPendingOperation.Delete(current.id)))
        assertFalse(isThreadTabOperationConfirmed(listOf(current), ThreadTabPendingOperation.Pin(current.id, false)))
    }

    /** 無関係な通知で Ensure を完了でき、後続の canonical 通知で metadata が収束する。 */
    @Test
    fun ensureExistingTab_completesOnUnrelatedRevisionAndConvergesMetadata() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val currentTarget = testTab("target", 3, isPinned = true, scrollIndex = 7).copy(
            title = "Old title",
            boardName = "Old board",
            boardId = 42L,
            resCount = 120,
            firstVisibleItemScrollOffset = 30,
        )
        val currentUnrelated = testTab("unrelated", 4, isPinned = false, scrollIndex = 2)
        val requestedTarget = currentTarget.copy(
            title = "New title",
            boardName = "New board",
            boardId = 43L,
            resCount = 140,
        )
        val next = testTab("next", 5)
        val requestedNext = testRoute("next")
        val writes = mutableListOf<String>()

        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } coAnswers {
            writes += (invocation.args[0] as ThreadTabInfo).id.value
            true
        }

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(currentTarget, currentUnrelated))
        coordinator.bind(backgroundScope)
        runCurrent()

        val route = AppRoute.Thread(
            threadKey = "target",
            boardUrl = "https://host/board/",
            boardName = "New board",
            threadTitle = "New title",
            boardId = 43L,
            resCount = 140,
        )
        val ensureJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureThreadTab(route)
        }
        val nextJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureThreadTab(requestedNext)
        }
        runCurrent()

        assertEquals(listOf(currentTarget.id.value, next.id.value), writes)
        assertFalse(ensureJob.isCompleted)
        assertFalse(nextJob.isCompleted)

        val unrelatedRevision = listOf(currentTarget, currentUnrelated.copy(title = "Unrelated new title"))
        databaseFlow.emit(unrelatedRevision)
        runCurrent()

        val pendingTarget = coordinator.openThreadTabs.value.first { it.id == currentTarget.id }
        assertTrue(ensureJob.isCompleted)
        assertFalse(nextJob.isCompleted)
        assertEquals(listOf(currentTarget.id.value, next.id.value), writes)
        assertEquals(
            listOf(currentTarget.id, currentUnrelated.id, next.id),
            coordinator.openThreadTabs.value.map { it.id },
        )
        assertEquals(currentTarget.title, pendingTarget.title)
        assertEquals(currentTarget.boardName, pendingTarget.boardName)
        assertEquals(currentTarget.boardUrl, pendingTarget.boardUrl)
        assertEquals(currentTarget.boardId, pendingTarget.boardId)
        assertEquals(currentTarget.resCount, pendingTarget.resCount)
        assertEquals(currentTarget.firstVisibleItemIndex, pendingTarget.firstVisibleItemIndex)
        assertEquals(currentTarget.firstVisibleItemScrollOffset, pendingTarget.firstVisibleItemScrollOffset)
        assertEquals(currentTarget.isPinned, pendingTarget.isPinned)
        assertEquals(
            "Unrelated new title",
            coordinator.openThreadTabs.value.first { it.id == currentUnrelated.id }.title,
        )

        val confirmedTarget = mergeThreadTabMetadata(currentTarget, requestedTarget)
        databaseFlow.emit(listOf(confirmedTarget, currentUnrelated.copy(title = "Unrelated new title")))
        runCurrent()

        assertEquals(listOf(currentTarget.id.value, next.id.value), writes)
        assertFalse(nextJob.isCompleted)
        assertEquals("New title", coordinator.openThreadTabs.value.first { it.id == currentTarget.id }.title)
        assertEquals("New board", coordinator.openThreadTabs.value.first { it.id == currentTarget.id }.boardName)
        assertEquals(43L, coordinator.openThreadTabs.value.first { it.id == currentTarget.id }.boardId)
        assertEquals(140, coordinator.openThreadTabs.value.first { it.id == currentTarget.id }.resCount)

        databaseFlow.emit(listOf(confirmedTarget, currentUnrelated.copy(title = "Unrelated new title"), next))
        runCurrent()

        assertEquals(0, ensureJob.await())
        assertEquals(2, nextJob.await())
        assertEquals(listOf(confirmedTarget.id, currentUnrelated.id, next.id), coordinator.openThreadTabs.value.map { it.id })
    }

    /** 空の読み込み済み状態を有効な状態として扱い、空のスナップショット後に追加を実行できる。 */
    @Test
    fun loadedEmpty_allowsMutationAfterInitialEmptyEmission() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val route = testRoute("first")
        val tab = testTab("first", 0)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } returns true
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(emptyList())
        coordinator.bind(backgroundScope)
        runCurrent()

        val ensureJob = backgroundScope.async { coordinator.ensureThreadTab(route) }
        runCurrent()
        assertTrue(ensureJob.isActive)
        databaseFlow.emit(listOf(tab))
        runCurrent()
        assertEquals(0, ensureJob.await())
    }

    /** 投影処理は追加・削除・固定を FIFO 順に適用し、対象外タブの固有値を変更しない。 */
    @Test
    fun projection_appliesRapidMutationIntentsInOrder() {
        val first = testTab("first", 0, isPinned = false, scrollIndex = 7)
        val second = testTab("second", 1, isPinned = true, scrollIndex = 9)
        val third = testTab("third", 2)
        val result = projectThreadTabs(
            canonicalTabs = listOf(first, second),
            pendingOperations = listOf(
                ThreadTabPendingOperation.Pin(first.id, true),
                ThreadTabPendingOperation.Delete(second.id),
                ThreadTabPendingOperation.Ensure(third),
            ),
        )

        assertEquals(listOf(first.id, third.id), result.map { it.id })
        assertTrue(result.first().isPinned)
        assertEquals(7, result.first().firstVisibleItemIndex)
    }

    /** プレースホルダーメタデータを、保留中の投影と正規行で同じようにマージする。 */
    @Test
    fun projection_placeholderEnsurePreservesResolvedMetadataAndTabFields() {
        val current = testTab("resolved", 3, isPinned = true, scrollIndex = 7).copy(
            title = "Resolved title",
            boardName = "Resolved board",
            boardUrl = "https://host/board/",
            boardId = 42L,
            resCount = 120,
            firstVisibleItemScrollOffset = 30,
        )
        val unrelated = testTab("unrelated", 4, isPinned = false, scrollIndex = 2)
        val placeholder = current.copy(
            title = "https://host/test/read.cgi/board/resolved/",
            boardName = "https://other.example/wrong/",
            boardUrl = "https://other.example/wrong/",
            boardId = 0L,
            resCount = 80,
            isPinned = false,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )

        val result = projectThreadTabs(
            canonicalTabs = listOf(current, unrelated),
            pendingOperations = listOf(ThreadTabPendingOperation.Ensure(placeholder)),
        )

        val projected = result.single { it.id == current.id }
        assertEquals("Resolved title", projected.title)
        assertEquals("Resolved board", projected.boardName)
        assertEquals("https://host/board/", projected.boardUrl)
        assertEquals(42L, projected.boardId)
        assertEquals(120, projected.resCount)
        assertEquals(7, projected.firstVisibleItemIndex)
        assertEquals(30, projected.firstVisibleItemScrollOffset)
        assertEquals(true, projected.isPinned)
        assertEquals(listOf(current.id, unrelated.id), result.map { it.id })
    }

    /** 1,252 canonical rowsへ100 pendingを一度だけ折り畳み、順序と key 一意性を維持する。 */
    @Test
    fun projection_largeCanonicalAndRapidCommandsPreservesOrderAndUniqueKeys() {
        val canonical = (0 until 1_252).map { index ->
            testTab("large-$index", index, isPinned = index % 2 == 0, scrollIndex = index)
        }
        val pending = (0 until 100).map { index ->
            ThreadTabPendingOperation.Pin(canonical[index].id, isPinned = index % 2 != 0)
        }

        val projected = projectThreadTabs(canonical, pending)

        assertEquals(1_252, projected.size)
        assertEquals(canonical.map { it.id }, projected.map { it.id })
        assertEquals(projected.size, projected.map { it.id }.toSet().size)
        pending.forEach { operation ->
            val projectedTab = projected.single { it.id == operation.threadId }
            assertEquals(operation.isPinned, projectedTab.isPinned)
        }
        assertEquals(canonical[1_000].firstVisibleItemIndex, projected[1_000].firstVisibleItemIndex)
    }

    /** rapid same-key toggle は targeted write だけを発行し、canonical pin 通知で完了する。 */
    @Test
    fun togglePinThreadTab_rapidTogglesUseTargetedWritesAndIndependentConfirmation() = runTest {
        assertRapidPinToggles()
    }

    /** 中間 pin 通知を省略しても、先行 waiter を supersession で終端し最終値へ収束する。 */
    @Test
    fun togglePinThreadTab_finalSnapshotOnlySupersedesEarlierWaiters() = runTest {
        // --- Dependencies and initial canonical snapshot ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val initialTab = testTab("pin-final-only", 0, isPinned = false)
        val requestedPins = mutableListOf<Boolean>()
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.setThreadTabPinned(initialTab.id, any()) } coAnswers {
            requestedPins += (invocation.args[1] as Boolean)
            true
        }
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(initialTab))
        coordinator.bind(backgroundScope)
        runCurrent()

        // --- Rapid targeted writes ---
        val toggleJobs = (0 until 4).map {
            backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.togglePinThreadTab(initialTab.id)
            }
        }
        runCurrent()

        assertEquals(listOf(true, false, true, false), requestedPins)
        assertTrue(toggleJobs.take(3).all { it.isCompleted })
        assertFalse(toggleJobs.last().isCompleted)
        assertEquals(false, coordinator.openThreadTabs.value.single().isPinned)

        // --- Final canonical snapshot ---
        databaseFlow.emit(listOf(initialTab.copy(isPinned = false)))
        runCurrent()
        toggleJobs.forEach { it.await() }

        assertEquals(false, coordinator.openThreadTabs.value.single().isPinned)
        coVerify(exactly = 4) { tabsRepository.setThreadTabPinned(initialTab.id, any()) }
        coVerify(exactly = 0) { tabsRepository.replaceOpenThreadTabsForBulkOperation(any()) }
    }

    /** Thread bulk pendingが対象集合を一度に投影から除外し、全対象不在だけを確認することを確認する。 */
    @Test
    fun projectThreadTabs_bulkDeleteRemovesTargetSetAndConfirmsAllTargetsAbsent() {
        val first = ThreadTabInfo(
            id = ThreadId.of("medaka.5ch.io", "mmominor", "first"),
            title = "First",
            boardName = "mmominor",
            boardUrl = "https://medaka.5ch.io/mmominor/",
            boardId = 1L,
        )
        val second = first.copy(id = ThreadId.of("medaka.5ch.io", "mmominor", "second"))
        val pinned = first.copy(
            id = ThreadId.of("medaka.5ch.io", "mmominor", "pinned"),
            isPinned = true,
        )
        val operation = ThreadTabPendingOperation.BulkDelete(
            threadIds = listOf(first.id, second.id),
            requestedSelection = pinned.id.value,
        )

        assertEquals(listOf(pinned), projectThreadTabs(listOf(first, second, pinned), listOf(operation)))
        assertFalse(isThreadTabOperationConfirmed(listOf(first, pinned), operation))
        assertTrue(isThreadTabOperationConfirmed(listOf(pinned), operation))
    }

    /** Ensure の待機中に Delete が成功した場合、Ensure を -1 で終端して最終削除を確認する。 */
    @Test
    fun ensureThenDelete_finalSnapshotOnlyReturnsMinusOneForEnsure() = runTest {
        // --- Dependencies and initial canonical snapshot ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val route = testRoute("ensure-delete-final-only")
        val tab = testTab("ensure-delete-final-only", 0)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } returns true
        coEvery { tabsRepository.deleteOpenThreadTab(tab.id) } returns true
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(emptyList())
        coordinator.bind(backgroundScope)
        runCurrent()

        // --- Ensure followed by Delete without an intermediate snapshot ---
        val ensureJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureThreadTab(route)
        }
        runCurrent()
        coordinator.updateThreadSessionState(tab.id) { it }
        coordinator.updateThreadRuntimeState(tab.id) { it }
        val deleteJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.closeThreadTab(tab)
        }
        runCurrent()

        assertEquals(-1, ensureJob.await())
        assertFalse(deleteJob.isCompleted)
        assertTrue(coordinator.openThreadTabs.value.isEmpty())

        // --- Final absence snapshot and cleanup ---
        databaseFlow.emit(emptyList())
        runCurrent()
        deleteJob.await()

        assertTrue(coordinator.openThreadTabs.value.isEmpty())
        assertNull(coordinator.selectedThreadTabKey.value)
        assertFalse(coordinator.threadSessionStates.value.containsKey(tab.id.value))
        assertFalse(coordinator.threadRuntimeStates.value.containsKey(tab.id.value))
    }

    /** bound Thread bulk close が対象を即時非表示にし、canonical確認後にRepositoryを一度だけ呼ぶことを確認する。 */
    @Test
    fun boundBulkClose_excludesTargetsImmediatelyAndCallsRepositoryOnce() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val first = testTab("bulk-first", 0)
        val second = testTab("bulk-second", 1)
        val last = testTab("bulk-last", 2)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenThreadTabs(any()) } returns true
        databaseFlow.emit(listOf(first, second, last))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(backgroundScope)
        runCurrent()
        coordinator.selectThreadTab(second.id)
        val bulkJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.closeThreadTabs(listOf(second, last))
        }
        runCurrent()

        assertEquals(listOf(first), coordinator.openThreadTabs.value)
        assertEquals(first.id.value, coordinator.selectedThreadTabKey.value)
        assertFalse(bulkJob.isCompleted)
        databaseFlow.emit(listOf(first))
        runCurrent()
        bulkJob.await()

        coVerify(exactly = 1) { tabsRepository.deleteOpenThreadTabs(listOf(second.id, last.id)) }
        coVerify(exactly = 0) { tabsRepository.deleteOpenThreadTab(any()) }
        coordinator.close()
    }

    /** Thread bulk Repository失敗時に対象projectionをcanonicalへ戻し、部分削除を公開しないことを確認する。 */
    @Test
    fun boundBulkClose_failureRestoresCanonicalProjection() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val first = testTab("bulk-failure-first", 0)
        val second = testTab("bulk-failure-second", 1)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenThreadTabs(any()) } throws IllegalStateException("bulk failure")
        databaseFlow.emit(listOf(first, second))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(backgroundScope)
        runCurrent()
        val bulkJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.closeThreadTabs(listOf(first, second))
        }
        runCurrent()

        val failure = runCatching { bulkJob.await() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(first, second), coordinator.openThreadTabs.value)
        coordinator.close()
    }

    /** Thread bulkがcanonical確認するまで後続Ensureを開始しないbarrierであることを確認する。 */
    @Test
    fun boundBulkClose_blocksLaterEnsureUntilCanonicalConfirmation() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val target = testTab("bulk-barrier-target", 0)
        val ensureTab = testTab("bulk-barrier-ensure", 1)
        val writeRelease = CompletableDeferred<Boolean>()
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenThreadTabs(any()) } coAnswers { writeRelease.await() }
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } returns true
        databaseFlow.emit(listOf(target))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(backgroundScope)
        runCurrent()
        val bulkJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.closeThreadTabs(listOf(target))
        }
        runCurrent()
        val ensureJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureThreadTab(testRoute(ensureTab.id.value.substringAfterLast('/')))
        }
        runCurrent()

        coVerify(exactly = 0) { tabsRepository.ensureOpenThreadTab(any()) }
        writeRelease.complete(true)
        databaseFlow.emit(emptyList())
        runCurrent()
        bulkJob.await()
        coVerify(exactly = 1) { tabsRepository.ensureOpenThreadTab(any()) }
        assertFalse(ensureJob.isCompleted)

        databaseFlow.emit(listOf(ensureTab))
        runCurrent()
        ensureJob.await()
        coordinator.close()
    }

    /** Delete が Ensure に置き換えられた場合、古い Delete の cleanup を実行しない。 */
    @Test
    fun deleteThenEnsure_finalSnapshotOnlyPreservesSessionUntilEnsure() = runTest {
        // --- Dependencies and initial canonical snapshot ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val tab = testTab("delete-ensure-final-only", 0)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenThreadTab(tab.id) } returns true
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } returns true
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(tab))
        coordinator.bind(backgroundScope)
        runCurrent()
        coordinator.selectThreadTab(tab.id)
        coordinator.updateThreadSessionState(tab.id) { it }
        coordinator.updateThreadRuntimeState(tab.id) { it }

        // --- Delete followed by Ensure without an intermediate snapshot ---
        val deleteJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.closeThreadTab(tab)
        }
        runCurrent()
        val ensureJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureThreadTab(testRoute(tab.id.value.substringAfterLast('/')))
        }
        runCurrent()

        assertTrue(deleteJob.isCompleted)
        assertTrue(coordinator.threadSessionStates.value.containsKey(tab.id.value))
        assertTrue(coordinator.threadRuntimeStates.value.containsKey(tab.id.value))
        assertEquals(tab.id.value, coordinator.selectedThreadTabKey.value)
        assertFalse(ensureJob.isCompleted)

        // --- Final presence snapshot ---
        databaseFlow.emit(listOf(tab))
        runCurrent()
        assertEquals(0, ensureJob.await())
        assertEquals(listOf(tab.id), coordinator.openThreadTabs.value.map { it.id })
    }

    /** 後続 pin が失敗した場合、先行 pin は supersede されず自身の canonical 値で完了する。 */
    @Test
    fun failedSuccessorPin_doesNotSupersedePredecessor() = runTest {
        // --- Dependencies and controlled write failure ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val initialTab = testTab("pin-failed-successor", 0, isPinned = false)
        var writeCount = 0
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.setThreadTabPinned(initialTab.id, any()) } coAnswers {
            writeCount += 1
            if (writeCount == 2) throw IllegalStateException("successor failed")
            true
        }
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(initialTab))
        coordinator.bind(backgroundScope)
        runCurrent()

        // --- Predecessor success and successor failure ---
        val firstJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.togglePinThreadTab(initialTab.id)
        }
        val secondJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { coordinator.togglePinThreadTab(initialTab.id) }
        }
        runCurrent()

        assertFalse(firstJob.isCompleted)
        assertTrue(secondJob.await().isFailure)

        // --- Predecessor canonical snapshot ---
        databaseFlow.emit(listOf(initialTab.copy(isPinned = true)))
        runCurrent()
        firstJob.await()
        assertEquals(true, coordinator.openThreadTabs.value.single().isPinned)
        coVerify(exactly = 2) { tabsRepository.setThreadTabPinned(initialTab.id, any()) }
        coVerify(exactly = 0) { tabsRepository.replaceOpenThreadTabsForBulkOperation(any()) }
    }

    /** Thread が異なる rapid mutation は相互に waiter と projection を終端しない。 */
    @Test
    fun rapidMutationsOnDifferentThreadsRemainIndependent() = runTest {
        // --- Dependencies and initial canonical snapshot ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val first = testTab("independent-a", 0, isPinned = false)
        val second = testTab("independent-b", 1, isPinned = false)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.setThreadTabPinned(any(), any()) } returns true
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(first, second))
        coordinator.bind(backgroundScope)
        runCurrent()

        // --- Independent rapid mutations ---
        val firstJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.togglePinThreadTab(first.id)
        }
        val secondJobs = (0 until 2).map {
            backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.togglePinThreadTab(second.id)
            }
        }
        runCurrent()

        databaseFlow.emit(listOf(first, second.copy(isPinned = false)))
        runCurrent()
        secondJobs.forEach { it.await() }
        assertFalse(firstJob.isCompleted)
        assertTrue(coordinator.openThreadTabs.value.first { it.id == first.id }.isPinned)

        // --- Final canonical snapshot for the held Thread ---
        databaseFlow.emit(listOf(first.copy(isPinned = true), second.copy(isPinned = false)))
        runCurrent()
        firstJob.await()
        assertEquals(
            listOf(true, false),
            coordinator.openThreadTabs.value.map { it.isPinned },
        )
    }

    /** DB 失敗で保留中の投影を戻しても、同じ worker は後続の要求を停止しない。 */
    @Test
    fun failedMutation_restoresCanonicalStateAndContinuesQueue() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val existing = testTab("existing", 0)
        val failedRoute = testRoute("failed")
        val nextRoute = testRoute("next")
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(match { it.id.value.endsWith("failed") }) } throws IllegalStateException("write failed")
        coEvery { tabsRepository.ensureOpenThreadTab(match { it.id.value.endsWith("next") }) } returns true
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(existing))
        coordinator.bind(backgroundScope)
        runCurrent()

        val failedJob = backgroundScope.async { runCatching { coordinator.ensureThreadTab(failedRoute) } }
        runCurrent()
        assertTrue(failedJob.await().isFailure)
        assertEquals(listOf(existing.id), coordinator.openThreadTabs.value.map { it.id })

        val nextJob = backgroundScope.async { coordinator.ensureThreadTab(nextRoute) }
        runCurrent()
        val nextTab = testTab("next", 1)
        databaseFlow.emit(listOf(existing, nextTab))
        runCurrent()
        assertEquals(1, nextJob.await())
    }

    /** 準備がブロック中にキャンセルされた場合、キャンセル済みの Repository 更新を開始しない。 */
    @Test
    fun cancelledDuringReadiness_doesNotWriteAndWorkerProcessesNextIntent() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val existing = testTab("existing", 0)
        val cancelledRoute = testRoute("cancelled")
        val nextRoute = testRoute("next")
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } returns true
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(backgroundScope)
        runCurrent()

        val cancelledJob = backgroundScope.async { coordinator.ensureThreadTab(cancelledRoute) }
        runCurrent()
        cancelledJob.cancel()
        runCurrent()

        databaseFlow.emit(listOf(existing))
        runCurrent()
        coVerify(exactly = 0) {
            tabsRepository.ensureOpenThreadTab(match { it.id == testTab("cancelled", 0).id })
        }
        assertEquals(listOf(existing.id), coordinator.openThreadTabs.value.map { it.id })

        val nextJob = backgroundScope.async { coordinator.ensureThreadTab(nextRoute) }
        runCurrent()
        databaseFlow.emit(listOf(existing, testTab("next", 1)))
        runCurrent()
        assertEquals(1, nextJob.await())
        assertEquals(2, coordinator.openThreadTabs.value.size)
    }

    /** Repository が書き込み許可を待機中にキャンセルされた場合、その停止点まで到達する。 */
    @Test
    fun cancelledDuringRepositoryWait_stopsWriteAndWorkerProcessesNextIntent() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val permitWait = CompletableDeferred<Unit>()
        val repositoryEntered = CompletableDeferred<Unit>()
        val repositoryCancelled = CompletableDeferred<Unit>()
        val existing = testTab("existing", 0)
        val cancelledRoute = testRoute("cancelled")
        val nextRoute = testRoute("next")
        var invocationCount = 0
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } coAnswers {
            invocationCount += 1
            if (invocationCount == 1) {
                repositoryEntered.complete(Unit)
                try {
                    permitWait.await()
                } catch (cancellationException: CancellationException) {
                    repositoryCancelled.complete(Unit)
                    throw cancellationException
                }
            }
            true
        }
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(existing))
        coordinator.bind(backgroundScope)
        runCurrent()

        val cancelledJob = backgroundScope.async { coordinator.ensureThreadTab(cancelledRoute) }
        runCurrent()
        assertTrue(repositoryEntered.isCompleted)
        cancelledJob.cancel()
        runCurrent()

        assertFalse(repositoryCancelled.isCompleted)
        assertEquals(1, invocationCount)
        assertEquals(listOf(existing.id, testTab("cancelled", 1).id), coordinator.openThreadTabs.value.map { it.id })

        val nextJob = backgroundScope.async { coordinator.ensureThreadTab(nextRoute) }
        runCurrent()
        assertEquals(2, invocationCount)
        permitWait.complete(Unit)
        runCurrent()
        databaseFlow.emit(listOf(existing, testTab("cancelled", 1), testTab("next", 2)))
        runCurrent()
        assertEquals(2, nextJob.await())
        assertEquals(2, invocationCount)
    }

    /** トランザクション開始後のキャンセルでは、FIFO を進める前に Repository の後始末を完了する。 */
    @Test
    fun cancelledAfterTransactionStart_rollsBackAndWorkerProcessesNextIntent() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val transactionBarrier = CompletableDeferred<Unit>()
        val transactionStarted = CompletableDeferred<Unit>()
        val rollbackCompleted = CompletableDeferred<Unit>()
        val existing = testTab("existing", 0)
        val cancelledRoute = testRoute("cancelled")
        val nextRoute = testRoute("next")
        var invocationCount = 0
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } coAnswers {
            invocationCount += 1
            if (invocationCount == 1) {
                transactionStarted.complete(Unit)
                try {
                    transactionBarrier.await()
                } catch (cancellationException: CancellationException) {
                    rollbackCompleted.complete(Unit)
                    throw cancellationException
                }
            }
            true
        }
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(existing))
        coordinator.bind(backgroundScope)
        runCurrent()

        val cancelledJob = backgroundScope.async { coordinator.ensureThreadTab(cancelledRoute) }
        runCurrent()
        assertTrue(transactionStarted.isCompleted)
        cancelledJob.cancel()
        runCurrent()

        assertFalse(rollbackCompleted.isCompleted)
        assertEquals(1, invocationCount)
        assertEquals(listOf(existing.id, testTab("cancelled", 1).id), coordinator.openThreadTabs.value.map { it.id })

        val nextJob = backgroundScope.async { coordinator.ensureThreadTab(nextRoute) }
        runCurrent()
        assertEquals(2, invocationCount)
        transactionBarrier.complete(Unit)
        runCurrent()
        databaseFlow.emit(listOf(existing, testTab("cancelled", 1), testTab("next", 2)))
        runCurrent()
        assertEquals(2, nextJob.await())
        assertEquals(2, invocationCount)
    }

    /** 呼び出し元のキャンセルより先に Repository の結果が確定した場合、補償処理や重複書き込みを発生させない。 */
    @Test
    fun repositorySuccessBeforeCancellation_doesNotCompensateAndWorkerProcessesNextIntent() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val repositoryReturned = CompletableDeferred<Unit>()
        val existing = testTab("existing", 0)
        val cancelledRoute = testRoute("cancelled")
        val nextRoute = testRoute("next")
        var invocationCount = 0
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenThreadTab(any()) } coAnswers {
            invocationCount += 1
            if (invocationCount == 1) repositoryReturned.complete(Unit)
            true
        }
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(existing))
        coordinator.bind(backgroundScope)
        runCurrent()

        val cancelledJob = backgroundScope.async { coordinator.ensureThreadTab(cancelledRoute) }
        runCurrent()
        assertTrue(repositoryReturned.isCompleted)
        cancelledJob.cancel()
        runCurrent()

        assertEquals(1, invocationCount)
        coVerify(exactly = 1) {
            tabsRepository.ensureOpenThreadTab(match { it.id == testTab("cancelled", 0).id })
        }
        assertEquals(listOf(existing.id, testTab("cancelled", 1).id), coordinator.openThreadTabs.value.map { it.id })

        val nextJob = backgroundScope.async { coordinator.ensureThreadTab(nextRoute) }
        runCurrent()
        databaseFlow.emit(listOf(existing, testTab("cancelled", 1), testTab("next", 2)))
        runCurrent()
        assertEquals(2, nextJob.await())
        assertEquals(2, invocationCount)
    }

    /** 選択中 tab の pending delete 中は key を保持し、canonical confirmation 後に隣接へ補正する。 */
    @Test
    fun closeSelectedThreadTab_publishesPendingMissingUntilCanonicalConfirmation() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val first = testTab("pending-first", 0)
        val second = testTab("pending-second", 1)
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenThreadTab(first.id) } returns true

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        val dispatcher = StandardTestDispatcher(testScheduler)
        databaseFlow.emit(listOf(first, second))
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + dispatcher))
        runCurrent()
        assertTrue(coordinator.selectThreadTab(first.id))

        val closeJob = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.closeThreadTab(first)
        }
        runCurrent()

        assertEquals(
            TabSelectionResolution.PendingMissing(first.id.value),
            coordinator.threadPresentationState.value.selection,
        )
        assertEquals(listOf(second), coordinator.threadPresentationState.value.tabs)
        assertFalse(closeJob.isCompleted)

        databaseFlow.emit(listOf(first, second))
        runCurrent()
        assertEquals(TabSelectionResolution.PendingMissing(first.id.value), coordinator.threadPresentationState.value.selection)
        databaseFlow.emit(listOf(second))
        runCurrent()
        closeJob.await()

        assertEquals(
            TabSelectionResolution.Selected(second.id.value),
            coordinator.threadPresentationState.value.selection,
        )
        assertEquals(second.id.value, coordinator.selectedThreadTabKey.value)
    }

    /** rapid toggle が targeted write と独立した canonical 確認だけを使うことを検証する。 */
    private suspend fun TestScope.assertRapidPinToggles() {
        // --- 依存関係の制御 ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val initialTab = testTab("pin-sequence", 0, isPinned = false)
        val toggleCount = 4
        val requestedPins = mutableListOf<Boolean>()
        val writeReleases = List(toggleCount) { CompletableDeferred<Unit>() }
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.setThreadTabPinned(initialTab.id, any()) } coAnswers {
            val writeIndex = requestedPins.size
            requestedPins += (invocation.args[1] as Boolean)
            writeReleases[writeIndex].await()
            true
        }

        // --- 初期の正規状態とキュー内の intent ---
        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        val workerDispatcher = StandardTestDispatcher(testScheduler)
        databaseFlow.emit(listOf(initialTab))
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + workerDispatcher))
        runCurrent()
        val threadId = initialTab.id
        val toggleJobs = (0 until toggleCount).map {
            backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.togglePinThreadTab(threadId)
            }
        }
        runCurrent()

        // --- targeted write と canonical 確認 ---
        writeReleases.forEach { release -> release.complete(Unit) }
        runCurrent()
        assertEquals(toggleCount, requestedPins.size)

        // 各 waiter は自分の pin 値だけを確認し、同一 key の先行完了には依存しない。
        databaseFlow.emit(listOf(initialTab.copy(isPinned = true)))
        runCurrent()
        databaseFlow.emit(listOf(initialTab.copy(isPinned = false)))
        runCurrent()

        toggleJobs.forEach { it.await() }
        coVerify(exactly = toggleCount) { tabsRepository.setThreadTabPinned(initialTab.id, any()) }
        coVerify(exactly = 0) { tabsRepository.replaceOpenThreadTabsForBulkOperation(any()) }
    }

    /**
     * テスト用に依存を差し替えた `ThreadTabsCoordinator` を生成する。
     */
    private fun createCoordinator(
        tabsRepository: TabsRepository,
        bookmarkRepository: ThreadBookmarkRepository = mockk(relaxed = true),
    ): ThreadTabsCoordinator {
        return ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = bookmarkRepository,
            datRepository = mockk<DatRepository>(relaxed = true),
            threadStateRepository = mockk<ThreadStateRepository>(relaxed = true),
        )
    }

    /** テストデータ内で識別子が一意になる安定したテストタブを組み立てる。 */
    private fun testTab(
        key: String,
        sortOrder: Int,
        isPinned: Boolean = false,
        scrollIndex: Int = 0,
    ): ThreadTabInfo = ThreadTabInfo(
        id = com.websarva.wings.android.slevo.data.model.ThreadId.of("host", "board", key),
        title = key,
        boardName = "Board",
        boardUrl = "https://host/board/",
        boardId = 1L,
        firstVisibleItemIndex = scrollIndex,
        isPinned = isPinned,
    )

    /** [testTab] の識別子に対応する route を組み立てる。 */
    private fun testRoute(key: String): AppRoute.Thread = AppRoute.Thread(
        threadKey = key,
        boardUrl = "https://host/board/",
        boardName = "Board",
        threadTitle = key,
        boardId = 1L,
    )
}
