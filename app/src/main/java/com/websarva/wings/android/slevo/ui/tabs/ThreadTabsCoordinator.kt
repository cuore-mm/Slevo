package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val tabViewModelRegistry: TabViewModelRegistry,
) {
    private val _openThreadTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())
    val openThreadTabs: StateFlow<List<ThreadTabInfo>> = _openThreadTabs.asStateFlow()

    private val _threadLoaded = MutableStateFlow(false)
    val threadLoaded: StateFlow<Boolean> = _threadLoaded.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _newResCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val newResCounts: StateFlow<Map<String, Int>> = _newResCounts.asStateFlow()

    private val _threadCurrentPage = MutableStateFlow(-1)
    val threadCurrentPage: StateFlow<Int> = _threadCurrentPage.asStateFlow()

    private val _threadPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val threadPageAnimation: SharedFlow<Int> = _threadPageAnimation.asSharedFlow()

    private var scope: CoroutineScope? = null

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
            title = route.threadTitle,
            boardName = route.boardName,
            boardUrl = route.boardUrl,
            boardId = route.boardId ?: 0L,
            resCount = route.resCount,
        )
        return upsertThreadTab(tabInfo)
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
     * 開いているタブをリフレッシュして、レス数の差分を計算し、_newResCounts と _openThreadTabs を更新する。
     * 非同期処理のため scope が必要。完了時に永続化も行う。
     */
    fun refreshOpenThreads() {
        val currentScope = scope ?: return
        currentScope.launch {
            _isRefreshing.value = true
            val currentTabs = _openThreadTabs.value
            val resultMap = mutableMapOf<String, Int>()
            val updatedTabs = currentTabs.map { tab ->
                val res = datRepository.getThread(tab.boardUrl, tab.threadKey)
                val size = res?.first?.size ?: tab.resCount
                val diff = size - tab.resCount
                if (diff > 0) {
                    resultMap[tab.id.value] = diff
                }
                val candidate =
                    if (tab.firstNewResNo == null || tab.firstNewResNo <= tab.lastReadResNo) {
                        tab.lastReadResNo + 1
                    } else {
                        tab.firstNewResNo
                    }
                val newFirst = if (candidate > size) null else candidate
                tab.copy(
                    resCount = size,
                    firstNewResNo = newFirst,
                )
            }
            _openThreadTabs.value = updatedTabs
            _newResCounts.value = resultMap
            _isRefreshing.value = false
            tabsRepository.saveOpenThreadTabs(_openThreadTabs.value)
        }
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
