package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
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

    fun closeThreadTab(threadKey: String, boardUrl: String) {
        val (host, board) = parseBoardUrl(boardUrl) ?: return
        val id = ThreadId.of(host, board, threadKey)
        _openThreadTabs.value.find { it.id == id }?.let { tab ->
            closeThreadTab(tab)
        }
    }

    fun setThreadCurrentPage(page: Int) {
        _threadCurrentPage.value = page
    }

    fun moveThreadPage(offset: Int) {
        val tabs = _openThreadTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _threadCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            setThreadCurrentPage(targetIndex)
        }
    }

    fun animateThreadPage(offset: Int) {
        val tabs = _openThreadTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _threadCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            scope?.launch { _threadPageAnimation.emit(targetIndex) }
        }
    }

    fun clearNewResCount(threadId: ThreadId) {
        val key = threadId.value
        _newResCounts.update { it - key }
    }

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

    fun getTabInfo(threadId: ThreadId): ThreadTabInfo? {
        return _openThreadTabs.value.find { it.id == threadId }
    }

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

    private fun saveThreadTabs(tabs: List<ThreadTabInfo> = _openThreadTabs.value) {
        scope?.launch { tabsRepository.saveOpenThreadTabs(tabs) }
    }

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
