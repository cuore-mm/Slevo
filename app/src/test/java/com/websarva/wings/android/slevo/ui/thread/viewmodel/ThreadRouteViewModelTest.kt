package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.PendingOwnPostRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.data.util.ThreadNewResCalculator
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.PopupContentType
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ThreadRouteViewModel] の軽量回帰テスト。
 *
 * 直接合成化後の lazy load、reload、自動スクロールの委譲条件とレスグループ境界を検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ThreadRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** 既知のレス境界を優先し、未取得時にはタブの件数へフォールバックする。 */
    @Test
    fun resolvePendingBaseResCount_prefersCapturedBoundaryAfterRefresh() {
        assertEquals(10, resolvePendingBaseResCount(10, loadedResCount = 11, tabResCount = 11))
        assertEquals(11, resolvePendingBaseResCount(null, loadedResCount = 11, tabResCount = 10))
        assertEquals(10, resolvePendingBaseResCount(null, loadedResCount = null, tabResCount = 10))
    }

    /** 表示可能レス番号を順序維持で重複除去し、NG・範囲外番号を除く。 */
    @Test
    fun resolveRenderablePostNumbersKeepsOrderAndRemovesNgDuplicatesAndMissingPosts() {
        assertEquals(
            listOf(3, 1),
            resolveRenderablePostNumbers(
                postNumbers = listOf(3, 2, 3, 0, 5, 1),
                postCount = 4,
                ngPostNumbers = setOf(2),
            ),
        )
        assertEquals(emptyList<Int>(), resolveRenderablePostNumbers(listOf(1), 0, emptySet()))
    }

    /** 同じタブkeyから同一のUiState Flowを返す。 */
    @Test
    fun uiStateFor_sameKeyReusesCachedFlow() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(listOf(threadTab(threadId, "title")), selectedTabKey = threadId.value)
        val viewModel = dependencies.createViewModel()

        val first = viewModel.uiStateFor(threadId.value)
        val second = viewModel.uiStateFor(threadId.value)

        assertSame(first, second)
    }

    /** reload要求をスレッド本文取得ユースケースへ渡す。 */
    @Test
    fun reloadThread_callsThreadContentLoadUseCase() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(listOf(threadTab(threadId, "title")), selectedTabKey = threadId.value)
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            dependencies.threadContentLoadUseCase.load(any(), "111", any(), any(), any(), any())
        }
    }

    /** 未選択タブの最下端到達では自動更新を実行しない。 */
    @Test
    fun onAutoScrollReachedBottom_doesNotUpdateUnselectedTab() = runTest {
        val firstId = ThreadId.of("example.com", "test", "111")
        val secondId = ThreadId.of("example.com", "test", "222")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(firstId, "first"), threadTab(secondId, "second")),
            selectedTabKey = firstId.value,
            initialSessionStates = mapOf(
                firstId.value to ThreadSessionState(isAutoScroll = true),
                secondId.value to ThreadSessionState(isAutoScroll = true),
            ),
        )
        val viewModel = dependencies.createViewModel()

        viewModel.onAutoScrollReachedBottom(secondId.value)
        advanceUntilIdle()

        coVerify(exactly = 0) { dependencies.threadContentLoadUseCase.load(any(), "222", any()) }
    }

    /** スレッドタブを閉じたとき実行中の読み込みをキャンセルする。 */
    @Test
    fun closingThreadTab_cancelsInFlightLoad() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            suspendLoad = true,
        )
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        dependencies.openTabs.value = emptyList()
        advanceUntilIdle()

        assertTrue(dependencies.threadLoadCancelled.value)
    }

    /** ViewModel破棄時にroute jobを解放し、タブsession状態を保持する。 */
    @Test
    fun onCleared_keepsSessionStateButCancelsRouteJobs() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            suspendLoad = true,
        )
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        invokeOnCleared(viewModel)
        advanceUntilIdle()

        assertTrue(dependencies.threadLoadCancelled.value)
        assertEquals(1, dependencies.sessionStates.value.size)
    }

    /** 読み込み失敗を画面stateのtoastへ反映する。 */
    @Test
    fun reloadThread_loadFailure_setsPendingToast() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            loadReturnsNull = true,
        )
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()

        assertEquals(com.websarva.wings.android.slevo.R.string.thread_load_failed, dependencies.sessionStates.value[threadId.value]?.pendingToastResId)
    }

    /** 読み込み成功後にloading状態を解除し、失敗toastを設定しない。 */
    @Test
    fun reloadThread_successfulLoad_keepsSuccessfulLoadState() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
        )
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()

        val session = dependencies.sessionStates.value[threadId.value]
        assertEquals(null, session?.pendingToastResId)
        assertEquals(false, session?.isLoading)
    }

    /** 初回取得で最終既読位置に対応する新着グループを復元する。 */
    @Test
    fun initialLoad_restoresUnreadGroupFromLastReadPosition() {
        val state = updateThreadPostGroups(
            previousGroups = emptyList(),
            previousResCount = 0,
            posts = posts(110),
            initialUnreadStartResNo = 101,
            isInitialLoad = true,
        )

        assertEquals(
            listOf(
                ThreadPostGroup(startResNo = 1, endResNo = 100, prevResCount = 0),
                ThreadPostGroup(startResNo = 101, endResNo = 110, prevResCount = 100),
            ),
            state.groups,
        )
        assertEquals(1, state.latestArrivalGroupIndex)

        val visible = ThreadVisiblePostsUseCase().buildVisibleRows(
            posts = posts(110),
            groups = state.groups,
            sortType = com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = state.latestArrivalGroupIndex,
            searchQuery = "",
            ngPostNumbers = emptySet(),
            visibleReplySourceMap = emptyMap(),
        )
        assertEquals(100, visible.firstAfterIndex)
    }

    /** first-new位置がない場合も既読位置から新着境界を復元する。 */
    @Test
    fun initialLoad_restoresUnreadGroupWhenFirstNewResNoIsMissing() {
        val readState = ThreadReadState(lastReadResNo = 100, firstNewResNo = null)
        val initialUnreadStartResNo = (ThreadNewResCalculator.calculate(110, readState) > 0)
            .let { hasUnread -> if (hasUnread) readState.lastReadResNo + 1 else null }

        val state = updateThreadPostGroups(
            previousGroups = emptyList(),
            previousResCount = 0,
            posts = posts(110),
            initialUnreadStartResNo = initialUnreadStartResNo,
            isInitialLoad = true,
        )

        assertEquals(1, state.latestArrivalGroupIndex)
        assertEquals(101, state.groups[1].startResNo)
    }

    /** 有効な未読境界がない初回取得では新着バーを表示しない。 */
    @Test
    fun initialLoad_doesNotShowArrivalBarWithoutValidUnreadBoundary() {
        val states = listOf(
            updateThreadPostGroups(emptyList(), 0, posts(110), null, true),
            updateThreadPostGroups(emptyList(), 0, posts(110), null, true),
            updateThreadPostGroups(emptyList(), 0, posts(90), 101, true),
        )

        states.forEach { state ->
            assertEquals(null, state.latestArrivalGroupIndex)
            val visible = ThreadVisiblePostsUseCase().buildVisibleRows(
                posts = posts(state.lastLoadedResCount),
                groups = state.groups,
                sortType = com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType.NUMBER,
                treeOrder = emptyList(),
                treeDepthMap = emptyMap(),
                treeRootMap = emptyMap(),
                latestArrivalGroupIndex = state.latestArrivalGroupIndex,
                searchQuery = "",
                ngPostNumbers = emptySet(),
                visibleReplySourceMap = emptyMap(),
            )
            assertEquals(-1, visible.firstAfterIndex)
        }
    }

    /** 未読境界がレス番号1なら全取得レスを新着グループにする。 */
    @Test
    fun initialLoad_treatsAllResponsesAsUnreadWhenBoundaryIsOne() {
        val state = updateThreadPostGroups(emptyList(), 0, posts(10), 1, true)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 10, prevResCount = 0)),
            state.groups,
        )
        assertEquals(0, state.latestArrivalGroupIndex)
    }

    /** 既読状態更新後も初回に確定した未読境界を維持する。 */
    @Test
    fun initialLoad_keepsBoundaryAfterReadStateChanges() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val afterRead = initial.copy()

        assertEquals(initial.groups, afterRead.groups)
        assertEquals(initial.latestArrivalGroupIndex, afterRead.latestArrivalGroupIndex)
    }

    /** 再取得で追加されたグループへ新着バーを移し、変化なしで消去する。 */
    @Test
    fun reload_movesArrivalBarToNewestAppendedGroupAndClearsOnNoChange() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val appended = updateThreadPostGroups(initial.groups, 110, posts(115), 101, false)
        val unchanged = updateThreadPostGroups(appended.groups, 115, posts(115), 101, false)

        assertEquals(2, appended.latestArrivalGroupIndex)
        assertEquals(110, appended.groups.last().startResNo - 1)
        assertEquals(null, unchanged.latestArrivalGroupIndex)
    }

    /** 空の再取得後に復旧しても古い初回未読境界を再利用しない。 */
    @Test
    fun recoveryAfterEmptyReload_doesNotReuseInitialBoundary() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val empty = updateThreadPostGroups(initial.groups, 110, emptyList(), 101, false)
        val recovered = updateThreadPostGroups(empty.groups, 0, posts(110), 101, false)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 110, prevResCount = 0)),
            recovered.groups,
        )
        assertEquals(null, recovered.latestArrivalGroupIndex)
        assertEquals(-1, firstAfterIndex(posts(110), recovered))
    }

    /** 空の初回取得後の復旧時に未読境界を再利用しない。 */
    @Test
    fun recoveryAfterInitialEmptyLoad_doesNotReuseInitialBoundary() {
        val empty = updateThreadPostGroups(emptyList(), 0, emptyList(), 101, true)
        val recovered = updateThreadPostGroups(empty.groups, 0, posts(110), 101, false)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 110, prevResCount = 0)),
            recovered.groups,
        )
        assertEquals(null, recovered.latestArrivalGroupIndex)
        assertEquals(-1, firstAfterIndex(posts(110), recovered))
    }

    /** レス数が減少して復旧した場合も初回未読境界を再利用しない。 */
    @Test
    fun recoveryAfterNonZeroCountDecrease_doesNotReuseInitialBoundary() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val decreased = updateThreadPostGroups(initial.groups, 110, posts(90), 101, false)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 90, prevResCount = 0)),
            decreased.groups,
        )
        assertEquals(null, decreased.latestArrivalGroupIndex)
        assertEquals(-1, firstAfterIndex(posts(90), decreased))
    }

    /** キャッシュ済み新着件数が0でも、初回実取得レス数から未読境界を復元する。 */
    /** cached件数が0でも実取得件数から既読・未読境界を決める。 */
    @Test
    fun initialLoad_usesFetchedCountWhenCachedNewResCountIsZero() = runTest {
        val tab = threadTab(ThreadId.of("example.com", "test", "stale-cache"), "title").copy(
            hasHistory = true,
            lastReadResNo = 100,
            newResCount = 0,
            resCount = 100,
        )
        val state = updateThreadPostGroups(
            previousGroups = emptyList(),
            previousResCount = 0,
            posts = posts(110),
            initialUnreadStartResNo = deriveInitialUnreadStartResNo(tab),
            isInitialLoad = true,
        )

        assertEquals(
            listOf(
                ThreadPostGroup(startResNo = 1, endResNo = 100, prevResCount = 0),
                ThreadPostGroup(startResNo = 101, endResNo = 110, prevResCount = 100),
            ),
            state.groups,
        )
        assertEquals(1, state.latestArrivalGroupIndex)
        assertEquals(100, firstAfterIndex(posts(110), state))
    }

    /** 未訪問スレッドでは実取得レス数が増えていても初回未読境界を作らない。 */
    /** 未訪問スレッドでは取得レス数が増えても未読境界を作らない。 */
    @Test
    fun initialLoad_doesNotCreateBoundaryForUnvisitedThreadWhenFetchedCountIncreases() = runTest {
        val tab = threadTab(ThreadId.of("example.com", "test", "unvisited"), "title").copy(
            hasHistory = false,
            lastReadResNo = 100,
            newResCount = 0,
            resCount = 100,
        )
        val state = updateThreadPostGroups(
            previousGroups = emptyList(),
            previousResCount = 0,
            posts = posts(110),
            initialUnreadStartResNo = deriveInitialUnreadStartResNo(tab),
            isInitialLoad = true,
        )

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 110, prevResCount = 0)),
            state.groups,
        )
        assertEquals(null, state.latestArrivalGroupIndex)
        assertEquals(-1, firstAfterIndex(posts(110), state))
    }

    /** 入力中compositionを保持したまま検索文字列を更新する。 */
    @Test
    fun updateSearchInput_preservesComposition() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(listOf(threadTab(threadId, "title")), selectedTabKey = threadId.value)
        val viewModel = dependencies.createViewModel()
        val input = TextFieldValue(text = "かな", selection = TextRange(2), composition = TextRange(0, 2))

        viewModel.updateSearchInput(threadId.value, input)

        assertEquals(input, dependencies.sessionStates.value[threadId.value]?.searchInputValue)
        assertEquals("かな", dependencies.sessionStates.value[threadId.value]?.searchQuery)
    }

    /** 検索終了時に入力値を消去してタブスワイプを再有効化する。 */
    @Test
    fun closeSearch_clearsSearchAndRestoresSwipe() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            initialSessionStates = mapOf(
                threadId.value to ThreadSessionState(
                    isSearchMode = true,
                    searchInputValue = TextFieldValue("query"),
                    isTabSwipeEnabled = false,
                )
            )
        )
        val viewModel = dependencies.createViewModel()

        viewModel.closeSearch(threadId.value)

        val state = dependencies.sessionStates.value[threadId.value]!!
        assertEquals(TextFieldValue(""), state.searchInputValue)
        assertEquals(false, state.isSearchMode)
        assertEquals(true, state.isTabSwipeEnabled)
    }

    /** 同じ板の複数タブは名無し名を一度だけ取得し、各 UiState に保持する。 */
    @Test
    fun initializeTabs_fetchesBoardDefaultNameOncePerBoard() = runTest {
        val firstId = ThreadId.of("example.com", "test", "111")
        val secondId = ThreadId.of("example.com", "test", "222")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(firstId, "first"), threadTab(secondId, "second")),
            selectedTabKey = firstId.value,
        )
        coEvery { dependencies.boardRepository.fetchBoardNoname(any()) } returns "名無し"
        val viewModel = dependencies.createViewModel()
        val firstState = viewModel.uiStateFor(firstId.value)
        val secondState = viewModel.uiStateFor(secondId.value)
        val firstJob = backgroundScope.launch { firstState.collect {} }
        val secondJob = backgroundScope.launch { secondState.collect {} }

        advanceUntilIdle()

        coVerify(exactly = 1) {
            dependencies.boardRepository.fetchBoardNoname("https://example.com/test/SETTING.TXT")
        }
        assertEquals("名無し", firstState.value.boardInfo.noname)
        assertEquals("名無し", secondState.value.boardInfo.noname)
        firstJob.cancel()
        secondJob.cancel()
    }

    /** NG変更と投稿追加・減少で返信番号を詰めずに再計算し、NG変更では再取得しない。 */
    @Test
    fun replySourceStateTracksNgAndPostChangesWithoutRenumbering() = runTest {
        // --- Fixture and initial state ---
        val threadId = ThreadId.of("example.com", "test", "111")
        val ngRules = MutableStateFlow(emptyList<NgEntity>())
        val initialPosts = listOf(
            threadPost(1, "root"),
            threadPost(2, ">>1 reply two"),
            threadPost(3, "blocked >>1 reply three"),
            threadPost(4, ">>1 reply four"),
        )
        val appendedPosts = initialPosts + threadPost(5, ">>1 reply five")
        val shortenedPosts = initialPosts.take(2)
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            ngRules = ngRules,
            loadResults = listOf(
                contentLoadResult(initialPosts),
                contentLoadResult(appendedPosts),
                contentLoadResult(shortenedPosts),
            ),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()

        coVerify(exactly = 1) {
            dependencies.threadContentLoadUseCase.load(any(), "111", any(), any(), any(), any())
        }
        assertEquals(4, uiState.value.posts?.size)
        assertEquals(mapOf(1 to listOf(2, 3, 4)), uiState.value.visibleReplySourceMap)
        val initialPreviewMap = uiState.value.replyPreviewByPostNumber
        assertTrue(initialPreviewMap.isNotEmpty())

        // --- NG changes without reload ---
        ngRules.value = listOf(
            NgEntity(pattern = "blocked", isRegex = false, boardId = 1L, type = NgType.WORD),
        )
        advanceUntilIdle()
        assertEquals(mapOf(1 to listOf(2, 4)), uiState.value.visibleReplySourceMap)
        assertEquals(4, uiState.value.posts?.size)
        assertEquals(2, uiState.value.replyCounts.first())
        val ngPreviewMap = uiState.value.replyPreviewByPostNumber
        assertNotSame(initialPreviewMap, ngPreviewMap)
        coVerify(exactly = 1) {
            dependencies.threadContentLoadUseCase.load(any(), "111", any(), any(), any(), any())
        }

        // --- Unrelated UI updates reuse derived previews ---
        viewModel.updateSearchInput(threadId.value, TextFieldValue("root"))
        advanceUntilIdle()
        assertSame(ngPreviewMap, uiState.value.replyPreviewByPostNumber)
        dependencies.openTabs.value = dependencies.openTabs.value.map { tab ->
            tab.copy(firstVisibleItemIndex = 12, firstVisibleItemScrollOffset = 24)
        }
        advanceUntilIdle()
        assertSame(ngPreviewMap, uiState.value.replyPreviewByPostNumber)
        viewModel.updateSearchInput(threadId.value, TextFieldValue(""))
        advanceUntilIdle()

        ngRules.value = emptyList()
        advanceUntilIdle()
        assertEquals(mapOf(1 to listOf(2, 3, 4)), uiState.value.visibleReplySourceMap)
        assertEquals(3, uiState.value.replyCounts.first())
        assertNotSame(ngPreviewMap, uiState.value.replyPreviewByPostNumber)

        // --- Added and shortened post sets ---
        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        assertEquals(mapOf(1 to listOf(2, 3, 4, 5)), uiState.value.visibleReplySourceMap)

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        assertEquals(2, uiState.value.posts?.size)
        assertEquals(mapOf(1 to listOf(2)), uiState.value.visibleReplySourceMap)
        assertEquals(2, uiState.value.replyCounts.size)
        coVerify(exactly = 3) {
            dependencies.threadContentLoadUseCase.load(any(), "111", any(), any(), any(), any())
        }
    }

    /** 複数参照ポップアップは表示可能番号だけを順序維持で開き、同一内容を連続追加しない。 */
    @Test
    fun addPopupForReplyNumbersResolvesVisiblePostsAndSuppressesDuplicates() = runTest {
        // --- Fixture and NG state ---
        val threadId = ThreadId.of("example.com", "test", "111")
        val ngRules = MutableStateFlow(
            listOf(NgEntity(pattern = "blocked", isRegex = false, boardId = 1L, type = NgType.WORD)),
        )
        val posts = (1..5).map { number ->
            threadPost(number, if (number == 3) "blocked post" else "post $number")
        }
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            ngRules = ngRules,
            loadResults = listOf(contentLoadResult(posts)),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()
        assertEquals(5, uiState.value.posts?.size)
        assertEquals(setOf(3), uiState.value.ngPostNumbers)

        // --- Visible target resolution and duplicate suppression ---
        val firstOffset = IntOffset(12, 24)
        viewModel.addPopupForReplyNumbers(
            tabKey = threadId.value,
            baseOffset = firstOffset,
            replyNumbers = listOf(5, 3, 4, 5, 0, 9),
        )
        val firstPopup = dependencies.sessionStates.value.getValue(threadId.value).popupStack.single()
        assertEquals(listOf(5, 4), firstPopup.postNumbers)
        assertEquals(firstOffset, firstPopup.offset)

        viewModel.addPopupForReplyNumbers(
            tabKey = threadId.value,
            baseOffset = IntOffset(100, 200),
            replyNumbers = listOf(5, 4),
        )
        viewModel.addPopupForReplyNumbers(
            tabKey = threadId.value,
            baseOffset = IntOffset.Zero,
            replyNumbers = listOf(3, 0, 9),
        )
        viewModel.addPopupForReplyNumbers(
            tabKey = threadId.value,
            baseOffset = IntOffset.Zero,
            replyNumbers = emptyList(),
        )
        assertEquals(1, dependencies.sessionStates.value.getValue(threadId.value).popupStack.size)

        viewModel.addPopupForReplyNumber(threadId.value, IntOffset.Zero, 1)
        assertEquals(
            listOf(listOf(5, 4), listOf(1)),
            dependencies.sessionStates.value.getValue(threadId.value).popupStack.map { it.postNumbers },
        )
    }

    /** 返信先一覧は取得済みNG番号だけを保持する。 */
    @Test
    fun addPopupForReplyTargetsShowsAcquiredNgCandidatesOnly() = runTest {
        val threadId = ThreadId.of("example.com", "test", "reply-targets")
        val ngRules = MutableStateFlow(
            listOf(NgEntity(pattern = "blocked", isRegex = false, boardId = 1L, type = NgType.WORD)),
        )
        val posts = listOf(
            threadPost(1, "blocked candidate"),
            threadPost(2, ">>1 >>99"),
        )
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            ngRules = ngRules,
            loadResults = listOf(contentLoadResult(posts)),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()

        viewModel.addPopupForReplyTargetNumbers(
            tabKey = threadId.value,
            baseOffset = IntOffset(10, 20),
            replyNumbers = listOf(1),
        )
        advanceUntilIdle()

        val popup = dependencies.sessionStates.value.getValue(threadId.value).popupStack.single()
        assertEquals(listOf(1), popup.postNumbers)
        assertEquals(PopupContentType.REPLY_TARGETS, popup.contentType)
        assertEquals(listOf(1), uiState.value.popupStack.single().postNumbers)
        assertEquals(setOf(1), uiState.value.ngPostNumbers)
    }

    /** 参照先が未取得番号だけの場合は返信先一覧を作らない。 */
    @Test
    fun addPopupForReplyTargetsDoesNotOpenWithoutAcquiredCandidates() = runTest {
        val threadId = ThreadId.of("example.com", "test", "reply-targets-unavailable-only")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            loadResults = listOf(contentLoadResult(listOf(threadPost(1, "body")))),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()

        viewModel.addPopupForReplyTargetNumbers(
            tabKey = threadId.value,
            baseOffset = IntOffset.Zero,
            replyNumbers = listOf(2, 99),
        )
        advanceUntilIdle()

        assertTrue(uiState.value.popupStack.isEmpty())
        assertTrue(dependencies.sessionStates.value.getValue(threadId.value).popupStack.isEmpty())
    }

    /** 再取得で投稿数が減り、残り候補がNG・未取得になっても一覧番号を維持する。 */
    @Test
    fun replyTargetPopupSurvivesPostCountDecreaseAndResolvesLatestNgState() = runTest {
        val threadId = ThreadId.of("example.com", "test", "reply-target-refresh")
        val ngRules = MutableStateFlow(
            listOf(NgEntity(pattern = "blocked", isRegex = false, boardId = 1L, type = NgType.WORD)),
        )
        val initialPosts = listOf(
            threadPost(1, "blocked target"),
            threadPost(2, ">>1"),
        )
        val refreshedPosts = listOf(threadPost(1, "available target"))
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            ngRules = ngRules,
            loadResults = listOf(contentLoadResult(initialPosts), contentLoadResult(refreshedPosts)),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()

        viewModel.addPopupForReplyTargetNumbers(
            threadId.value,
            IntOffset.Zero,
            listOf(1, 2),
        )
        advanceUntilIdle()
        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()

        assertEquals(1, uiState.value.posts?.size)
        assertTrue(uiState.value.ngPostNumbers.isEmpty())
        val popup = uiState.value.popupStack.single { it.contentType == PopupContentType.REPLY_TARGETS }
        assertEquals(listOf(1, 2), popup.postNumbers)
        assertEquals(1, dependencies.sessionStates.value.getValue(threadId.value).popupStack.size)
    }

    /** 更新後に候補がすべて未取得となった返信先一覧を閉じる。 */
    @Test
    fun replyTargetPopupClosesWhenAllCandidatesBecomeUnavailable() = runTest {
        val threadId = ThreadId.of("example.com", "test", "reply-target-close-after-refresh")
        val initialPosts = listOf(
            threadPost(1, "first post"),
            threadPost(2, "second post"),
        )
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            loadResults = listOf(
                contentLoadResult(initialPosts),
                contentLoadResult(listOf(threadPost(1, "remaining post"))),
            ),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()

        viewModel.addPopupForReplyTargetNumbers(
            tabKey = threadId.value,
            baseOffset = IntOffset.Zero,
            replyNumbers = listOf(2),
        )
        advanceUntilIdle()
        assertEquals(1, uiState.value.popupStack.size)

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()

        assertTrue(uiState.value.popupStack.isEmpty())
        assertTrue(dependencies.sessionStates.value.getValue(threadId.value).popupStack.isEmpty())
    }

    /** NG・追加取得の変化を反映しつつ、行候補順と残数を維持する。 */
    @Test
    fun replyTargetCandidatesUpdateAcrossNgChangesAndAdditionalPosts() = runTest {
        val threadId = ThreadId.of("example.com", "test", "reply-target-candidates")
        val ngRules = MutableStateFlow(
            listOf(NgEntity(pattern = "blocked", isRegex = false, boardId = 1L, type = NgType.WORD)),
        )
        val initialPosts = listOf(
            threadPost(1, "blocked target"),
            threadPost(2, ">>1 >>3"),
        )
        val postsAfterAdditionalFetch = initialPosts + threadPost(3, "new target")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            ngRules = ngRules,
            loadResults = listOf(
                contentLoadResult(initialPosts),
                contentLoadResult(postsAfterAdditionalFetch),
            ),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()

        val initialTarget = uiState.value.replyPreviewByPostNumber.getValue(2).replyTargets.single()
        assertEquals(listOf(1), initialTarget.referencedNumbers)
        assertEquals(ReplyTargetCandidateStatus.NG, initialTarget.candidates[0].status)
        assertEquals(listOf(ReplyTargetUnavailableRange(3, 3)), initialTarget.unavailableRanges)
        assertEquals(1, initialTarget.selectedCandidate?.postNumber)
        assertEquals(0, initialTarget.overflowCount)

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        val afterFetch = uiState.value.replyPreviewByPostNumber.getValue(2).replyTargets.single()
        assertEquals(listOf(1, 3), afterFetch.referencedNumbers)
        assertEquals(ReplyTargetCandidateStatus.AVAILABLE, afterFetch.candidates[1].status)
        assertEquals(emptyList<ReplyTargetUnavailableRange>(), afterFetch.unavailableRanges)
        assertEquals(3, afterFetch.selectedCandidate?.postNumber)
        assertEquals(1, afterFetch.overflowCount)

        ngRules.value = emptyList()
        advanceUntilIdle()
        val afterNgRemoval = uiState.value.replyPreviewByPostNumber.getValue(2).replyTargets.single()
        assertEquals(listOf(1, 3), afterNgRemoval.referencedNumbers)
        assertEquals(ReplyTargetCandidateStatus.AVAILABLE, afterNgRemoval.candidates[0].status)
        assertEquals(1, afterNgRemoval.selectedCandidate?.postNumber)
        assertEquals(1, afterNgRemoval.overflowCount)
    }

    /** NG開示をスレッド内で共有し、popup close後も保持して別スレッドから分離する。 */
    @Test
    fun replyTargetDisclosureIsSharedWithinThreadAndResetAfterNgRulesChange() = runTest {
        // --- Thread and NG fixtures ---
        val threadId = ThreadId.of("example.com", "test", "reply-target-disclosure")
        val otherThreadId = ThreadId.of("example.com", "test", "other-reply-target-disclosure")
        val ngRules = MutableStateFlow(
            listOf(NgEntity(pattern = "blocked", isRegex = false, boardId = 1L, type = NgType.WORD)),
        )
        val posts = listOf(
            threadPost(1, "blocked candidate"),
            threadPost(2, ">>1 >>99"),
        )
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title"), threadTab(otherThreadId, "other title")),
            selectedTabKey = threadId.value,
            ngRules = ngRules,
            loadResults = listOf(contentLoadResult(posts), contentLoadResult(posts)),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        val otherUiState = viewModel.uiStateFor(otherThreadId.value)
        backgroundScope.launch { uiState.collect {} }
        backgroundScope.launch { otherUiState.collect {} }
        advanceUntilIdle()

        // --- Disclosure state shared by inline and popup displays ---
        viewModel.setReplyTargetDisclosure(threadId.value, postNumber = 1, revealed = true)
        advanceUntilIdle()
        assertEquals(setOf(1), uiState.value.revealedReplyTargetNumbers)
        assertTrue(otherUiState.value.revealedReplyTargetNumbers.isEmpty())

        viewModel.addPopupForReplyTargetNumbers(
            threadId.value,
            IntOffset.Zero,
            listOf(1),
        )
        advanceUntilIdle()
        val popup = dependencies.sessionStates.value.getValue(threadId.value).popupStack.single()
        assertEquals(setOf(1), uiState.value.revealedReplyTargetNumbers)

        // --- Popup close and reopen retain thread-owned disclosure ---
        viewModel.removeTopPopup(threadId.value)
        advanceUntilIdle()
        assertEquals(setOf(1), uiState.value.revealedReplyTargetNumbers)

        viewModel.addPopupForReplyTargetNumbers(
            threadId.value,
            IntOffset.Zero,
            listOf(1),
        )
        advanceUntilIdle()
        val reopenedPopup = dependencies.sessionStates.value.getValue(threadId.value).popupStack.single()
        assertTrue(reopenedPopup.popupId != popup.popupId)
        assertEquals(setOf(1), uiState.value.revealedReplyTargetNumbers)

        // --- A matching post in another thread has independent state ---
        viewModel.setReplyTargetDisclosure(otherThreadId.value, postNumber = 1, revealed = true)
        advanceUntilIdle()
        assertEquals(setOf(1), otherUiState.value.revealedReplyTargetNumbers)
        viewModel.setReplyTargetDisclosure(otherThreadId.value, postNumber = 1, revealed = false)
        advanceUntilIdle()
        assertEquals(setOf(1), uiState.value.revealedReplyTargetNumbers)
        assertTrue(otherUiState.value.revealedReplyTargetNumbers.isEmpty())

        // --- NG rule changes reset disclosure without changing NG filtering ---
        ngRules.value = ngRules.value + NgEntity(
            pattern = "unused rule",
            isRegex = false,
            boardId = 1L,
            type = NgType.WORD,
        )
        advanceUntilIdle()
        assertTrue(uiState.value.revealedReplyTargetNumbers.isEmpty())
        assertTrue(otherUiState.value.revealedReplyTargetNumbers.isEmpty())
        assertEquals(setOf(1), uiState.value.ngPostNumbers)
    }

    /** NG対象レスが一覧から消えた時点で開示を破棄し、後日の再出現へ引き継がない。 */
    @Test
    fun replyTargetDisclosureIsDiscardedWhenItsPostDisappears() = runTest {
        // --- Initial and updated post fixtures ---
        val threadId = ThreadId.of("example.com", "test", "reply-target-invalidated")
        val ngRules = MutableStateFlow(
            listOf(NgEntity(pattern = "blocked", isRegex = false, boardId = 1L, type = NgType.WORD)),
        )
        val initialPosts = listOf(
            threadPost(1, "blocked candidate"),
            threadPost(2, ">>1"),
        )
        val changedPosts = listOf(threadPost(1, "post removed"))
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            ngRules = ngRules,
            loadResults = listOf(
                contentLoadResult(initialPosts),
                contentLoadResult(changedPosts),
                contentLoadResult(initialPosts),
            ),
        )
        val viewModel = dependencies.createViewModel()
        val uiState = viewModel.uiStateFor(threadId.value)
        backgroundScope.launch { uiState.collect {} }
        advanceUntilIdle()

        // --- Disappearance removes the disclosed post number ---
        viewModel.setReplyTargetDisclosure(threadId.value, postNumber = 1, revealed = true)
        advanceUntilIdle()
        assertEquals(setOf(1), uiState.value.revealedReplyTargetNumbers)

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        assertTrue(uiState.value.posts?.size == 1)
        assertTrue(uiState.value.revealedReplyTargetNumbers.isEmpty())

        // --- A later post with the same number starts hidden ---
        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        assertEquals(listOf(1), uiState.value.replyPreviewByPostNumber[2]?.replyTargets?.single()?.referencedNumbers)
        assertTrue(uiState.value.revealedReplyTargetNumbers.isEmpty())
    }

    /** テスト用依存一式を構築する。 */
    private fun mockDependencies(
        tabs: List<ThreadTabInfo>,
        selectedTabKey: String?,
        initialSessionStates: Map<String, ThreadSessionState> = tabs.associate { it.id.value to ThreadSessionState() },
        suspendLoad: Boolean = false,
        loadReturnsNull: Boolean = false,
        ngRules: MutableStateFlow<List<NgEntity>> = MutableStateFlow(emptyList()),
        loadResults: List<ThreadContentLoadResult>? = null,
    ): RouteDependencies {
        val openTabs = MutableStateFlow(tabs)
        val selectedKey = MutableStateFlow(selectedTabKey)
        val sessionStates = MutableStateFlow(initialSessionStates)
        val runtimeStates = MutableStateFlow(tabs.associate { it.id.value to ThreadSessionRuntimeState() })
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.openThreadTabs } returns openTabs
        every { store.selectedThreadTabKey } returns selectedKey
        every { store.threadSessionStates } returns sessionStates
        every { store.threadBookmarkSheetHolder(any()).uiState } returns MutableStateFlow(BookmarkSheetUiState())
        every { store.threadPostDialogController(any()) } returns mockk<PostDialogController>(relaxed = true)
        every { store.threadPostDialogSuccessEvents(any()) } returns MutableSharedFlow()
        every { store.getThreadSessionState(any()) } answers {
            val threadKey = threadKey(firstArg())
            sessionStates.value[threadKey] ?: ThreadSessionState()
        }
        every { store.updateThreadSessionState(any(), any()) } answers {
            val threadId = threadKey(firstArg())
            val transform = secondArg<(ThreadSessionState) -> ThreadSessionState>()
            sessionStates.value = sessionStates.value + (threadId to transform(sessionStates.value[threadId] ?: ThreadSessionState()))
        }
        every { store.getThreadRuntimeState(any()) } answers {
            val threadKey = threadKey(firstArg())
            runtimeStates.value[threadKey] ?: ThreadSessionRuntimeState()
        }
        every { store.updateThreadRuntimeState(any(), any()) } answers {
            val threadId = threadKey(firstArg())
            val transform = secondArg<(ThreadSessionRuntimeState) -> ThreadSessionRuntimeState>()
            runtimeStates.value = runtimeStates.value + (threadId to transform(runtimeStates.value[threadId] ?: ThreadSessionRuntimeState()))
        }
        coEvery { store.updateThreadResolvedBoardInfo(any(), any(), any()) } answers {
            val threadId = ThreadId(firstArg())
            val boardId = secondArg<Long>()
            val boardName = thirdArg<String?>()
            openTabs.value = openTabs.value.map { tab ->
                if (tab.id == threadId) {
                    tab.copy(
                        boardId = boardId,
                        boardName = boardName?.takeIf(String::isNotBlank) ?: tab.boardName,
                    )
                } else {
                    tab
                }
            }
        }

        val boardRepository = mockk<BoardRepository>()
        coEvery { boardRepository.ensureBoard(any()) } answers { firstArg<BoardInfo>().boardId.takeIf { it != 0L } ?: 1L }
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val historyRepository = mockk<ThreadHistoryRepository>()
        coEvery { historyRepository.recordHistory(any(), any(), any()) } returns 1L

        val postHistoryRepository = mockk<PostHistoryRepository>(relaxed = true)
        every { postHistoryRepository.observeMyPostNumbers(any()) } returns flowOf(emptySet())

        val pendingOwnPostRepository = mockk<PendingOwnPostRepository>(relaxed = true)

        val bookmarkRepository = mockk<ThreadBookmarkRepository>()
        every { bookmarkRepository.getBookmarkWithGroup(any(), any()) } returns flowOf(null)

        val ngRepository = mockk<NgRepository>()
        every { ngRepository.observeNgs() } returns ngRules

        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.observeTextScale() } returns flowOf(1f)
        every { settingsRepository.observeIsIndividualTextScale() } returns flowOf(false)
        every { settingsRepository.observeHeaderTextScale() } returns flowOf(0.85f)
        every { settingsRepository.observeBodyTextScale() } returns flowOf(1f)
        every { settingsRepository.observeLineHeight() } returns flowOf(1f)
        every { settingsRepository.observeIsThreadMinimapScrollbarEnabled() } returns flowOf(true)
        every { settingsRepository.observeGestureSettings() } returns flowOf(com.websarva.wings.android.slevo.data.model.GestureSettings.DEFAULT)
        every { settingsRepository.observeIsTreeSort() } returns flowOf(false)

        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        every { tabsRepository.observeOpenThreadTabs() } returns openTabs

        val readStateRepository = mockk<ThreadReadStateRepository>(relaxed = true)

        val threadContentLoadUseCase = mockk<ThreadContentLoadUseCase>()
        val threadLoadCancelled = MutableStateFlow(false)
        val remainingLoadResults = loadResults?.toMutableList()
        tabs.forEach { tab ->
            coEvery {
                threadContentLoadUseCase.load(
                    tab.boardUrl,
                    tab.threadKey,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } coAnswers {
                if (suspendLoad) {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { threadLoadCancelled.value = true }
                    }
                } else if (loadReturnsNull) {
                    null
                } else if (remainingLoadResults != null) {
                    remainingLoadResults.removeAt(0)
                } else {
                    ThreadContentLoadResult(
                        uiPosts = emptyList(),
                        threadTitle = tab.title,
                        resCount = 0,
                        threadDate = ThreadDate(2024, 1, 1, 0, 0, "月"),
                        momentum = 0.0,
                        idCountMap = emptyMap(),
                        idIndexList = emptyList(),
                        replySourceMap = emptyMap(),
                        treeOrder = emptyList(),
                        treeDepthMap = emptyMap(),
                        treeRootMap = emptyMap(),
                    )
                }
            }
        }

        return RouteDependencies(
            store = store,
            openTabs = openTabs,
            sessionStates = sessionStates,
            threadLoadCancelled = threadLoadCancelled,
            boardRepository = boardRepository,
            historyRepository = historyRepository,
            postHistoryRepository = postHistoryRepository,
            pendingOwnPostRepository = pendingOwnPostRepository,
            bookmarkRepository = bookmarkRepository,
            ngRepository = ngRepository,
            settingsRepository = settingsRepository,
            tabsRepository = tabsRepository,
            readStateRepository = readStateRepository,
            threadContentLoadUseCase = threadContentLoadUseCase,
            threadVisiblePostsUseCase = ThreadVisiblePostsUseCase(),
            logger = mockk(relaxed = true),
        )
    }

    /** テスト用依存 bundle。 */
    private data class RouteDependencies(
        val store: TabSessionStore,
        val openTabs: MutableStateFlow<List<ThreadTabInfo>>,
        val sessionStates: MutableStateFlow<Map<String, ThreadSessionState>>,
        val threadLoadCancelled: MutableStateFlow<Boolean>,
        val boardRepository: BoardRepository,
        val historyRepository: ThreadHistoryRepository,
        val postHistoryRepository: PostHistoryRepository,
        val pendingOwnPostRepository: PendingOwnPostRepository,
        val bookmarkRepository: ThreadBookmarkRepository,
        val ngRepository: NgRepository,
        val settingsRepository: SettingsRepository,
        val tabsRepository: TabsRepository,
        val readStateRepository: ThreadReadStateRepository,
        val threadContentLoadUseCase: ThreadContentLoadUseCase,
        val threadVisiblePostsUseCase: ThreadVisiblePostsUseCase,
        val logger: AppLogger,
    ) {
        /** 依存 bundle から ViewModel を生成する。 */
        fun createViewModel(): ThreadRouteViewModel {
            return ThreadRouteViewModel(
                tabSessionStore = store,
                boardRepository = boardRepository,
                historyRepository = historyRepository,
                postHistoryRepository = postHistoryRepository,
                pendingOwnPostRepository = pendingOwnPostRepository,
                threadBookmarkRepository = bookmarkRepository,
                ngRepository = ngRepository,
                settingsRepository = settingsRepository,
                tabsRepository = tabsRepository,
                threadReadStateRepository = readStateRepository,
                threadContentLoadUseCase = threadContentLoadUseCase,
                threadVisiblePostsUseCase = threadVisiblePostsUseCase,
                logger = logger,
            )
        }
    }

    /** テスト用のタブ情報を作る。 */
    private fun threadTab(threadId: ThreadId, title: String, boardId: Long = 1L): ThreadTabInfo {
        return ThreadTabInfo(
            id = threadId,
            title = title,
            boardName = "board",
            boardUrl = "https://example.com/test/",
            boardId = boardId,
        )
    }

    /** 指定件数の投稿を作り、初回・追加ロードのレス範囲を再現する。 */
    private fun posts(count: Int): List<ThreadPostUiModel> {
        return (1..count).map { number ->
            threadPost(number, "post $number")
        }
    }

    /** 指定番号と本文から route テスト用投稿を作る。 */
    private fun threadPost(number: Int, content: String): ThreadPostUiModel = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(
            name = "name$number",
            email = "",
            date = "2024/01/01 00:00:00",
            id = "id$number",
        ),
        body = ThreadPostUiModel.Body(content = content),
    )

    /** 投稿から実際の取得ユースケースと同じ返信・ツリー派生結果を作る。 */
    private fun contentLoadResult(posts: List<ThreadPostUiModel>): ThreadContentLoadResult {
        val (idCountMap, idIndexList, replySourceMap) = deriveReplyMaps(posts)
        val (treeOrder, treeDepthMap) = deriveTreeOrder(posts)
        return ThreadContentLoadResult(
            uiPosts = posts,
            threadTitle = "title",
            resCount = posts.size,
            threadDate = ThreadDate(2024, 1, 1, 0, 0, "月"),
            momentum = 0.0,
            idCountMap = idCountMap,
            idIndexList = idIndexList,
            replySourceMap = replySourceMap,
            treeOrder = treeOrder,
            treeDepthMap = treeDepthMap,
            treeRootMap = deriveTreeRoots(treeOrder, treeDepthMap),
        )
    }

    /** グループ結果を表示変換へ渡し、新着バー挿入位置を返す。 */
    private fun firstAfterIndex(
        posts: List<ThreadPostUiModel>,
        state: ThreadRoutePostGroupState,
    ): Int {
        return ThreadVisiblePostsUseCase().buildVisibleRows(
            posts = posts,
            groups = state.groups,
            sortType = com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = state.latestArrivalGroupIndex,
            searchQuery = "",
            ngPostNumbers = emptySet(),
            visibleReplySourceMap = emptyMap(),
        ).firstAfterIndex
    }

    /** ViewModelの内部scopeを含めてテスト用ViewModelを解放する。 */
    private fun invokeOnCleared(viewModel: ThreadRouteViewModel) {
        viewModel.viewModelScope.cancel()
        val method = ThreadRouteViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }

    /** MockK 引数から thread key を取り出す。 */
    private fun threadKey(arg: Any?): String {
        return when (arg) {
            is ThreadId -> arg.value
            is String -> arg
            else -> error("Unexpected thread key argument: $arg")
        }
    }
}
