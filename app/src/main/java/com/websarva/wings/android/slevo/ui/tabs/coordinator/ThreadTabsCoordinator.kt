package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.registry.TabViewModelRegistry
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/**
 * スレッドタブの集合を管理するコーディネータ。
 *
 * 主な責務:
 * - 開いているスレッドタブの状態を保持・更新する
 * - タブの追加/更新/削除、選択ページ管理、リフレッシュ処理を提供する
 * - タブの永続化（リポジトリ経由）を行う
 *
 * スコープは外部から bind(...) で渡される ViewModel スコープを使用する。
 */
@ViewModelScoped
class ThreadTabsCoordinator @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val threadBookmarkRepository: ThreadBookmarkRepository,
    private val datRepository: DatRepository,
    private val threadStateRepository: ThreadStateRepository,
    private val tabViewModelRegistry: TabViewModelRegistry,
) {
    /**
     * 正常完了時に 100% の進捗を表示し続ける時間。
     */
    private companion object {
        const val REFRESH_COMPLETION_VISIBILITY_MILLIS = 300L
    }

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

    private val _threadCurrentPage = MutableStateFlow(-1)
    val threadCurrentPage: StateFlow<Int> = _threadCurrentPage.asStateFlow()

    private val _threadPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val threadPageAnimation: SharedFlow<Int> = _threadPageAnimation.asSharedFlow()

    private var scope: CoroutineScope? = null

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
                _openThreadTabs.value = threads
                _newResCounts.value = threads
                    .filter { tab -> tab.newResCount > 0 }
                    .associate { tab -> tab.id.value to tab.newResCount }
                _threadLoaded.value = true
            }
        }
    }

    /**
     * 指定のルート情報に対応するスレッドタブを作成または更新し、タブのインデックスを返す。
     * 失敗した場合は -1 を返す。
     */
    fun ensureThreadTab(route: AppRoute.Thread): Int {
        val (host, board) = parseBoardUrl(route.boardUrl) ?: return -1
        val tabInfo = ThreadTabInfo(
            id = ThreadId.of(host, board, route.threadKey),
            title = buildInitialThreadTitle(route),
            boardName = route.boardName,
            boardUrl = route.boardUrl,
            boardId = route.boardId ?: 0L,
            resCount = route.resCount,
        )
        return upsertThreadTab(tabInfo)
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
     * 指定の ThreadTabInfo を閉じる（ViewModel の解放、内部状態更新、永続化）。
     */
    fun closeThreadTab(tab: ThreadTabInfo) {
        val key = tab.id.value
        tabViewModelRegistry.releaseThreadViewModel(key)

        val removedIndex = _openThreadTabs.value.indexOfFirst { it.id == tab.id }
        var updatedTabs: List<ThreadTabInfo> = emptyList()
        _openThreadTabs.update { state ->
            val newTabs = state.filterNot { it.id == tab.id }
            updatedTabs = newTabs
            newTabs
        }
        _newResCounts.update { it - key }
        updateCurrentPageAfterRemoval(_threadCurrentPage, removedIndex, updatedTabs.size)
        saveThreadTabs(updatedTabs)
    }

    /**
     * threadKey と boardUrl からタブを特定して閉じる（存在しない場合は何もしない）。
     */
    fun closeThreadTab(threadKey: String, boardUrl: String) {
        val (host, board) = parseBoardUrl(boardUrl) ?: return
        val id = ThreadId.of(host, board, threadKey)
        _openThreadTabs.value.find { it.id == id }?.let { tab ->
            closeThreadTab(tab)
        }
    }

    /**
     * 現在のページ（タブのインデックス）をセットする。
     */
    fun setThreadCurrentPage(page: Int) {
        _threadCurrentPage.value = page
    }

    /**
     * 現在ページから offset 分だけ移動する（範囲チェックあり）。
     */
    fun moveThreadPage(offset: Int) {
        val tabs = _openThreadTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _threadCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            setThreadCurrentPage(targetIndex)
        }
    }

    /**
     * ページ遷移のアニメーションを発行する（SharedFlow にインデックスを emit）。
     */
    fun animateThreadPage(offset: Int) {
        val tabs = _openThreadTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _threadCurrentPage.value.takeIf { it in tabs.indices } ?: 0
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
    fun togglePinThreadTab(threadId: ThreadId) {
        _openThreadTabs.update { state ->
            state.map { tab ->
                if (tab.id == threadId) {
                    tab.copy(isPinned = !tab.isPinned)
                } else {
                    tab
                }
            }
        }
        saveThreadTabs()
    }

    /**
     * 指定の ThreadId に対応する ThreadTabInfo を返す（存在しなければ null）。
     */
    fun getTabInfo(threadId: ThreadId): ThreadTabInfo? {
        return _openThreadTabs.value.find { it.id == threadId }
    }

    /**
     * タブを挿入または更新し、そのタブのインデックスを返す。
     * 保存は非同期で行われる。
     */
    private fun upsertThreadTab(tabInfo: ThreadTabInfo): Int {
        var updatedTabs: List<ThreadTabInfo> = emptyList()
        var targetIndex = -1
        _openThreadTabs.update { state ->
            val current = state
            val index = current.indexOfFirst { it.id == tabInfo.id }
            val newList = if (index != -1) {
                targetIndex = index
                current.toMutableList().apply {
                    val existing = this[index]
                    this[index] = existing.copy(
                        title = tabInfo.title,
                        boardName = tabInfo.boardName,
                        boardId = if (tabInfo.boardId != 0L) tabInfo.boardId else existing.boardId,
                        boardUrl = tabInfo.boardUrl,
                        resCount = if (tabInfo.resCount != 0) tabInfo.resCount else existing.resCount,
                        bookmarkColorName = tabInfo.bookmarkColorName ?: existing.bookmarkColorName,
                    )
                }
            } else {
                targetIndex = current.size
                current + tabInfo
            }
            updatedTabs = newList
            newList
        }
        saveThreadTabs(updatedTabs)
        return targetIndex
    }

    /**
     * タブ一覧をリポジトリに保存する。scope がない場合は何もしない。
     */
    private fun saveThreadTabs(tabs: List<ThreadTabInfo> = _openThreadTabs.value) {
        scope?.launch { tabsRepository.saveOpenThreadTabs(tabs) }
    }

    /**
     * タブ削除後に currentPage を調整するヘルパー。
     * 挙動:
     * - タブが空になったら -1 をセット
     * - 削除したインデックスが現在ページと同じなら、最小値に合わせる
     * - 削除前より current が大きければ 1 減らす
     */
    private fun updateCurrentPageAfterRemoval(
        currentPageFlow: MutableStateFlow<Int>,
        removedIndex: Int,
        updatedSize: Int,
    ) {
        val current = currentPageFlow.value
        val newPage = when {
            updatedSize <= 0 -> -1
            current < 0 -> current
            removedIndex == -1 -> current.coerceIn(0, updatedSize - 1)
            current == removedIndex -> removedIndex.coerceAtMost(updatedSize - 1)
            current > removedIndex -> (current - 1).coerceIn(0, updatedSize - 1)
            current >= updatedSize -> updatedSize - 1
            else -> current
        }
        currentPageFlow.value = newPage
    }
}
