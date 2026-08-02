package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.TabMutationResult
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.bbsroute.TabPresentationState
import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.controller.IndexedTabOperation
import com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandId
import com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandLifecycle
import com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult
import com.websarva.wings.android.slevo.ui.tabs.controller.TabControllerState
import com.websarva.wings.android.slevo.ui.tabs.controller.TabLoadPhase
import com.websarva.wings.android.slevo.ui.tabs.controller.foldEffectiveTabs
import com.websarva.wings.android.slevo.ui.tabs.controller.resolveTabPresentation
import com.websarva.wings.android.slevo.ui.tabs.controller.selectionAfterTabRemoval
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.mergeBoardTabMetadata
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Board タブの domain Controller。
 * Room canonical snapshot、pending command、選択、presentation を一つの state から派生させる。
 * 通常 command は対象行 repository API を Controller scope が所有して実行する。
 */
@ActivityRetainedScoped
class BoardTabsCoordinator @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val bookmarkBoardRepository: BookmarkBoardRepository,
) {
    private data class BoardPendingOperation(
        val id: TabCommandId,
        val lifecycle: TabCommandLifecycle,
        val operation: Operation,
        val result: CompletableDeferred<TabCommandResult<Int>>,
    )

    private sealed interface Operation {
        data class Ensure(val tab: BoardTabInfo) : Operation
        data class Delete(val boardUrl: String, val requestedSelection: String?) : Operation
        data class Pin(val boardUrl: String, val isPinned: Boolean) : Operation
        data class Info(val tab: BoardTabInfo) : Operation
        data class Scroll(val boardUrl: String, val index: Int, val offset: Int) : Operation
    }

    /**
     * 同一 Board の同一 targeted write を supersede するための内部 key。
     * Board URL と更新種別の両方を含め、異なる更新種別の pending は独立して保持する。
     */
    private data class BoardSupersessionKey(
        val boardUrl: String,
        val kind: Kind,
    ) {
        /** Board command の targeted write 種別を表す。 */
        enum class Kind {
            Scroll,
            Pin,
            Info,
        }
    }

    private val commandIds = AtomicLong(0)
    private val controllerScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
    private val _state = MutableStateFlow(
        TabControllerState<BoardTabInfo, String, BoardPendingOperation>(
            loadPhase = TabLoadPhase.Loading,
            canonicalTabs = emptyList(),
            pendingCommands = emptyList(),
            selectedKey = null,
            presentation = TabPresentationState(emptyList(), TabSelectionResolution.Loading),
        )
    )

    /** Room canonical state と pending を投影した Board タブ一覧。 */
    val openBoardTabs: StateFlow<List<BoardTabInfo>> = _state.map(::effectiveTabs).stateIn(
        controllerScope,
        SharingStarted.Eagerly,
        emptyList(),
    )
    /** 初回 canonical snapshot を受け取ったかどうか。 */
    val boardLoaded: StateFlow<Boolean> = _state.map { it.loadPhase == TabLoadPhase.Loaded }.stateIn(
        controllerScope,
        SharingStarted.Eagerly,
        false,
    )
    /** Controller state から導出した選択 key。 */
    val selectedBoardTabKey: StateFlow<String?> = _state.map { it.selectedKey }.stateIn(
        controllerScope,
        SharingStarted.Eagerly,
        null,
    )
    /** 一覧と selection resolution を同一 state transition から公開する。 */
    val boardPresentationState: StateFlow<TabPresentationState<BoardTabInfo, String>> =
        _state.map { it.presentation }.stateIn(
            controllerScope,
            SharingStarted.Eagerly,
            TabPresentationState(emptyList(), TabSelectionResolution.Loading),
        )

    private val _boardSessionStates = MutableStateFlow<Map<String, BoardSessionState>>(emptyMap())
    val boardSessionStates: StateFlow<Map<String, BoardSessionState>> = _boardSessionStates.asStateFlow()
    private val _boardPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val boardPageAnimation: SharedFlow<Int> = _boardPageAnimation.asSharedFlow()
    private var boundScope: CoroutineScope? = null

    /**
     * Activity retained scopeへ bindし、Room canonical Flowを一度だけ購読する。
     * bookmark 色は canonical tab に合成するが、tab の正本と command state は一つに保つ。
     */
    fun bind(scope: CoroutineScope) {
        if (boundScope != null) return
        boundScope = scope
        scope.launch {
            combine(
                tabsRepository.observeOpenBoardTabs(),
                bookmarkBoardRepository.observeGroupsWithBoards(),
            ) { tabs, groups ->
                val colors = buildMap<Long, String> {
                    groups.forEach { group ->
                        group.boards.forEach { board -> put(board.boardId, group.group.colorName) }
                    }
                }
                tabs.map { tab -> tab.copy(bookmarkColorName = colors[tab.boardId]) }
            }.collect { canonical ->
                reconcileCanonical(canonical)
            }
        }
    }

    /** Board route を command として受理し、canonical 確認まで明示結果を待つ。 */
    suspend fun ensureBoardTabCommand(route: AppRoute.Board): TabCommandResult<Int> =
        accept(ensureOperation(route.toTabInfo()))

    /**
     * 既存 UI 互換の即時 API。
     * command を Controller が所有し、戻り値は受理直後の effective index とする。
     */
    fun ensureBoardTab(route: AppRoute.Board): Int {
        val tab = route.toTabInfo()
        if (boundScope == null) {
            applyUnboundEnsure(tab)
            return effectiveTabs(_state.value).indexOfFirst { it.boardUrl == tab.boardUrl }
        }
        val pending = acceptWithoutWaiting(ensureOperation(tab))
        return effectiveTabs(_state.value).indexOfFirst { it.boardUrl == tab.boardUrl }.takeIf { it >= 0 }
            ?: pending
    }

    /** BoardTabInfo を command として保証する互換入口。 */
    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        if (boundScope == null) applyUnboundEnsure(boardTabInfo) else acceptWithoutWaiting(ensureOperation(boardTabInfo))
    }

    /** Board selection を state event として適用する。 */
    fun selectBoardTab(boardUrl: String?) {
        _state.update { state -> state.copy(selectedKey = boardUrl).rebuildPresentation() }
    }

    /** Board selection を明示 command result として返す。 */
    fun selectBoardTabCommand(boardUrl: String?): TabCommandResult<Unit> {
        if (boardUrl != null && effectiveTabs(_state.value).none { it.boardUrl == boardUrl }) {
            return TabCommandResult.NoOp()
        }
        selectBoardTab(boardUrl)
        return TabCommandResult.Success(Unit)
    }

    /** Board tab close を受理し、session cleanup は canonical confirmation 後に行う。 */
    fun closeBoardTab(tab: BoardTabInfo) {
        if (boundScope == null) {
            val tabs = effectiveTabs(_state.value)
            val index = tabs.indexOfFirst { it.boardUrl == tab.boardUrl }
            val next = selectionAfterTabRemoval(_state.value.selectedKey, tab.boardUrl, index, tabs.filterNot { it.boardUrl == tab.boardUrl }) { it.boardUrl }
            _state.update { it.copy(canonicalTabs = tabs.filterNot { it.boardUrl == tab.boardUrl }, selectedKey = next).rebuildPresentation() }
            _boardSessionStates.update { it - tab.boardUrl }
            return
        }
        val tabs = effectiveTabs(_state.value)
        val removedIndex = tabs.indexOfFirst { it.boardUrl == tab.boardUrl }
        val remainingTabs = tabs.filterNot { it.boardUrl == tab.boardUrl }
        val requestedSelection = selectionAfterTabRemoval(
            _state.value.selectedKey,
            tab.boardUrl,
            removedIndex,
            remainingTabs,
        ) { it.boardUrl }
        acceptWithoutWaiting(Operation.Delete(tab.boardUrl, requestedSelection))
    }

    /** boardUrl から対象 tab を探して close command を受理する。 */
    fun closeBoardTabByUrl(boardUrl: String) {
        effectiveTabs(_state.value).firstOrNull { it.boardUrl == boardUrl }?.let(::closeBoardTab)
    }

    /** Board pin を effective state から反転し、targeted command を受理する。 */
    fun togglePinBoardTab(boardUrl: String) {
        val current = effectiveTabs(_state.value).firstOrNull { it.boardUrl == boardUrl } ?: return
        if (boundScope == null) {
            val updated = current.copy(isPinned = !current.isPinned)
            _state.update { state -> state.copy(canonicalTabs = state.canonicalTabs.map { if (it.boardUrl == boardUrl) updated else it }).rebuildPresentation() }
        } else {
            acceptWithoutWaiting(Operation.Pin(boardUrl, !current.isPinned))
        }
    }

    /** Board の対象行だけの scroll command を受理する。 */
    fun updateBoardScrollPosition(boardUrl: String, firstVisibleIndex: Int, scrollOffset: Int) {
        if (boundScope == null) {
            _state.update { state ->
                state.copy(canonicalTabs = state.canonicalTabs.map {
                    if (it.boardUrl == boardUrl) it.copy(firstVisibleItemIndex = firstVisibleIndex, firstVisibleItemScrollOffset = scrollOffset) else it
                }).rebuildPresentation()
            }
        } else acceptWithoutWaiting(Operation.Scroll(boardUrl, firstVisibleIndex, scrollOffset))
    }

    /** 解決済み Board metadata を targeted command として反映する。 */
    fun updateBoardResolvedInfo(boardUrl: String, boardId: Long, boardName: String? = null) {
        if (boardId == 0L) return
        val current = effectiveTabs(_state.value).firstOrNull { it.boardUrl == boardUrl } ?: return
        val updated = current.copy(boardId = boardId, boardName = boardName?.takeIf(String::isNotBlank) ?: current.boardName)
        if (boundScope == null) {
            _state.update { state -> state.copy(canonicalTabs = state.canonicalTabs.map { if (it.boardUrl == boardUrl) updated else it }).rebuildPresentation() }
        } else acceptWithoutWaiting(Operation.Info(updated))
    }

    /** Board の揮発 session state を取得する。 */
    fun getBoardSessionState(boardUrl: String): BoardSessionState = _boardSessionStates.value[boardUrl] ?: BoardSessionState()

    /** Board の揮発 session state を更新する。 */
    fun updateBoardSessionState(boardUrl: String, transform: (BoardSessionState) -> BoardSessionState) {
        _boardSessionStates.update { states -> states + (boardUrl to transform(states[boardUrl] ?: BoardSessionState())) }
    }

    /** 現在 page から指定 offset の page animation を発行する。 */
    fun animateBoardPage(offset: Int) {
        val state = _state.value
        val tabs = effectiveTabs(state)
        val current = tabs.indexOfFirst { it.boardUrl == state.selectedKey }
        if (current < 0) return
        val target = current + offset
        if (target !in tabs.indices) return
        boundScope?.launch { _boardPageAnimation.emit(target) }
    }

    private fun acceptWithoutWaiting(operation: Operation): Int {
        val pending = register(operation)
        boundScope?.launch { execute(pending) }
        return when (operation) {
            is Operation.Ensure -> effectiveTabs(_state.value).indexOfFirst { it.boardUrl == operation.tab.boardUrl }
            else -> 0
        }
    }

    /** Board ensure payload を domain operation へ変換する。 */
    private fun ensureOperation(tab: BoardTabInfo): Operation = Operation.Ensure(tab)

    private suspend fun accept(operation: Operation): TabCommandResult<Int> {
        if (boundScope == null) {
            val index = when (operation) {
                is Operation.Ensure -> { applyUnboundEnsure(operation.tab); effectiveTabs(_state.value).indexOfFirst { it.boardUrl == operation.tab.boardUrl } }
                else -> 0
            }
            return TabCommandResult.Success(index)
        }
        val pending = register(operation)
        boundScope?.launch { execute(pending) }
        return pending.result.await()
    }

    private fun register(operation: Operation): BoardPendingOperation {
        val pending = BoardPendingOperation(
            id = TabCommandId(commandIds.incrementAndGet()),
            lifecycle = TabCommandLifecycle.Accepted,
            operation = operation,
            result = CompletableDeferred(),
        )
        var superseded = emptyList<BoardPendingOperation>()
        _state.update { state ->
            val key = operation.supersessionKey()
            superseded = state.pendingCommands.filter { it.operation.supersessionKey() == key && key != null }
            val selectedKey = if (operation is Operation.Delete) operation.requestedSelection else state.selectedKey
            state.copy(
                pendingCommands = state.pendingCommands.filterNot { it in superseded } + pending,
                selectedKey = selectedKey,
            ).rebuildPresentation()
        }
        // supersede 済み waiter は個別 canonical 通知を待たず、obsolete intent として解放する。
        superseded.forEach { it.result.complete(TabCommandResult.NoOp()) }
        return pending
    }

    /** Controller が所有する targeted repository effect を実行し、caller cancellation から分離する。 */
    private suspend fun execute(pending: BoardPendingOperation) {
        // 初回 Room snapshot 前は空一覧を canonical とみなさず、write を開始しない。
        _state.first { it.loadPhase == TabLoadPhase.Loaded }
        // load 待ち中に supersede された command は targeted write を開始しない。
        if (_state.value.pendingCommands.none { it.id == pending.id }) return
        val mutation = runCatching {
            when (val operation = pending.operation) {
                is Operation.Ensure -> tabsRepository.ensureOpenBoardTab(operation.tab)
                is Operation.Delete -> tabsRepository.deleteOpenBoardTab(operation.boardUrl)
                is Operation.Pin -> tabsRepository.setBoardTabPinned(operation.boardUrl, operation.isPinned)
                is Operation.Info -> tabsRepository.updateBoardTabInfo(operation.tab)
                is Operation.Scroll -> tabsRepository.updateBoardTabScrollPosition(operation.boardUrl, operation.index, operation.offset)
            }
        }.getOrElse { TabMutationResult.Failure(it) }
        // dispatch 済み command が遅れて終わっても、最新 command の state を復活させない。
        if (_state.value.pendingCommands.none { it.id == pending.id }) return
        when (mutation) {
            TabMutationResult.Success -> Unit
            TabMutationResult.NoOp -> finish(pending, TabCommandResult.NoOp(indexFor(pending.operation)))
            is TabMutationResult.Failure -> finish(pending, TabCommandResult.Failure(mutation.cause))
        }
        if (mutation != TabMutationResult.Success) return
        _state.update { state -> state.copy(pendingCommands = state.pendingCommands.map { if (it.id == pending.id) it.copy(lifecycle = TabCommandLifecycle.CommittedAwaitingCanonical) else it }) }
        reconcileCanonical(_state.value.canonicalTabs)
    }

    /**
     * Scroll、Pin、Info だけを Board と更新種別の key に分類する。
     * Ensure と Delete は lifecycle と selection の契約があるため supersession 対象外とする。
     */
    private fun Operation.supersessionKey(): BoardSupersessionKey? = when (this) {
        is Operation.Ensure -> null
        is Operation.Delete -> null
        is Operation.Pin -> BoardSupersessionKey(boardUrl, BoardSupersessionKey.Kind.Pin)
        is Operation.Info -> BoardSupersessionKey(tab.boardUrl, BoardSupersessionKey.Kind.Info)
        is Operation.Scroll -> BoardSupersessionKey(boardUrl, BoardSupersessionKey.Kind.Scroll)
    }

    private fun finish(pending: BoardPendingOperation, result: TabCommandResult<Int>) {
        _state.update { state ->
            state.copy(
                pendingCommands = state.pendingCommands.filterNot { it.id == pending.id },
            ).rebuildPresentation()
        }
        pending.result.complete(result)
        val operation = pending.operation
        if (operation is Operation.Delete) _boardSessionStates.update { it - operation.boardUrl }
    }

    /** Room snapshot を一度だけ state に取り込み、matching pending だけを terminal にする。 */
    private fun reconcileCanonical(canonicalTabs: List<BoardTabInfo>) {
        _state.update { it.copy(loadPhase = TabLoadPhase.Loaded, canonicalTabs = canonicalTabs).rebuildPresentation() }
        val pending = _state.value.pendingCommands
        pending.forEach { operation ->
            if (operation.lifecycle == TabCommandLifecycle.CommittedAwaitingCanonical && isConfirmed(canonicalTabs, operation.operation)) {
                finish(operation, TabCommandResult.Success(indexFor(operation.operation)))
            }
        }
    }

    private fun isConfirmed(canonical: List<BoardTabInfo>, operation: Operation): Boolean {
        val actual = when (operation) {
            is Operation.Ensure -> canonical.firstOrNull { it.boardUrl == operation.tab.boardUrl }
            is Operation.Delete -> canonical.firstOrNull { it.boardUrl == operation.boardUrl }
            is Operation.Pin -> canonical.firstOrNull { it.boardUrl == operation.boardUrl }
            is Operation.Info -> canonical.firstOrNull { it.boardUrl == operation.tab.boardUrl }
            is Operation.Scroll -> canonical.firstOrNull { it.boardUrl == operation.boardUrl }
        }
        return when (operation) {
            is Operation.Ensure -> actual != null && actual.boardId == (operation.tab.boardId.takeIf { it != 0L } ?: actual.boardId)
            is Operation.Delete -> actual == null
            is Operation.Pin -> actual?.isPinned == operation.isPinned
            is Operation.Info -> actual != null && actual.boardId == operation.tab.boardId && actual.boardName == operation.tab.boardName
            is Operation.Scroll -> actual?.firstVisibleItemIndex == operation.index && actual.firstVisibleItemScrollOffset == operation.offset
        }
    }

    private fun indexFor(operation: Operation): Int = when (operation) {
        is Operation.Ensure -> effectiveTabs(_state.value).indexOfFirst { it.boardUrl == operation.tab.boardUrl }
        else -> -1
    }

    private fun effectiveTabs(state: TabControllerState<BoardTabInfo, String, BoardPendingOperation>): List<BoardTabInfo> =
        foldEffectiveTabs(
            state.canonicalTabs,
            state.pendingCommands.map { pending ->
                when (val operation = pending.operation) {
                    is Operation.Ensure -> IndexedTabOperation(operation.tab.boardUrl) { current -> mergeBoardTabMetadata(current, operation.tab) }
                    is Operation.Delete -> IndexedTabOperation(operation.boardUrl, remove = true) { current -> current }
                    is Operation.Pin -> IndexedTabOperation(operation.boardUrl) { current -> current?.copy(isPinned = operation.isPinned) }
                    is Operation.Info -> IndexedTabOperation(operation.tab.boardUrl) { current -> mergeBoardTabMetadata(current, operation.tab) }
                    is Operation.Scroll -> IndexedTabOperation(operation.boardUrl) { current -> current?.copy(firstVisibleItemIndex = operation.index, firstVisibleItemScrollOffset = operation.offset) }
                }
            },
            BoardTabInfo::boardUrl,
        )

    private fun TabControllerState<BoardTabInfo, String, BoardPendingOperation>.rebuildPresentation(): TabControllerState<BoardTabInfo, String, BoardPendingOperation> {
        val tabs = effectiveTabs(this)
        val pendingMissing = pendingCommands.firstOrNull { pending ->
            pending.operation is Operation.Ensure && selectedKey == (pending.operation as Operation.Ensure).tab.boardUrl && tabs.none { it.boardUrl == selectedKey }
        }?.let { selectedKey }
        val presentation = resolveTabPresentation(tabs, loadPhase == TabLoadPhase.Loaded, selectedKey, pendingMissing, BoardTabInfo::boardUrl)
        val resolvedKey = when (val selection = presentation.selection) {
            is TabSelectionResolution.Selected -> selection.key
            is TabSelectionResolution.PendingMissing -> selection.key
            else -> null
        }
        return copy(selectedKey = resolvedKey, presentation = presentation)
    }

    private fun applyUnboundEnsure(tab: BoardTabInfo) {
        val existing = effectiveTabs(_state.value).firstOrNull { it.boardUrl == tab.boardUrl }
        _state.update { state ->
            state.copy(
                loadPhase = TabLoadPhase.Loaded,
                canonicalTabs = if (existing == null) state.canonicalTabs + tab else state.canonicalTabs.map {
                    if (it.boardUrl == tab.boardUrl) mergeBoardTabMetadata(it, tab) else it
                },
            ).rebuildPresentation()
        }
    }

    private fun AppRoute.Board.toTabInfo(): BoardTabInfo = BoardTabInfo(
        boardId = boardId ?: 0L,
        boardName = boardName,
        boardUrl = boardUrl,
        serviceName = parseServiceName(boardUrl),
    )

    /** retained Controller の effect runner と state projection を終了する。 */
    fun close() {
        val cancellation = CancellationException("Board tab controller was closed")
        _state.value.pendingCommands.forEach { pending ->
            pending.result.complete(TabCommandResult.Failure(cancellation))
        }
        _state.update { it.copy(pendingCommands = emptyList()).rebuildPresentation() }
        controllerScope.cancel()
    }
}
