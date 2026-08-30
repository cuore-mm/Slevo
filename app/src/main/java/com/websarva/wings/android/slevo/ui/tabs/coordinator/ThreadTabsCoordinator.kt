package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.TabMutationResult
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.bbsroute.TabPresentationState
import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.tabs.model.mergeThreadTabMetadata
import com.websarva.wings.android.slevo.ui.tabs.controller.selectionAfterTabRemovals
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadRefreshRequest
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadRefreshUseCase
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import javax.inject.Inject

/**
 * スレッドタブの集合を管理するコーディネータ。
 *
 * 主な責務:
 * - 開いているスレッドタブの状態を保持・更新する
 * - タブの追加/更新/削除、選択 key 管理、リフレッシュ処理を提供する
 * - タブの永続化（リポジトリ経由）を行う
 *
 * スコープは外部から bind(...) で渡されるスコープを使用する。
 */
@ActivityRetainedScoped
class ThreadTabsCoordinator @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val threadBookmarkRepository: ThreadBookmarkRepository,
    private val threadRefreshUseCase: ThreadRefreshUseCase,
) {
    /**
     * 正常完了時に 100% の進捗を表示し続ける時間。
     */
    private companion object {
        const val REFRESH_COMPLETION_VISIBILITY_MILLIS = 300L
    }

    private val _threadTabState = MutableStateFlow<ThreadTabsLoadState>(ThreadTabsLoadState.Loading)
    val threadTabState: StateFlow<ThreadTabsLoadState> = _threadTabState.asStateFlow()

    private val _openThreadTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())
    val openThreadTabs: StateFlow<List<ThreadTabInfo>> = _openThreadTabs.asStateFlow()

    private val _threadLoaded = MutableStateFlow(false)
    val threadLoaded: StateFlow<Boolean> = _threadLoaded.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshProgress = MutableStateFlow<ThreadTabRefreshProgress?>(null)
    val refreshProgress: StateFlow<ThreadTabRefreshProgress?> = _refreshProgress.asStateFlow()

    private val _newResCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val newResCounts: StateFlow<Map<String, Int>> = _newResCounts.asStateFlow()

    private val _selectedThreadTabKey = MutableStateFlow<String?>(null)
    val selectedThreadTabKey: StateFlow<String?> = _selectedThreadTabKey.asStateFlow()

    private val _threadPresentationState = MutableStateFlow<TabPresentationState<ThreadTabInfo, String>>(
        TabPresentationState(emptyList(), TabSelectionResolution.Loading),
    )
    val threadPresentationState: StateFlow<TabPresentationState<ThreadTabInfo, String>> =
        _threadPresentationState.asStateFlow()

    private val _threadSessionStates = MutableStateFlow<Map<String, ThreadSessionState>>(emptyMap())
    val threadSessionStates: StateFlow<Map<String, ThreadSessionState>> = _threadSessionStates.asStateFlow()

    private val _threadRuntimeStates = MutableStateFlow<Map<String, ThreadSessionRuntimeState>>(emptyMap())
    val threadRuntimeStates: StateFlow<Map<String, ThreadSessionRuntimeState>> = _threadRuntimeStates.asStateFlow()

    private val _threadCurrentPage = MutableStateFlow(-1)

    private val _threadPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val threadPageAnimation: SharedFlow<Int> = _threadPageAnimation.asSharedFlow()

    private var scope: CoroutineScope? = null

    /** Room が最後に通知した一覧だけを正規スナップショットとして保持する。 */
    private val canonicalTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())
    private val pendingOperations = mutableListOf<ThreadTabPendingEntry>()
    private var snapshotVersion = 0L
    private val snapshotVersionFlow = MutableStateFlow(0L)
    private val commandQueue = Channel<ThreadTabMutationIntent>(Channel.UNLIMITED)
    private var commandDispatcherJob: Job? = null

    /** 保留中の操作と、その操作だけを一度終端させる supersession 通知を保持する。 */
    private class ThreadTabPendingEntry(
        val operation: ThreadTabPendingOperation,
    ) {
        val superseded = CompletableDeferred<Unit>()
    }

    /** Room 確認または後続成功による supersession の終端結果を表す。 */
    private enum class ThreadTabConfirmationResolution {
        Confirmed,
        Superseded,
    }

    /**
     * 実行中のスレッドタブ更新ジョブを保持する。
     */
    private var refreshJob: Job? = null

    /**
     * コーディネータを指定の CoroutineScope にバインドする。
     *
     * bind は一度だけ有効で、既にバインド済みの場合は何もしない。
     * バインド時にリポジトリのフローを結合して、_openThreadTabs を更新する購読を開始する。
     */
    fun bind(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        commandDispatcherJob = scope.launch { processMutationIntents() }
        scope.launch {
            combine(
                tabsRepository.observeOpenThreadTabs(),
                threadBookmarkRepository.observeSortedGroupsWithThreadBookmarks()
            ) { tabs, groups ->
                val colorMap = mutableMapOf<String, String>()
                groups.forEach { group ->
                    val color = group.group.colorName
                    group.threads.forEach { bookmark ->
                        parseBoardUrl(bookmark.boardUrl)?.let { (host, board) ->
                            val threadId = ThreadId.of(host, board, bookmark.threadKey)
                            colorMap[threadId.value] = color
                        }
                    }
                }
                tabs.map { tab -> tab.copy(bookmarkColorName = colorMap[tab.id.value]) }
            }.collect { threads ->
                canonicalTabs.value = threads
                snapshotVersion += 1
                snapshotVersionFlow.value = snapshotVersion
                setThreadTabState(ThreadTabsLoadState.Loaded(threads))
                publishProjectedTabs()
            }
        }
    }

    /**
     * 指定のルート情報に対応するスレッドタブを作成または更新し、タブのインデックスを返す。
     * 失敗した場合は -1 を返す。
     */
    suspend fun ensureThreadTab(route: AppRoute.Thread): Int {
        val (host, board) = parseBoardUrl(route.boardUrl) ?: return -1
        val tabInfo = ThreadTabInfo(
            id = ThreadId.of(host, board, route.threadKey),
            title = buildInitialThreadTitle(route),
            boardName = route.boardName,
            boardUrl = route.boardUrl,
            boardId = route.boardId ?: 0L,
            resCount = route.resCount,
        )
        // テストではライフサイクルスコープを bind せずに coordinator を使用できる。本番では常に先に bind する。
        if (scope == null) return ensureThreadTabWithoutPersistence(tabInfo)
        val operation = ThreadTabPendingOperation.Ensure(tabInfo)
        val completion = CompletableDeferred<Int>()
        commandQueue.send(ThreadTabMutationIntent.Ensure(tabInfo, operation, completion))
        return try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /**
     * スレタイトル未取得時の初期表示名を組み立てる。
     *
     * `threadTitle` が空の場合は、正規化済み `boardUrl` と `threadKey` から
     * スレURLを組み立てて表示文字列にする。
     */
    private fun buildInitialThreadTitle(route: AppRoute.Thread): String {
        route.threadTitle?.takeIf { it.isNotBlank() }?.let { return it }
        val parsed = parseBoardUrl(route.boardUrl) ?: return ""
        val (host, boardKey) = parsed
        return "https://$host/test/read.cgi/$boardKey/${route.threadKey}/"
    }

    /**
     * 指定の ThreadTabInfo を閉じる。
     *
     * セッション状態、ランタイム状態、選択 key を整理してから永続状態を更新する。
     */
    suspend fun closeThreadTab(tab: ThreadTabInfo) {
        if (scope == null) {
            closeThreadTabWithoutPersistence(tab)
            return
        }
        val operation = ThreadTabPendingOperation.Delete(tab.id)
        val completion = CompletableDeferred<Unit>()
        commandQueue.send(ThreadTabMutationIntent.Delete(tab.id, operation, completion))
        try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /** 複数のスレッドタブを一つのbulk intentとして受理する。 */
    suspend fun closeThreadTabs(tabs: List<ThreadTabInfo>) {
        val threadIds = tabs.map { it.id }.distinctBy { it.value }
        if (threadIds.isEmpty()) return
        if (scope == null) {
            closeThreadTabsWithoutPersistence(tabs)
            return
        }
        val currentTabs = _openThreadTabs.value
        val requestedSelection = selectionAfterTabRemovals(
            selectedKey = _selectedThreadTabKey.value?.let(::ThreadId),
            tabs = currentTabs,
            removedKeys = threadIds,
            keyOf = ThreadTabInfo::id,
        )?.value
        val operation = ThreadTabPendingOperation.BulkDelete(threadIds, requestedSelection)
        val completion = CompletableDeferred<Unit>()
        commandQueue.send(ThreadTabMutationIntent.BulkDelete(operation, completion))
        try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /** 指定スレッドタブ集合の pin 状態を明示値へ揃える。 */
    suspend fun setThreadTabsPinned(tabs: List<ThreadTabInfo>, isPinned: Boolean) {
        val threadIds = tabs.map { it.id }.distinctBy { it.value }
        if (threadIds.isEmpty()) return
        if (scope == null) {
            _openThreadTabs.update { currentTabs ->
                currentTabs.map { tab ->
                    if (tab.id in threadIds) tab.copy(isPinned = isPinned) else tab
                }
            }
            return
        }
        val operation = ThreadTabPendingOperation.BulkPin(threadIds, isPinned)
        val completion = CompletableDeferred<Unit>()
        commandQueue.send(ThreadTabMutationIntent.BulkPin(operation, completion))
        try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /** スレッドタブの最終key順をpending projectionへ登録し、Room保存を開始する。 */
    fun reorderThreadTabs(threadIds: List<String>): Boolean {
        val distinctIds = threadIds.distinct()
        if (distinctIds.isEmpty()) return false
        val operation = ThreadTabPendingOperation.Reorder(distinctIds.map(::ThreadId))
        if (scope == null) {
            val reordered = com.websarva.wings.android.slevo.ui.tabs.controller.reorderTabs(
                _openThreadTabs.value,
                distinctIds,
                { it.id.value },
            )
            publishThreadPresentation(reordered)
            return true
        }
        // Reorderはkey列を受理した時点でpendingへ登録し、画面側のdraftから連続して引き継ぐ。
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            processReorder(operation, CompletableDeferred())
        }
        return true
    }

    /**
     * 選択中のスレッドタブ key を更新する。
     */
    /**
     * 現在表示中の正規タブを選択し、対象が存在したかを返す。
     * null でない対象が存在しない場合は、直前の選択をそのまま維持する。
     */
    fun selectThreadTab(threadId: ThreadId?): Boolean {
        if (threadId == null) {
            publishThreadPresentation(requestedSelection = null)
            return true
        }
        val availableTabs = if (scope == null) _openThreadTabs.value else canonicalTabs.value
        if (!availableTabs.any { it.id == threadId }) return false
        publishThreadPresentation(requestedSelection = threadId.value)
        return true
    }

    /** Room が要求されたスレッドタブを確認済みかどうかを返す。 */
    fun isCanonicalThreadTab(threadId: ThreadId): Boolean =
        canonicalTabs.value.any { it.id == threadId }

    /**
     * threadKey と boardUrl からタブを特定して閉じる（存在しない場合は何もしない）。
     */
    suspend fun closeThreadTab(threadKey: String, boardUrl: String) {
        val (host, board) = parseBoardUrl(boardUrl) ?: return
        val id = ThreadId.of(host, board, threadKey)
        val tab = _openThreadTabs.value.find { it.id == id }
        if (tab != null) closeThreadTab(tab)
    }

    /**
     * ページ遷移のアニメーションを発行する（SharedFlow にインデックスを emit）。
     */
    fun animateThreadPage(offset: Int) {
        val tabs = _openThreadTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _threadCurrentPage.value.takeIf { it in tabs.indices } ?: return
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            scope?.launch { _threadPageAnimation.emit(targetIndex) }
        }
    }

    /**
     * 指定スレッドの新着レスカウントをクリアする。
     */
    fun clearNewResCount(threadId: ThreadId) {
        val key = threadId.value
        _newResCounts.update { it - key }
    }

    /**
     * 開いているタブをリフレッシュして、取得した最新レス数を `thread_states` へ保存する。
     *
     * 更新中は進捗状態を更新し、取得できたタブから順次反映する。
     * 新着バッジは保存後の `thread_states + thread_histories` 合成 Flow から再導出する。
     */
    fun refreshOpenThreads() {
        val currentScope = scope ?: return // Guard: bind 前は更新を開始しない。
        // Guard: 既に更新中の場合は重複開始しない。
        if (refreshJob?.isActive == true) return
        val snapshotTabs = _openThreadTabs.value
        // Guard: 更新対象が空の場合は何もしない。
        if (snapshotTabs.isEmpty()) return
        refreshJob = currentScope.launch {
            var completedNormally = false
            try {
                // --- Refresh start ---
                _isRefreshing.value = true
                _refreshProgress.value = ThreadTabRefreshProgress(0, snapshotTabs.size)

                // --- Refresh loop ---
                snapshotTabs.forEachIndexed { index, tab ->
                    // Guard: キャンセル済みなら即座に中断する。
                    currentCoroutineContext().ensureActive()
                    // Guard: 取得開始前に閉じられたタブは通信と取得後処理を行わない。
                    if (_openThreadTabs.value.any { currentTab -> currentTab.id == tab.id }) {
                        threadRefreshUseCase.refresh(
                            ThreadRefreshRequest(
                                threadId = tab.id,
                                boardUrl = tab.boardUrl,
                                boardId = tab.boardId,
                                boardName = tab.boardName,
                                threadKey = tab.threadKey,
                                threadTitle = tab.title,
                            ),
                        )
                    }
                    _refreshProgress.update { progress ->
                        progress?.copy(completedCount = index + 1)
                    }
                }
                completedNormally = true
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } finally {
                val completedJob = currentCoroutineContext()[Job]
                // Guard: 先行ジョブの終了処理で新しい更新状態を上書きしない。
                if (refreshJob !== completedJob) {
                    return@launch
                }
                // --- Refresh end ---
                // Guard: 正常完了時のみ 100% を短時間表示してから非表示にする。
                if (completedNormally) {
                    // Guard: 表示待機中にキャンセルされても cleanup は継続する。
                    try {
                        delay(REFRESH_COMPLETION_VISIBILITY_MILLIS)
                    } catch (_: CancellationException) {
                        // no-op
                    }
                }
                _isRefreshing.value = false
                _refreshProgress.value = null
                refreshJob = null
            }
        }
    }

    /**
     * 実行中のスレッドタブ更新をキャンセルする。
     */
    fun cancelRefreshOpenThreads() {
        refreshJob?.cancel()
    }

    /** Controller の Room collector、effect runner、未完 waiter を retained lifetime の終端で停止する。 */
    fun close() {
        scope?.cancel()
    }

    /**
     * 指定した ThreadId のスレッドタブの固定状態を切り替えて保存する。
     */
    suspend fun togglePinThreadTab(threadId: ThreadId) {
        if (scope == null) {
            _openThreadTabs.update { tabs ->
                tabs.map { tab -> if (tab.id == threadId) tab.copy(isPinned = !tab.isPinned) else tab }
            }
            return
        }
        val completion = CompletableDeferred<Unit>()
        commandQueue.send(ThreadTabMutationIntent.Pin(threadId, completion))
        try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /**
     * 指定の ThreadId に対応する ThreadTabInfo を返す（存在しなければ null）。
     */
    fun getTabInfo(threadId: ThreadId): ThreadTabInfo? {
        return _openThreadTabs.value.find { it.id == threadId }
    }

    /**
     * 指定スレッドタブの揮発 UI セッション状態を返す。
     */
    fun getThreadSessionState(threadId: ThreadId): ThreadSessionState {
        return _threadSessionStates.value[threadId.value] ?: ThreadSessionState()
    }

    /**
     * 指定スレッドタブの揮発 UI セッション状態を更新する。
     */
    fun updateThreadSessionState(
        threadId: ThreadId,
        transform: (ThreadSessionState) -> ThreadSessionState,
    ) {
        val key = threadId.value
        _threadSessionStates.update { states ->
            val current = states[key] ?: ThreadSessionState()
            states + (key to transform(current))
        }
    }

    /**
     * 指定スレッドタブの継続ランタイム状態を返す。
     */
    fun getThreadRuntimeState(threadId: ThreadId): ThreadSessionRuntimeState {
        return _threadRuntimeStates.value[threadId.value] ?: ThreadSessionRuntimeState()
    }

    /**
     * 指定スレッドタブの継続ランタイム状態を更新する。
     */
    fun updateThreadRuntimeState(
        threadId: ThreadId,
        transform: (ThreadSessionRuntimeState) -> ThreadSessionRuntimeState,
    ) {
        val key = threadId.value
        _threadRuntimeStates.update { states ->
            val current = states[key] ?: ThreadSessionRuntimeState()
            states + (key to transform(current))
        }
    }

    /**
     * ensure 済みの boardId を既存スレッドタブへ反映して永続状態も更新する。
     *
     * URL から開いた placeholder boardId のタブを、Repository で解決した実 boardId に差し替える。
     */
    suspend fun updateThreadResolvedBoardInfo(
        threadId: ThreadId,
        boardId: Long,
        boardName: String? = null,
    ) {
        if (boardId == 0L) return
        val current = _openThreadTabs.value.firstOrNull { it.id == threadId } ?: return
        val updated = current.copy(
            boardId = boardId,
            boardName = boardName?.takeIf(String::isNotBlank) ?: current.boardName,
        )
        if (scope == null) {
            _openThreadTabs.update { tabs -> tabs.map { tab -> if (tab.id == threadId) updated else tab } }
            return
        }
        val operation = ThreadTabPendingOperation.Info(updated)
        val completion = CompletableDeferred<Unit>()
        commandQueue.send(ThreadTabMutationIntent.Info(updated, operation, completion))
        try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /** キューに入った 1 件の更新操作と、それを呼び出した側が所有する完了通知。 */
    private sealed interface ThreadTabMutationIntent {
        val completion: CompletableDeferred<*>

        /** スレッドタブの存在を 1 件追加または保証する。 */
        data class Ensure(
            val tab: ThreadTabInfo,
            val operation: ThreadTabPendingOperation.Ensure,
            override val completion: CompletableDeferred<Int>,
        ) : ThreadTabMutationIntent

        /** スレッドタブを 1 件削除する。 */
        data class Delete(
            val threadId: ThreadId,
            val operation: ThreadTabPendingOperation.Delete,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** 複数スレッドタブを一つの対象集合として削除する。 */
        data class BulkDelete(
            val operation: ThreadTabPendingOperation.BulkDelete,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** 1 件の固定列を変更する。 */
        data class Pin(
            val threadId: ThreadId,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** 複数スレッドの固定列を一つの対象集合として変更する。 */
        data class BulkPin(
            val operation: ThreadTabPendingOperation.BulkPin,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** JOIN 済みの ThreadState 投影を 1 件更新する。 */
        data class Info(
            val tab: ThreadTabInfo,
            val operation: ThreadTabPendingOperation.Info,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** stable key列の表示順を変更する。 */
        data class Reorder(
            val operation: ThreadTabPendingOperation.Reorder,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent
    }

    /**
     * 受理順に command を登録し、confirmation を待たずに各 effect を Controller scope で進める。
     * 同じ scope の repository gate が DB write 順序を保ち、Room snapshot の書き手は Room だけにする。
     */
    private suspend fun processMutationIntents() {
        try {
            for (intent in commandQueue) {
                if (isIntentCancelled(intent)) continue
                awaitLoadedState()
                // 呼び出し元のキャンセルと同時に準備が完了している可能性がある。
                if (isIntentCancelled(intent)) continue
                if (intent is ThreadTabMutationIntent.BulkDelete || intent is ThreadTabMutationIntent.BulkPin) {
                    // Bulkは後続mutationを開始する前にcanonical確認まで完了させるbarrierとする。
                    processIntent(intent)
                } else {
                    // 単体mutationの既存並行性とsupersession契約は維持する。
                    scope?.launch(start = CoroutineStart.UNDISPATCHED) { processIntent(intent) }
                }
            }
        } finally {
            // 破棄後に完了できない通知を待つ呼び出し元を残さない。
            while (true) {
                val intent = commandQueue.tryReceive().getOrNull() ?: break
                cancelIntentCompletion(intent)
            }
            pendingOperations.clear()
            publishProjectedTabs()
        }
    }

    /** 受理済み command を caller の待機 Job から独立した Controller scope で実行する。 */
    private suspend fun processIntent(intent: ThreadTabMutationIntent) {
        when (intent) {
            is ThreadTabMutationIntent.Ensure -> processEnsure(intent)
            is ThreadTabMutationIntent.Delete -> processDelete(intent)
            is ThreadTabMutationIntent.BulkDelete -> processBulkDelete(intent)
            is ThreadTabMutationIntent.Pin -> processPin(intent)
            is ThreadTabMutationIntent.BulkPin -> processBulkPin(intent)
            is ThreadTabMutationIntent.Info -> processInfo(intent)
            is ThreadTabMutationIntent.Reorder -> processReorder(intent.operation, intent.completion)
        }
    }

    /** 起動時の空一覧を読み込み済みとみなさず、最初の正規 Room スナップショットを待つ。 */
    private suspend fun awaitLoadedState() {
        _threadTabState.filter { it is ThreadTabsLoadState.Loaded }.first()
    }

    /** 明示的な読み込み状態と、互換用 boolean 投影を同時に更新する。 */
    private fun setThreadTabState(state: ThreadTabsLoadState) {
        _threadTabState.value = state
        _threadLoaded.value = state is ThreadTabsLoadState.Loaded
    }

    /** DB 書き込み、Flow による確認、投影の後始末を通して存在保証操作を実行する。 */
    private suspend fun processEnsure(intent: ThreadTabMutationIntent.Ensure) {
        val (entry, baselineVersion) = registerPending(intent.operation)
        try {
            if (!tabsRepository.ensureOpenThreadTab(intent.tab)) {
                throw IllegalStateException("Thread tab ensure failed")
            }
            supersedeEarlierOperations(entry)
            when (awaitConfirmation(entry, baselineVersion)) {
                ThreadTabConfirmationResolution.Confirmed -> {
                    removePending(entry)
                    intent.completion.complete(_openThreadTabs.value.indexOfFirst { it.id == intent.tab.id })
                }
                ThreadTabConfirmationResolution.Superseded -> {
                    // 最終不在を決めた Delete に置き換えられたため、Ensure は選択を作らない。
                    removePending(entry)
                    intent.completion.complete(-1)
                }
            }
        } catch (exception: Throwable) {
            removePending(entry)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** delete 操作を実行し、正規状態での削除確認後にだけ選択を補正する。 */
    private suspend fun processDelete(intent: ThreadTabMutationIntent.Delete) {
        val selectedKeyBeforeRemoval = _selectedThreadTabKey.value
        val removedIndex = canonicalTabs.value.indexOfFirst { it.id == intent.threadId }
        val (entry, baselineVersion) = registerPending(intent.operation)
        try {
            // --- Repository write and supersession ---
            val changed = tabsRepository.deleteOpenThreadTab(intent.threadId)
            if (changed) {
                supersedeEarlierOperations(entry)
                if (awaitConfirmation(entry, baselineVersion) == ThreadTabConfirmationResolution.Superseded) {
                    // 後続 Ensure が存在状態を決めたため、古い Delete の cleanup は実行しない。
                    removePending(entry)
                    intent.completion.complete(Unit)
                    return
                }
            }
            // --- Selection and session cleanup ---
            val updatedTabs = projectThreadTabs(
                canonicalTabs.value,
                pendingOperations.filterNot { it === entry }.map { it.operation },
            )
            val nextSelection = selectedThreadKeyAfterRemoval(
                selectedKeyBeforeRemoval,
                intent.threadId.value,
                removedIndex,
                updatedTabs,
            )
            removePending(entry, nextSelection)
            _newResCounts.update { it - intent.threadId.value }
            _threadSessionStates.update { it - intent.threadId.value }
            _threadRuntimeStates.update { it - intent.threadId.value }
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(entry)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** bulk DELETE、canonical確認、最終選択補正、全対象の揮発状態cleanupを実行する。 */
    private suspend fun processBulkDelete(intent: ThreadTabMutationIntent.BulkDelete) {
        val operation = intent.operation
        val (entry, baselineVersion) = registerPending(operation)
        try {
            val changed = tabsRepository.deleteOpenThreadTabs(operation.threadIds)
            if (changed) {
                supersedeEarlierOperations(entry)
                if (awaitConfirmation(entry, baselineVersion) == ThreadTabConfirmationResolution.Superseded) {
                    removePending(entry)
                    intent.completion.complete(Unit)
                    return
                }
            }
            removePending(entry, operation.requestedSelection)
            operation.threadIds.forEach { threadId ->
                val key = threadId.value
                _newResCounts.update { it - key }
                _threadSessionStates.update { it - key }
                _threadRuntimeStates.update { it - key }
            }
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(entry)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** bulk pinを実行し、指定集合のRoom確認後にpendingを破棄する。 */
    private suspend fun processBulkPin(intent: ThreadTabMutationIntent.BulkPin) {
        val operation = intent.operation
        val (entry, baselineVersion) = registerPending(operation)
        try {
            val changed = tabsRepository.setThreadTabsPinned(operation.threadIds, operation.isPinned)
            if (changed) {
                supersedeEarlierOperations(entry)
                if (awaitConfirmation(entry, baselineVersion) == ThreadTabConfirmationResolution.Superseded) {
                    removePending(entry)
                    intent.completion.complete(Unit)
                    return
                }
            }
            removePending(entry)
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(entry)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** 先行するすべての要求が残した投影状態を使って固定状態の切り替えを実行する。 */
    private suspend fun processPin(intent: ThreadTabMutationIntent.Pin) {
        val current = _openThreadTabs.value.firstOrNull { it.id == intent.threadId }
        // 削除済みなどで対象がない場合、切り替え要求は成功扱いの no-op とする。
        if (current == null) {
            intent.completion.complete(Unit)
            return
        }
        val operation = ThreadTabPendingOperation.Pin(intent.threadId, !current.isPinned)
        val (entry, baselineVersion) = registerPending(operation)
        try {
            val changed = tabsRepository.setThreadTabPinned(intent.threadId, operation.isPinned)
            if (changed) {
                supersedeEarlierOperations(entry)
                if (awaitConfirmation(entry, baselineVersion) == ThreadTabConfirmationResolution.Superseded) {
                    removePending(entry)
                    intent.completion.complete(Unit)
                    return
                }
            }
            removePending(entry)
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(entry)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** ThreadState を更新し、JOIN されたタブ Flow に値が反映されるまで待つ。 */
    private suspend fun processInfo(intent: ThreadTabMutationIntent.Info) {
        val (entry, baselineVersion) = registerPending(intent.operation)
        try {
            tabsRepository.updateThreadState(intent.tab.toThreadStateUpdate())
            supersedeEarlierOperations(entry)
            if (awaitConfirmation(entry, baselineVersion) == ThreadTabConfirmationResolution.Superseded) {
                removePending(entry)
                intent.completion.complete(Unit)
                return
            }
            removePending(entry)
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(entry)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** 順序列だけを保存し、Roomの新しいsnapshotで並び順を確認する。 */
    private suspend fun processReorder(
        operation: ThreadTabPendingOperation.Reorder,
        completion: CompletableDeferred<Unit>,
    ) {
        val (entry, baselineVersion) = registerPending(operation)
        try {
            when (val mutation = tabsRepository.reorderOpenThreadTabs(operation.threadIds.map(ThreadId::value))) {
                TabMutationResult.Success -> {
                    supersedeEarlierOperations(entry)
                    when (awaitConfirmation(entry, baselineVersion)) {
                        ThreadTabConfirmationResolution.Confirmed,
                        ThreadTabConfirmationResolution.Superseded,
                        -> {
                            removePending(entry)
                            completion.complete(Unit)
                        }
                    }
                }

                TabMutationResult.NoOp -> {
                    removePending(entry)
                    completion.complete(Unit)
                }

                is TabMutationResult.Failure -> throw mutation.cause
            }
        } catch (exception: Throwable) {
            removePending(entry)
            completion.completeExceptionally(exception)
        }
    }

    /** 後続の成功 write と両立しない同一 Thread の先行 entry だけを終端する。 */
    private fun supersedeEarlierOperations(currentEntry: ThreadTabPendingEntry) {
        val currentIndex = pendingOperations.indexOfFirst { entry -> entry === currentEntry }
        if (currentIndex < 0) return
        val supersededEntries = pendingOperations
            .take(currentIndex)
            .filter { entry ->
                entry.operation.targetThreadIds.any { it in currentEntry.operation.targetThreadIds } &&
                    canSupersede(currentEntry.operation, entry.operation)
            }
        if (supersededEntries.isEmpty()) return
        // 先に投影から外して、signal の即時再開が後続 entry を再投影しないようにする。
        supersededEntries.forEach { entry -> pendingOperations.remove(entry) }
        supersededEntries.forEach { entry -> entry.superseded.complete(Unit) }
        publishProjectedTabs()
    }

    /** 後続 operation が先行 operation の canonical 条件を無効にする組み合わせかを返す。 */
    private fun canSupersede(
        later: ThreadTabPendingOperation,
        earlier: ThreadTabPendingOperation,
    ): Boolean = when (later) {
        is ThreadTabPendingOperation.Pin -> earlier is ThreadTabPendingOperation.Pin ||
            earlier is ThreadTabPendingOperation.BulkPin
        is ThreadTabPendingOperation.Delete -> earlier is ThreadTabPendingOperation.Ensure ||
            earlier is ThreadTabPendingOperation.Pin ||
            earlier is ThreadTabPendingOperation.BulkPin ||
            earlier is ThreadTabPendingOperation.Info ||
            earlier is ThreadTabPendingOperation.Reorder
            is ThreadTabPendingOperation.BulkDelete -> earlier is ThreadTabPendingOperation.Ensure ||
            earlier is ThreadTabPendingOperation.Pin ||
            earlier is ThreadTabPendingOperation.Info ||
            earlier is ThreadTabPendingOperation.Delete ||
            earlier is ThreadTabPendingOperation.BulkDelete ||
            earlier is ThreadTabPendingOperation.BulkPin ||
            earlier is ThreadTabPendingOperation.Reorder
        is ThreadTabPendingOperation.BulkPin -> earlier is ThreadTabPendingOperation.Pin ||
            earlier is ThreadTabPendingOperation.BulkPin
        is ThreadTabPendingOperation.Ensure -> earlier is ThreadTabPendingOperation.Delete
        is ThreadTabPendingOperation.Info -> false
        is ThreadTabPendingOperation.Reorder -> earlier is ThreadTabPendingOperation.Reorder
    }

    /** 保留中の操作を 1 件追加し、投影した一覧を再発行する。 */
    private fun registerPending(operation: ThreadTabPendingOperation): Pair<ThreadTabPendingEntry, Long> {
        val entry = ThreadTabPendingEntry(operation)
        pendingOperations += entry
        publishProjectedTabs()
        return entry to snapshotVersion
    }

    /** 完了または失敗した操作を 1 件削除し、正規状態の投影を再発行する。 */
    private fun removePending(
        entry: ThreadTabPendingEntry,
        requestedSelection: String? = _selectedThreadTabKey.value,
    ) {
        val operationIndex = pendingOperations.indexOfFirst { pendingEntry -> pendingEntry === entry }
        if (operationIndex >= 0) pendingOperations.removeAt(operationIndex)
        publishProjectedTabs(requestedSelection)
    }

    /** 操作固有の条件を満たす新しい Room 通知または supersession を待つ。 */
    private suspend fun awaitConfirmation(
        entry: ThreadTabPendingEntry,
        baselineVersion: Long,
    ): ThreadTabConfirmationResolution = coroutineScope {
        val canonicalConfirmation = async(start = CoroutineStart.UNDISPATCHED) {
            snapshotVersionFlow.first { version ->
                version > baselineVersion &&
                    isThreadTabOperationConfirmed(canonicalTabs.value, entry.operation)
            }
        }
        try {
            select {
                canonicalConfirmation.onAwait {
                    ThreadTabConfirmationResolution.Confirmed
                }
                entry.superseded.onAwait {
                    canonicalConfirmation.cancel()
                    ThreadTabConfirmationResolution.Superseded
                }
            }
        } finally {
            canonicalConfirmation.cancel()
        }
    }

    /** canonicalTabs を変更せず、保留中の投影だけを発行する。 */
    private fun publishProjectedTabs(requestedSelection: String? = _selectedThreadTabKey.value) {
        val projected = projectThreadTabs(
            canonicalTabs.value,
            pendingOperations.map { entry -> entry.operation },
        )
        publishThreadPresentation(projected, requestedSelection)
        _newResCounts.value = projected
            .filter { tab -> tab.newResCount > 0 }
            .associate { tab -> tab.id.value to tab.newResCount }
    }

    /**
     * projected tabs と選択 key を同じ snapshot に解決して公開する。
     * 不在 key は pending operation が説明できる間だけ保持し、それ以外は先頭へ補正する。
     */
    private fun publishThreadPresentation(
        tabs: List<ThreadTabInfo> = _openThreadTabs.value,
        requestedSelection: String? = _selectedThreadTabKey.value,
    ) {
        _openThreadTabs.value = tabs
        if (_threadTabState.value is ThreadTabsLoadState.Loading) {
            _threadPresentationState.value = TabPresentationState(
                emptyList(),
                TabSelectionResolution.Loading,
            )
            _threadCurrentPage.value = -1
            return
        }
        when {
            requestedSelection != null &&
                tabs.none { it.id.value == requestedSelection } &&
                pendingOperations.any { entry -> entry.operation.selectionKey == requestedSelection } -> {
                _selectedThreadTabKey.value = requestedSelection
                _threadPresentationState.value = TabPresentationState(
                    tabs,
                    TabSelectionResolution.PendingMissing(requestedSelection),
                )
            }
            tabs.isEmpty() -> {
                _selectedThreadTabKey.value = null
                _threadPresentationState.value = TabPresentationState(tabs, TabSelectionResolution.Empty)
            }
            requestedSelection != null && tabs.any { it.id.value == requestedSelection } -> {
                _selectedThreadTabKey.value = requestedSelection
                _threadPresentationState.value = TabPresentationState(
                    tabs,
                    TabSelectionResolution.Selected(requestedSelection),
                )
            }
            else -> {
                val repairedKey = tabs.first().id.value
                _selectedThreadTabKey.value = repairedKey
                _threadPresentationState.value = TabPresentationState(
                    tabs,
                    TabSelectionResolution.Selected(repairedKey),
                )
            }
        }
        syncThreadCurrentPageFromSelectedKey(tabs)
    }

    /** pending operation が説明できる選択 key を返す。 */
    private val ThreadTabPendingOperation.selectionKey: String?
        get() = when (this) {
            is ThreadTabPendingOperation.Ensure -> tab.id.value
            is ThreadTabPendingOperation.Delete -> threadId.value
            is ThreadTabPendingOperation.BulkDelete -> requestedSelection
            is ThreadTabPendingOperation.Pin -> threadId.value
            is ThreadTabPendingOperation.BulkPin -> null
            is ThreadTabPendingOperation.Info -> tab.id.value
            is ThreadTabPendingOperation.Reorder -> null
        }

    /** 各 pending operation が対象とする Thread ID 集合を返す。 */
    private val ThreadTabPendingOperation.targetThreadIds: Set<ThreadId>
        get() = when (this) {
            is ThreadTabPendingOperation.Ensure -> setOf(tab.id)
            is ThreadTabPendingOperation.Delete -> setOf(threadId)
            is ThreadTabPendingOperation.BulkDelete -> threadIds.toSet()
            is ThreadTabPendingOperation.Pin -> setOf(threadId)
            is ThreadTabPendingOperation.BulkPin -> threadIds.toSet()
            is ThreadTabPendingOperation.Info -> setOf(tab.id)
            is ThreadTabPendingOperation.Reorder -> threadIds.toSet()
        }

    /** 投影したメタデータを Repository 共通の ThreadState 更新入力へ変換する。 */
    private fun ThreadTabInfo.toThreadStateUpdate(): ThreadStateRepository.ThreadStateUpdate =
        ThreadStateRepository.ThreadStateUpdate(
            threadId = id,
            boardId = boardId,
            boardUrl = boardUrl,
            boardName = boardName,
            title = title,
            latestResCount = resCount,
        )

    /** coordinator scope の破棄で実行できなくなったキュー内の呼び出し元へ完了を通知する。 */
    private fun cancelIntentCompletion(intent: ThreadTabMutationIntent) {
        val exception = CancellationException("Thread tab coordinator was cancelled")
        when (intent) {
            is ThreadTabMutationIntent.Ensure -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Delete -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.BulkDelete -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Pin -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.BulkPin -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Info -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Reorder -> intent.completion.completeExceptionally(exception)
        }
    }

    /** キュー内の更新操作を開始する前に完了通知の所有状態を確認する。 */
    private fun isIntentCancelled(intent: ThreadTabMutationIntent): Boolean = when (intent) {
        is ThreadTabMutationIntent.Ensure -> intent.completion.isCancelled
        is ThreadTabMutationIntent.Delete -> intent.completion.isCancelled
            is ThreadTabMutationIntent.BulkDelete -> intent.completion.isCancelled
            is ThreadTabMutationIntent.Pin -> intent.completion.isCancelled
            is ThreadTabMutationIntent.BulkPin -> intent.completion.isCancelled
        is ThreadTabMutationIntent.Info -> intent.completion.isCancelled
        is ThreadTabMutationIntent.Reorder -> intent.completion.isCancelled
    }

    /**
     * selected key から互換用 currentPage を導出する。
     */
    private fun syncThreadCurrentPageFromSelectedKey(tabs: List<ThreadTabInfo> = _openThreadTabs.value) {
        val selectedKey = _selectedThreadTabKey.value
        _threadCurrentPage.value = when {
            tabs.isEmpty() -> -1
            selectedKey == null -> -1
            else -> tabs.indexOfFirst { it.id.value == selectedKey }
        }
    }

    /** coordinator の純粋な状態処理を検証する単体テスト向けに、未 bind の小さな接続点を提供する。 */
    private fun ensureThreadTabWithoutPersistence(tabInfo: ThreadTabInfo): Int {
        var targetIndex = -1
        _openThreadTabs.update { tabs ->
            val index = tabs.indexOfFirst { it.id == tabInfo.id }
            if (index >= 0) {
                targetIndex = index
                tabs.toMutableList().apply {
                    val existing = this[index]
                    this[index] = mergeThreadTabMetadata(existing, tabInfo)
                }
            } else {
                targetIndex = tabs.size
                tabs + tabInfo
            }
        }
        setThreadTabState(ThreadTabsLoadState.Loaded(_openThreadTabs.value))
        publishThreadPresentation()
        return targetIndex
    }

    /** 未 bind の単体テスト用接続点でタブを 1 件削除し、選択の後始末を維持する。 */
    private fun closeThreadTabWithoutPersistence(tab: ThreadTabInfo) {
        val key = tab.id.value
        val selectedKey = _selectedThreadTabKey.value
        val removedIndex = _openThreadTabs.value.indexOfFirst { it.id == tab.id }
        val updatedTabs = _openThreadTabs.value.filterNot { it.id == tab.id }
        val nextSelection = selectedThreadKeyAfterRemoval(
            selectedKey,
            key,
            removedIndex,
            updatedTabs,
        )
        publishThreadPresentation(updatedTabs, nextSelection)
        _newResCounts.update { it - key }
        _threadSessionStates.update { it - key }
        _threadRuntimeStates.update { it - key }
    }

    /** 未bind時のbulkテスト経路で、一覧順の選択補正と揮発状態cleanupを一度に行う。 */
    private fun closeThreadTabsWithoutPersistence(tabs: List<ThreadTabInfo>) {
        val threadIds = tabs.map { it.id }.distinctBy { it.value }
        val currentTabs = _openThreadTabs.value
        val updatedTabs = currentTabs.filterNot { it.id in threadIds }
        val nextSelection = selectionAfterTabRemovals(
            selectedKey = _selectedThreadTabKey.value?.let(::ThreadId),
            tabs = currentTabs,
            removedKeys = threadIds,
            keyOf = ThreadTabInfo::id,
        )?.value
        publishThreadPresentation(updatedTabs, nextSelection)
        threadIds.forEach { threadId ->
            val key = threadId.value
            _newResCounts.update { it - key }
            _threadSessionStates.update { it - key }
            _threadRuntimeStates.update { it - key }
        }
    }

    /** 削除前 index に基づく既存の隣接/末尾選択規則を返す。 */
    private fun selectedThreadKeyAfterRemoval(
        selectedKeyBeforeRemoval: String?,
        removedTabKey: String,
        removedIndex: Int,
        updatedTabs: List<ThreadTabInfo>,
    ): String? {
        val removedTabWasSelected = removedIndex >= 0 && selectedKeyBeforeRemoval == removedTabKey
        return when {
            updatedTabs.isEmpty() -> null
            !removedTabWasSelected && selectedKeyBeforeRemoval != null && updatedTabs.any { it.id.value == selectedKeyBeforeRemoval } -> selectedKeyBeforeRemoval
            removedIndex in updatedTabs.indices -> updatedTabs[removedIndex].id.value
            else -> updatedTabs.last().id.value
        }
    }
}
