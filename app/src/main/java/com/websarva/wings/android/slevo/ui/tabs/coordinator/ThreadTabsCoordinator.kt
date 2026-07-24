package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.tabs.model.mergeThreadTabMetadata
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
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
    private val datRepository: DatRepository,
    private val threadStateRepository: ThreadStateRepository,
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

    private val _threadSessionStates = MutableStateFlow<Map<String, ThreadSessionState>>(emptyMap())
    val threadSessionStates: StateFlow<Map<String, ThreadSessionState>> = _threadSessionStates.asStateFlow()

    private val _threadRuntimeStates = MutableStateFlow<Map<String, ThreadSessionRuntimeState>>(emptyMap())
    val threadRuntimeStates: StateFlow<Map<String, ThreadSessionRuntimeState>> = _threadRuntimeStates.asStateFlow()

    private val _threadCurrentPage = MutableStateFlow(-1)

    private val _threadPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val threadPageAnimation: SharedFlow<Int> = _threadPageAnimation.asSharedFlow()

    private var scope: CoroutineScope? = null

    /** Room が最後に通知した一覧だけを canonical snapshot として保持する。 */
    private val canonicalTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())
    private val pendingOperations = mutableListOf<ThreadTabPendingOperation>()
    private var canonicalRevision = 0L
    private val canonicalRevisionFlow = MutableStateFlow(0L)
    private val mutationIntents = Channel<ThreadTabMutationIntent>(Channel.UNLIMITED)
    private var mutationWorker: Job? = null

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
        mutationWorker = scope.launch { processMutationIntents() }
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
                canonicalRevision += 1
                canonicalRevisionFlow.value = canonicalRevision
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
        // Tests can use the coordinator without binding a lifecycle scope; production always binds first.
        if (scope == null) return ensureThreadTabWithoutPersistence(tabInfo)
        val operation = ThreadTabPendingOperation.Ensure(tabInfo)
        val completion = CompletableDeferred<Int>()
        mutationIntents.send(ThreadTabMutationIntent.Ensure(tabInfo, operation, completion))
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
        mutationIntents.send(ThreadTabMutationIntent.Delete(tab.id, operation, completion))
        try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /**
     * 選択中のスレッドタブ key を更新する。
     */
    /**
     * Selects a currently visible canonical tab and reports whether the target existed.
     * A missing non-null target leaves the previous selection untouched.
     */
    fun selectThreadTab(threadId: ThreadId?): Boolean {
        if (threadId == null) {
            _selectedThreadTabKey.value = null
            syncThreadCurrentPageFromSelectedKey()
            return true
        }
        val availableTabs = if (scope == null) _openThreadTabs.value else canonicalTabs.value
        if (!availableTabs.any { it.id == threadId }) return false
        _selectedThreadTabKey.value = threadId.value
        syncThreadCurrentPageFromSelectedKey()
        return true
    }

    /** Returns whether Room has confirmed the requested thread tab. */
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
                    val result = datRepository.getThread(tab.boardUrl, tab.threadKey)
                    val latestResCount = result?.first?.size
                    val isTabStillOpen = _openThreadTabs.value.any { it.id == tab.id }
                    if (latestResCount != null && isTabStillOpen) {
                        // 削除済みタブには反映せず、開いているタブのみ更新対象にする。
                        // 取得済みの最新レス数を thread_states に保存する。
                        val update = ThreadStateRepository.ThreadStateUpdate(
                            threadId = tab.id,
                            boardId = tab.boardId,
                            boardUrl = tab.boardUrl,
                            boardName = tab.boardName,
                            title = tab.title,
                            latestResCount = latestResCount,
                        )
                        threadStateRepository.saveThreadState(update)
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

    /**
     * 指定した ThreadId のスレッドタブの固定状態を切り替えて保存する。
     */
    suspend fun togglePinThreadTab(threadId: ThreadId) {
        val current = _openThreadTabs.value.firstOrNull { it.id == threadId } ?: return
        if (scope == null) {
            _openThreadTabs.update { tabs ->
                tabs.map { tab -> if (tab.id == threadId) tab.copy(isPinned = !tab.isPinned) else tab }
            }
            return
        }
        val operation = ThreadTabPendingOperation.Pin(threadId, !current.isPinned)
        val completion = CompletableDeferred<Unit>()
        mutationIntents.send(
            ThreadTabMutationIntent.Pin(threadId, !current.isPinned, operation, completion)
        )
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
        mutationIntents.send(ThreadTabMutationIntent.Info(updated, operation, completion))
        try {
            completion.await()
        } catch (cancellationException: CancellationException) {
            completion.cancel(cancellationException)
            throw cancellationException
        }
    }

    /** One queued mutation and the completion owned by its caller. */
    private sealed interface ThreadTabMutationIntent {
        val completion: CompletableDeferred<*>

        /** Adds or ensures one thread tab. */
        data class Ensure(
            val tab: ThreadTabInfo,
            val operation: ThreadTabPendingOperation.Ensure,
            override val completion: CompletableDeferred<Int>,
        ) : ThreadTabMutationIntent

        /** Deletes one thread tab. */
        data class Delete(
            val threadId: ThreadId,
            val operation: ThreadTabPendingOperation.Delete,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** Changes one pin column. */
        data class Pin(
            val threadId: ThreadId,
            val isPinned: Boolean,
            val operation: ThreadTabPendingOperation.Pin,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** Updates one joined ThreadState projection. */
        data class Info(
            val tab: ThreadTabInfo,
            val operation: ThreadTabPendingOperation.Info,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent
    }

    /**
     * Processes every mutation in FIFO order and keeps pending projection cleanup local to the worker.
     * Room remains the only writer of the canonical snapshot.
     */
    private suspend fun processMutationIntents() {
        try {
            for (intent in mutationIntents) {
                if (isIntentCancelled(intent)) continue
                awaitLoadedState()
                // Readiness may have released concurrently with caller cancellation.
                if (isIntentCancelled(intent)) continue
                processIntentInCancellableOperation(intent)
            }
        } finally {
            // Teardown must not leave callers suspended on completions that can no longer finish.
            while (true) {
                val intent = mutationIntents.tryReceive().getOrNull() ?: break
                cancelIntentCompletion(intent)
            }
            pendingOperations.clear()
            publishProjectedTabs()
        }
    }

    /** Runs one intent in an independently cancellable child while the FIFO worker remains alive. */
    private suspend fun processIntentInCancellableOperation(intent: ThreadTabMutationIntent) {
        coroutineScope {
            val operation = launch(start = CoroutineStart.LAZY) {
                when (intent) {
                    is ThreadTabMutationIntent.Ensure -> processEnsure(intent)
                    is ThreadTabMutationIntent.Delete -> processDelete(intent)
                    is ThreadTabMutationIntent.Pin -> processPin(intent)
                    is ThreadTabMutationIntent.Info -> processInfo(intent)
                }
            }
            val cancellationLink = intent.completion.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    // Only this intent is cancelled; the long-lived FIFO worker continues.
                    operation.cancel(cause)
                }
            }
            try {
                operation.start()
                operation.join()
            } finally {
                cancellationLink.dispose()
            }
        }
    }

    /** Waits for the first canonical Room snapshot instead of treating an empty startup list as loaded. */
    private suspend fun awaitLoadedState() {
        _threadTabState.filter { it is ThreadTabsLoadState.Loaded }.first()
    }

    /** Updates the explicit load state and its legacy boolean projection together. */
    private fun setThreadTabState(state: ThreadTabsLoadState) {
        _threadTabState.value = state
        _threadLoaded.value = state is ThreadTabsLoadState.Loaded
    }

    /** Runs an ensure operation through DB write, Flow confirmation, and projection cleanup. */
    private suspend fun processEnsure(intent: ThreadTabMutationIntent.Ensure) {
        val baselineRevision = registerPending(intent.operation)
        try {
            if (!tabsRepository.ensureOpenThreadTab(intent.tab)) {
                throw IllegalStateException("Thread tab ensure failed")
            }
            if (intent.completion.isCancelled) {
                removePending(intent.operation)
                return
            }
            awaitConfirmation(intent.operation, baselineRevision, intent.completion)
            removePending(intent.operation)
            intent.completion.complete(_openThreadTabs.value.indexOfFirst { it.id == intent.tab.id })
        } catch (exception: Throwable) {
            removePending(intent.operation)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** Runs a delete operation and adjusts selection only after canonical deletion is confirmed. */
    private suspend fun processDelete(intent: ThreadTabMutationIntent.Delete) {
        val selectedKeyBeforeRemoval = _selectedThreadTabKey.value
        val removedIndex = canonicalTabs.value.indexOfFirst { it.id == intent.threadId }
        val baselineRevision = registerPending(intent.operation)
        try {
            val changed = tabsRepository.deleteOpenThreadTab(intent.threadId)
            if (intent.completion.isCancelled) {
                removePending(intent.operation)
                return
            }
            if (changed) awaitConfirmation(intent.operation, baselineRevision, intent.completion)
            removePending(intent.operation)
            _newResCounts.update { it - intent.threadId.value }
            _threadSessionStates.update { it - intent.threadId.value }
            _threadRuntimeStates.update { it - intent.threadId.value }
            updateSelectedThreadKeyAfterRemoval(
                selectedKeyBeforeRemoval = selectedKeyBeforeRemoval,
                removedTabKey = intent.threadId.value,
                removedIndex = removedIndex,
                updatedTabs = _openThreadTabs.value,
            )
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(intent.operation)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** Runs a pin operation and waits for the requested value in the canonical snapshot. */
    private suspend fun processPin(intent: ThreadTabMutationIntent.Pin) {
        val baselineRevision = registerPending(intent.operation)
        try {
            val changed = tabsRepository.setThreadTabPinned(intent.threadId, intent.isPinned)
            if (intent.completion.isCancelled) {
                removePending(intent.operation)
                return
            }
            if (changed) awaitConfirmation(intent.operation, baselineRevision, intent.completion)
            removePending(intent.operation)
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(intent.operation)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** Runs a ThreadState update and waits for the joined tab Flow to expose its values. */
    private suspend fun processInfo(intent: ThreadTabMutationIntent.Info) {
        val baselineRevision = registerPending(intent.operation)
        try {
            tabsRepository.updateThreadState(intent.tab.toThreadStateUpdate())
            if (intent.completion.isCancelled) {
                removePending(intent.operation)
                return
            }
            awaitConfirmation(intent.operation, baselineRevision, intent.completion)
            removePending(intent.operation)
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(intent.operation)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** Adds one pending operation and republishes the projected list. */
    private fun registerPending(operation: ThreadTabPendingOperation): Long {
        pendingOperations += operation
        publishProjectedTabs()
        return canonicalRevision
    }

    /** Removes one completed or failed operation and republishes the canonical projection. */
    private fun removePending(operation: ThreadTabPendingOperation) {
        pendingOperations.remove(operation)
        publishProjectedTabs()
    }

    /** Waits for a newer Room emission that satisfies the operation-specific predicate. */
    private suspend fun awaitConfirmation(
        operation: ThreadTabPendingOperation,
        baselineRevision: Long,
        completion: CompletableDeferred<*>? = null,
    ) {
        canonicalRevisionFlow.first { revision ->
            if (completion?.isCancelled == true) {
                throw CancellationException("Thread tab mutation caller was cancelled")
            }
            revision > baselineRevision &&
                isThreadTabOperationConfirmed(canonicalTabs.value, operation)
        }
    }

    /** Publishes only the pending projection while keeping canonicalTabs untouched. */
    private fun publishProjectedTabs() {
        val projected = projectThreadTabs(canonicalTabs.value, pendingOperations)
        _openThreadTabs.value = projected
        syncThreadCurrentPageFromSelectedKey(projected)
        _newResCounts.value = projected
            .filter { tab -> tab.newResCount > 0 }
            .associate { tab -> tab.id.value to tab.newResCount }
    }

    /** Converts projected metadata to the repository's common ThreadState update input. */
    private fun ThreadTabInfo.toThreadStateUpdate(): ThreadStateRepository.ThreadStateUpdate =
        ThreadStateRepository.ThreadStateUpdate(
            threadId = id,
            boardId = boardId,
            boardUrl = boardUrl,
            boardName = boardName,
            title = title,
            latestResCount = resCount,
        )

    /** Completes queued callers when coordinator scope teardown prevents execution. */
    private fun cancelIntentCompletion(intent: ThreadTabMutationIntent) {
        val exception = CancellationException("Thread tab coordinator was cancelled")
        when (intent) {
            is ThreadTabMutationIntent.Ensure -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Delete -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Pin -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Info -> intent.completion.completeExceptionally(exception)
        }
    }

    /** Checks completion ownership before starting a queued mutation. */
    private fun isIntentCancelled(intent: ThreadTabMutationIntent): Boolean = when (intent) {
        is ThreadTabMutationIntent.Ensure -> intent.completion.isCancelled
        is ThreadTabMutationIntent.Delete -> intent.completion.isCancelled
        is ThreadTabMutationIntent.Pin -> intent.completion.isCancelled
        is ThreadTabMutationIntent.Info -> intent.completion.isCancelled
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

    /** Provides a small non-bound seam for unit tests that exercise pure coordinator state handling. */
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
        return targetIndex
    }

    /** Removes one tab in the unbound unit-test seam while preserving selection cleanup behavior. */
    private fun closeThreadTabWithoutPersistence(tab: ThreadTabInfo) {
        val key = tab.id.value
        val selectedKey = _selectedThreadTabKey.value
        val removedIndex = _openThreadTabs.value.indexOfFirst { it.id == tab.id }
        _openThreadTabs.update { tabs -> tabs.filterNot { it.id == tab.id } }
        _newResCounts.update { it - key }
        _threadSessionStates.update { it - key }
        _threadRuntimeStates.update { it - key }
        updateSelectedThreadKeyAfterRemoval(selectedKey, key, removedIndex, _openThreadTabs.value)
    }

    /**
     * タブ削除後に selected key を補正する。
     */
    private fun updateSelectedThreadKeyAfterRemoval(
        selectedKeyBeforeRemoval: String?,
        removedTabKey: String,
        removedIndex: Int,
        updatedTabs: List<ThreadTabInfo>,
    ) {
        val removedTabWasSelected = removedIndex >= 0 && selectedKeyBeforeRemoval == removedTabKey

        _selectedThreadTabKey.value = when {
            updatedTabs.isEmpty() -> null
            !removedTabWasSelected && selectedKeyBeforeRemoval != null && updatedTabs.any { it.id.value == selectedKeyBeforeRemoval } -> selectedKeyBeforeRemoval
            removedIndex in updatedTabs.indices -> updatedTabs[removedIndex].id.value
            else -> updatedTabs.last().id.value
        }
        syncThreadCurrentPageFromSelectedKey(updatedTabs)
    }
}
