package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.bbsroute.TabPresentationState
import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.tabs.model.mergeThreadTabMetadata
import com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult
import com.websarva.wings.android.slevo.ui.tabs.controller.TabControllerState
import com.websarva.wings.android.slevo.ui.tabs.controller.TabLoadPhase
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

    /** canonical、pending、selection、presentation を一つの論理 snapshot として公開する。 */
    private val _controllerState = MutableStateFlow(
        TabControllerState<ThreadTabInfo, String, ThreadTabPendingOperation>(
            loadPhase = TabLoadPhase.Loading,
            canonicalTabs = emptyList(),
            pendingCommands = emptyList(),
            selectedKey = null,
            presentation = TabPresentationState(emptyList(), TabSelectionResolution.Loading),
        )
    )
    /** Thread domain の pure state test と retained UI adapter が参照する state snapshot。 */
    internal val controllerState: StateFlow<TabControllerState<ThreadTabInfo, String, ThreadTabPendingOperation>> =
        _controllerState.asStateFlow()

    /** Room が最後に通知した一覧だけを正規スナップショットとして保持する。 */
    private val canonicalTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())
    private val pendingOperations = mutableListOf<ThreadTabPendingOperation>()
    private var snapshotVersion = 0L
    private val snapshotVersionFlow = MutableStateFlow(0L)
    private var pendingStateRevision = 0L
    private val pendingStateRevisionFlow = MutableStateFlow(0L)
    private val commandQueue = Channel<ThreadTabMutationIntent>(Channel.UNLIMITED)
    private var commandDispatcherJob: Job? = null

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

    /** Thread ensure の terminal state を presentation から独立した結果として返す。 */
    suspend fun ensureThreadTabCommand(route: AppRoute.Thread): TabCommandResult<Int> = try {
        TabCommandResult.Success(ensureThreadTab(route))
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (exception: Throwable) {
        TabCommandResult.Failure(exception)
    }

    /** Thread selection の結果を明示 command として返す。 */
    fun selectThreadTabCommand(threadId: ThreadId?): TabCommandResult<Unit> =
        if (selectThreadTab(threadId)) TabCommandResult.Success(Unit) else TabCommandResult.NoOp()

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

        /** 1 件の固定列を変更する。 */
        data class Pin(
            val threadId: ThreadId,
            override val completion: CompletableDeferred<Unit>,
        ) : ThreadTabMutationIntent

        /** JOIN 済みの ThreadState 投影を 1 件更新する。 */
        data class Info(
            val tab: ThreadTabInfo,
            val operation: ThreadTabPendingOperation.Info,
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
                // canonical confirmation は後続 command の write barrier ではない。
                scope?.launch(start = CoroutineStart.UNDISPATCHED) { processIntent(intent) }
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
            is ThreadTabMutationIntent.Pin -> processPin(intent)
            is ThreadTabMutationIntent.Info -> processInfo(intent)
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
        _controllerState.update { current ->
            current.copy(
                loadPhase = if (state is ThreadTabsLoadState.Loaded) TabLoadPhase.Loaded else TabLoadPhase.Loading,
                canonicalTabs = canonicalTabs.value,
            )
        }
    }

    /** DB 書き込み、Flow による確認、投影の後始末を通して存在保証操作を実行する。 */
    private suspend fun processEnsure(intent: ThreadTabMutationIntent.Ensure) {
        val baselineVersion = registerPending(intent.operation)
        try {
            if (!tabsRepository.ensureOpenThreadTab(intent.tab)) {
                throw IllegalStateException("Thread tab ensure failed")
            }
            awaitConfirmation(intent.operation, baselineVersion)
            removePending(intent.operation)
            intent.completion.complete(_openThreadTabs.value.indexOfFirst { it.id == intent.tab.id })
        } catch (exception: Throwable) {
            removePending(intent.operation)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** delete 操作を実行し、正規状態での削除確認後にだけ選択を補正する。 */
    private suspend fun processDelete(intent: ThreadTabMutationIntent.Delete) {
        val selectedKeyBeforeRemoval = _selectedThreadTabKey.value
        val removedIndex = canonicalTabs.value.indexOfFirst { it.id == intent.threadId }
        val baselineVersion = registerPending(intent.operation)
        try {
            val changed = tabsRepository.deleteOpenThreadTab(intent.threadId)
            if (changed) awaitConfirmation(intent.operation, baselineVersion)
            val updatedTabs = projectThreadTabs(
                canonicalTabs.value,
                pendingOperations.filterNot { it === intent.operation },
            )
            val nextSelection = selectedThreadKeyAfterRemoval(
                selectedKeyBeforeRemoval,
                intent.threadId.value,
                removedIndex,
                updatedTabs,
            )
            removePending(intent.operation, nextSelection)
            _newResCounts.update { it - intent.threadId.value }
            _threadSessionStates.update { it - intent.threadId.value }
            _threadRuntimeStates.update { it - intent.threadId.value }
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(intent.operation)
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
        val baselineVersion = registerPending(operation)
        try {
            val changed = tabsRepository.setThreadTabPinned(intent.threadId, operation.isPinned)
            if (changed) awaitConfirmation(operation, baselineVersion)
            removePending(operation)
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(operation)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** ThreadState を更新し、JOIN されたタブ Flow に値が反映されるまで待つ。 */
    private suspend fun processInfo(intent: ThreadTabMutationIntent.Info) {
        val baselineVersion = registerPending(intent.operation)
        try {
            tabsRepository.updateThreadState(intent.tab.toThreadStateUpdate())
            awaitConfirmation(intent.operation, baselineVersion)
            removePending(intent.operation)
            intent.completion.complete(Unit)
        } catch (exception: Throwable) {
            removePending(intent.operation)
            intent.completion.completeExceptionally(exception)
        }
    }

    /** 保留中の操作を 1 件追加し、投影した一覧を再発行する。 */
    private fun registerPending(operation: ThreadTabPendingOperation): Long {
        pendingOperations += operation
        pendingStateRevision += 1
        pendingStateRevisionFlow.value = pendingStateRevision
        publishProjectedTabs()
        return snapshotVersion
    }

    /** 完了または失敗した操作を 1 件削除し、正規状態の投影を再発行する。 */
    private fun removePending(
        operation: ThreadTabPendingOperation,
        requestedSelection: String? = _selectedThreadTabKey.value,
    ) {
        val operationIndex = pendingOperations.indexOfFirst { pendingOperation -> pendingOperation === operation }
        if (operationIndex >= 0) pendingOperations.removeAt(operationIndex)
        pendingStateRevision += 1
        pendingStateRevisionFlow.value = pendingStateRevision
        publishProjectedTabs(requestedSelection)
    }

    /** 操作固有の条件を満たす新しい Room 通知を待つ。 */
    private suspend fun awaitConfirmation(
        operation: ThreadTabPendingOperation,
        baselineVersion: Long,
    ) {
        combine(snapshotVersionFlow, pendingStateRevisionFlow) { version, _ -> version }.first { version ->
            version > baselineVersion &&
                isThreadTabOperationConfirmed(canonicalTabs.value, operation) &&
                !hasEarlierPendingOperation(operation)
        }
    }

    /** 同一 ThreadId の先行 pending が残っている間は後続操作の確認を保留する。 */
    private fun hasEarlierPendingOperation(operation: ThreadTabPendingOperation): Boolean {
        val operationIndex = pendingOperations.indexOfFirst { pendingOperation -> pendingOperation === operation }
        if (operationIndex <= 0) return false
        val confirmationKey = operation.confirmationKey
        return pendingOperations
            .subList(0, operationIndex)
            .any { pendingOperation -> pendingOperation.confirmationKey == confirmationKey }
    }

    /** canonicalTabs を変更せず、保留中の投影だけを発行する。 */
    private fun publishProjectedTabs(requestedSelection: String? = _selectedThreadTabKey.value) {
        val projected = projectThreadTabs(canonicalTabs.value, pendingOperations)
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
            _controllerState.value = _controllerState.value.copy(
                canonicalTabs = canonicalTabs.value,
                pendingCommands = pendingOperations.toList(),
                selectedKey = _selectedThreadTabKey.value,
                presentation = _threadPresentationState.value,
            )
            return
        }
        when {
            requestedSelection != null &&
                tabs.none { it.id.value == requestedSelection } &&
                pendingOperations.any { operation -> operation.selectionKey == requestedSelection } -> {
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
        _controllerState.value = _controllerState.value.copy(
            canonicalTabs = canonicalTabs.value,
            pendingCommands = pendingOperations.toList(),
            selectedKey = _selectedThreadTabKey.value,
            presentation = _threadPresentationState.value,
        )
    }

    /** pending operation が説明できる選択 key を返す。 */
    private val ThreadTabPendingOperation.selectionKey: String?
        get() = confirmationKey.value

    /** 全 pending 操作が共有する、因果順確認用の ThreadId キーを返す。 */
    private val ThreadTabPendingOperation.confirmationKey: ThreadId
        get() = when (this) {
            is ThreadTabPendingOperation.Ensure -> tab.id
            is ThreadTabPendingOperation.Delete -> threadId
            is ThreadTabPendingOperation.Pin -> threadId
            is ThreadTabPendingOperation.Info -> tab.id
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
            is ThreadTabMutationIntent.Pin -> intent.completion.completeExceptionally(exception)
            is ThreadTabMutationIntent.Info -> intent.completion.completeExceptionally(exception)
        }
    }

    /** キュー内の更新操作を開始する前に完了通知の所有状態を確認する。 */
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
