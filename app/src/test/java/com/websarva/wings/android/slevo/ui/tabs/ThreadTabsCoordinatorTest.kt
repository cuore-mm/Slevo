package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.DatRepository
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
import kotlinx.coroutines.Deferred
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

    /** 連続 2 回の pin toggle が元の値へ戻り、各確認後だけ次の write を開始する。 */
    @Test
    fun togglePinThreadTab_twoRapidTogglesAlternateAfterEachConfirmation() = runTest {
        assertRapidPinToggles(initialPinned = false, toggleCount = 2)
    }

    /** 連続 3 回の pin toggle が初期値を反転し、要求値を交互に確定する。 */
    @Test
    fun togglePinThreadTab_threeRapidTogglesAlternateAfterEachConfirmation() = runTest {
        assertRapidPinToggles(initialPinned = true, toggleCount = 3)
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

    /** Ensure の確認は古いメタデータを拒否し、共有 merge 結果だけを受け入れる。 */
    @Test
    fun ensureConfirmation_requiresMergedMetadataMatch() {
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
        val operation = ThreadTabPendingOperation.Ensure(expected)

        assertFalse(isThreadTabOperationConfirmed(listOf(current), operation))

        val merged = mergeThreadTabMetadata(current, expected)
        assertEquals("New title", merged.title)
        assertEquals("New board", merged.boardName)
        assertEquals(current.boardUrl, merged.boardUrl)
        assertEquals(43L, merged.boardId)
        assertEquals(140, merged.resCount)
        assertTrue(isThreadTabOperationConfirmed(listOf(merged), operation))
    }

    /** 対象メタデータを含まない先行 revision では pending と後続 FIFO を保持する。 */
    @Test
    fun ensureExistingTab_waitsForMatchingMetadataAfterUnrelatedRevision() = runTest {
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
        assertFalse(ensureJob.isCompleted)
        assertFalse(nextJob.isCompleted)
        assertEquals(listOf(currentTarget.id.value, next.id.value), writes)
        assertEquals(
            listOf(currentTarget.id, currentUnrelated.id, next.id),
            coordinator.openThreadTabs.value.map { it.id },
        )
        assertEquals("New title", pendingTarget.title)
        assertEquals("New board", pendingTarget.boardName)
        assertEquals(currentTarget.boardUrl, pendingTarget.boardUrl)
        assertEquals(43L, pendingTarget.boardId)
        assertEquals(140, pendingTarget.resCount)
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

        assertTrue(ensureJob.isCompleted)
        assertEquals(listOf(currentTarget.id.value, next.id.value), writes)
        assertFalse(nextJob.isCompleted)

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

    /** pin write 成功後の caller cancellation でも確認と後続 toggle を FIFO で継続する。 */
    @Test
    fun pinCommitBeforeCallerCancellation_keepsPendingUntilMatchingFlow() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val initialTab = testTab("cancel-after-commit", 0, isPinned = false)
        val requestedPins = mutableListOf<Boolean>()
        val firstWriteReturned = CompletableDeferred<Unit>()
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.setThreadTabPinned(initialTab.id, any()) } coAnswers {
            requestedPins += (invocation.args[1] as Boolean)
            if (requestedPins.size == 1) firstWriteReturned.complete(Unit)
            true
        }

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        val workerDispatcher = StandardTestDispatcher(testScheduler)
        databaseFlow.emit(listOf(initialTab))
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + workerDispatcher))
        runCurrent()

        val firstToggle = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.togglePinThreadTab(initialTab.id)
        }
        val secondToggle = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.togglePinThreadTab(initialTab.id)
        }
        runCurrent()
        firstWriteReturned.await()
        runCurrent()

        assertEquals(listOf(true, false), requestedPins)
        assertFalse(coordinator.openThreadTabs.value.single().isPinned)
        firstToggle.cancel()
        firstToggle.join()
        runCurrent()

        // 古い canonical 値でも pending pin は再投影され、後続 write は既に開始できる。
        databaseFlow.emit(listOf(initialTab.copy(isPinned = false)))
        runCurrent()
        assertEquals(listOf(true, false), requestedPins)
        assertFalse(coordinator.openThreadTabs.value.single().isPinned)
        assertFalse(secondToggle.isCompleted)

        // matching canonical 値を確認すると、先行 pending だけが terminal になる。
        databaseFlow.emit(listOf(initialTab.copy(isPinned = true)))
        runCurrent()
        assertEquals(listOf(true, false), requestedPins)
        assertFalse(secondToggle.isCompleted)

        databaseFlow.emit(listOf(initialTab.copy(isPinned = false)))
        runCurrent()
        secondToggle.await()

        assertTrue(firstToggle.isCancelled)
        assertEquals(listOf(true, false), requestedPins)
        assertFalse(coordinator.openThreadTabs.value.single().isPinned)
    }

    /** Repository の正常結果発行と caller cancellation が同じ mock 境界で競合しても commit を保持する。 */
    @Test
    fun pinCommitAndSynchronousCallerCancellation_keepsCommittedResultForReconciliation() = runTest {
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val initialTab = testTab("synchronous-cancel", 0, isPinned = false)
        val requestedPins = mutableListOf<Boolean>()
        val commitRecorded = CompletableDeferred<Unit>()
        lateinit var firstToggle: Deferred<Unit>
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.setThreadTabPinned(initialTab.id, any()) } coAnswers {
            requestedPins += (invocation.args[1] as Boolean)
            if (requestedPins.size == 1) {
                commitRecorded.complete(Unit)
                // 正常値を返す同じ coAnswers 内で caller を同期的にキャンセルする。
                firstToggle.cancel()
            }
            true
        }

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        val workerDispatcher = StandardTestDispatcher(testScheduler)
        databaseFlow.emit(listOf(initialTab))
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + workerDispatcher))
        runCurrent()

        firstToggle = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.togglePinThreadTab(initialTab.id)
        }
        val secondToggle = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.togglePinThreadTab(initialTab.id)
        }
        runCurrent()

        assertTrue(commitRecorded.isCompleted)
        assertTrue(firstToggle.isCancelled)
        assertEquals(listOf(true, false), requestedPins)
        assertFalse(coordinator.openThreadTabs.value.single().isPinned)
        assertFalse(secondToggle.isCompleted)

        // 古い canonical false でも commit 済み pending pin と後続 write を保持する。
        databaseFlow.emit(listOf(initialTab.copy(isPinned = false)))
        runCurrent()
        assertEquals(listOf(true, false), requestedPins)
        assertFalse(coordinator.openThreadTabs.value.single().isPinned)
        assertFalse(secondToggle.isCompleted)

        // matching true の確認後、後続 false は既に受理済みのまま残る。
        databaseFlow.emit(listOf(initialTab.copy(isPinned = true)))
        runCurrent()
        assertEquals(listOf(true, false), requestedPins)
        assertFalse(secondToggle.isCompleted)

        databaseFlow.emit(listOf(initialTab.copy(isPinned = false)))
        runCurrent()
        secondToggle.await()

        assertEquals(listOf(true, false), requestedPins)
        assertFalse(coordinator.openThreadTabs.value.single().isPinned)
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

    /**
     * 正規状態の通知と Repository の完了を制御し、切り替えの FIFO 動作を検証する。
     *
     * undispatched caller を使うことで、worker が最初の要求を受け取る前にすべての切り替え操作
     * をキューへ追加する。後続の書き込みが早すぎる場合に検出できるよう、各 Repository barrier
     * は対応する Room 通知より先に解放する。
     */
    private suspend fun TestScope.assertRapidPinToggles(
        initialPinned: Boolean,
        toggleCount: Int,
    ) {
        // --- 依存関係の制御 ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val initialTab = testTab("pin-sequence", 0, isPinned = initialPinned)
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

        // --- 順次書き込みと確認 ---
        val expectedPins = (1..toggleCount).map { step ->
            if (step % 2 == 1) !initialPinned else initialPinned
        }
        // matching Flow confirmation は後続 write の開始条件ではない。
        assertEquals(expectedPins, requestedPins)
        expectedPins.forEachIndexed { index, expectedPin ->
            writeReleases[index].complete(Unit)
            runCurrent()
            assertEquals(expectedPins, requestedPins)
            assertFalse(toggleJobs[index].isCompleted)

            databaseFlow.emit(listOf(initialTab.copy(isPinned = expectedPin)))
            runCurrent()
            assertTrue(toggleJobs[index].isCompleted)
            if (index + 1 < toggleCount) {
                assertEquals(expectedPins[index + 1], requestedPins[index + 1])
            }
        }

        toggleJobs.forEach { it.await() }
        assertEquals(expectedPins, requestedPins)
        assertEquals(expectedPins.last(), coordinator.openThreadTabs.value.single().isPinned)
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
