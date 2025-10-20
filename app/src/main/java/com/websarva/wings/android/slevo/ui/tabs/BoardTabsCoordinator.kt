package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.util.parseServiceName
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

@ViewModelScoped
class BoardTabsCoordinator @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val bookmarkBoardRepository: BookmarkBoardRepository,
    private val tabViewModelRegistry: TabViewModelRegistry,
) {
    private val _openBoardTabs = MutableStateFlow<List<BoardTabInfo>>(emptyList())
    val openBoardTabs: StateFlow<List<BoardTabInfo>> = _openBoardTabs.asStateFlow()

    private val _boardLoaded = MutableStateFlow(false)
    val boardLoaded: StateFlow<Boolean> = _boardLoaded.asStateFlow()

    private val _boardCurrentPage = MutableStateFlow(-1)
    val boardCurrentPage: StateFlow<Int> = _boardCurrentPage.asStateFlow()

    private val _boardPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val boardPageAnimation: SharedFlow<Int> = _boardPageAnimation.asSharedFlow()

    private var scope: CoroutineScope? = null

    fun bind(scope: CoroutineScope) {
        if (this.scope != null) return
        this.scope = scope
        scope.launch {
            combine(
                tabsRepository.observeOpenBoardTabs(),
                bookmarkBoardRepository.observeGroupsWithBoards()
            ) { tabs, groups ->
                val colorMap = mutableMapOf<Long, String>()
                groups.forEach { g ->
                    val color = g.group.colorName
                    g.boards.forEach { b -> colorMap[b.boardId] = color }
                }
                tabs.map { tab -> tab.copy(bookmarkColorName = colorMap[tab.boardId]) }
            }.collect { boards ->
                _openBoardTabs.value = boards
                _boardLoaded.value = true
            }
        }
    }

    fun ensureBoardTab(route: AppRoute.Board): Int {
        val index = upsertBoardTab(
            BoardTabInfo(
                boardId = route.boardId ?: 0L,
                boardName = route.boardName,
                boardUrl = route.boardUrl,
                serviceName = parseServiceName(route.boardUrl)
            )
        )
        saveBoardTabs()
        return index
    }

    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        upsertBoardTab(boardTabInfo)
        saveBoardTabs()
    }

    fun closeBoardTab(tab: BoardTabInfo) {
        tabViewModelRegistry.releaseBoardViewModel(tab.boardUrl)

        val removedIndex = _openBoardTabs.value.indexOfFirst { it.boardUrl == tab.boardUrl }
        var updatedTabs: List<BoardTabInfo> = emptyList()
        _openBoardTabs.update { state ->
            val newTabs = state.filterNot { it.boardUrl == tab.boardUrl }
            updatedTabs = newTabs
            newTabs
        }
        updateCurrentPageAfterRemoval(_boardCurrentPage, removedIndex, updatedTabs.size)
        saveBoardTabs(updatedTabs)
    }

    fun closeBoardTabByUrl(boardUrl: String) {
        _openBoardTabs.value.find { it.boardUrl == boardUrl }?.let { tab ->
            closeBoardTab(tab)
        }
    }

    fun updateBoardScrollPosition(
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int,
    ) {
        _openBoardTabs.update { state ->
            state.map { tab ->
                if (tab.boardUrl == boardUrl) {
                    tab.copy(
                        firstVisibleItemIndex = firstVisibleIndex,
                        firstVisibleItemScrollOffset = scrollOffset,
                    )
                } else {
                    tab
                }
            }
        }
        saveBoardTabs()
    }

    fun setBoardCurrentPage(page: Int) {
        _boardCurrentPage.value = page
    }

    fun moveBoardPage(offset: Int) {
        val tabs = _openBoardTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _boardCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            setBoardCurrentPage(targetIndex)
        }
    }

    fun animateBoardPage(offset: Int) {
        val tabs = _openBoardTabs.value
        if (tabs.isEmpty()) return
        val currentIndex = _boardCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            scope?.launch { _boardPageAnimation.emit(targetIndex) }
        }
    }

    private fun upsertBoardTab(boardTabInfo: BoardTabInfo): Int {
        var targetIndex = -1
        _openBoardTabs.update { state ->
            val currentBoards = state
            val index = currentBoards.indexOfFirst { it.boardUrl == boardTabInfo.boardUrl }
            val updated = if (index != -1) {
                targetIndex = index
                currentBoards.toMutableList().apply {
                    val existing = this[index]
                    this[index] = boardTabInfo.copy(
                        bookmarkColorName = boardTabInfo.bookmarkColorName ?: existing.bookmarkColorName,
                        firstVisibleItemIndex = existing.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = existing.firstVisibleItemScrollOffset,
                    )
                }
            } else {
                targetIndex = currentBoards.size
                currentBoards + boardTabInfo
            }
            updated
        }
        return targetIndex
    }

    private fun saveBoardTabs(tabs: List<BoardTabInfo> = _openBoardTabs.value) {
        scope?.launch { tabsRepository.saveOpenBoardTabs(tabs) }
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
