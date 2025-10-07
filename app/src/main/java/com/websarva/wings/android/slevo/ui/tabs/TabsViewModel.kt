package com.websarva.wings.android.slevo.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.board.BoardViewModel
import com.websarva.wings.android.slevo.ui.board.BoardViewModelFactory
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModelFactory
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
/**
 * スレッドタブ・板タブをまとめて管理する ViewModel。
 * 各タブに紐づく ViewModel の生成や破棄もここで行う。
 */
class TabsViewModel @Inject constructor(
    private val threadViewModelFactory: ThreadViewModelFactory,
    private val boardViewModelFactory: BoardViewModelFactory,
    private val tabsRepository: TabsRepository,
    private val bookmarkBoardRepo: BookmarkBoardRepository,
    private val threadBookmarkRepo: ThreadBookmarkRepository,
    private val boardRepository: BoardRepository,
    private val datRepository: DatRepository,
) : ViewModel() {
    // 画面用のUIステートのみを保持
    private val _uiState = MutableStateFlow(TabsUiState())
    val uiState: StateFlow<TabsUiState> = _uiState.asStateFlow()

    private val _boardCurrentPage = MutableStateFlow(-1)
    val boardCurrentPage: StateFlow<Int> = _boardCurrentPage.asStateFlow()

    private val _threadCurrentPage = MutableStateFlow(-1)
    val threadCurrentPage: StateFlow<Int> = _threadCurrentPage.asStateFlow()

    private val _boardPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val boardPageAnimation: SharedFlow<Int> = _boardPageAnimation.asSharedFlow()

    private val _threadPageAnimation = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val threadPageAnimation: SharedFlow<Int> = _threadPageAnimation.asSharedFlow()

    // boardUrl をキーに BoardViewModel をキャッシュ
    private val boardViewModelMap: MutableMap<String, BoardViewModel> = mutableMapOf()

    // threadId をキーに ThreadViewModel をキャッシュ
    private val threadViewModelMap: MutableMap<String, ThreadViewModel> = mutableMapOf()

    val lastSelectedPage = tabsRepository.observeLastSelectedPage()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            combine(
                tabsRepository.observeOpenBoardTabs(),
                bookmarkBoardRepo.observeGroupsWithBoards()
            ) { tabs, groups ->
                val colorMap = mutableMapOf<Long, String>()
                groups.forEach { g ->
                    val color = g.group.colorName
                    g.boards.forEach { b -> colorMap[b.boardId] = color }
                }
                tabs.map { it.copy(bookmarkColorName = colorMap[it.boardId]) }
            }.collect { boards ->
                _uiState.update { current ->
                    current.copy(
                        openBoardTabs = boards,
                        boardLoaded = true
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                tabsRepository.observeOpenThreadTabs(),
                threadBookmarkRepo.observeSortedGroupsWithThreadBookmarks()
            ) { tabs, groups ->
                val colorMap = mutableMapOf<String, String>()
                groups.forEach { g ->
                    val color = g.group.colorName
                    g.threads.forEach { t ->
                        parseBoardUrl(t.boardUrl)?.let { (host, board) ->
                            val threadId = ThreadId.of(host, board, t.threadKey)
                            colorMap[threadId.value] = color
                        }
                    }
                }
                tabs.map { it.copy(bookmarkColorName = colorMap[it.id.value]) }
            }.collect { threads ->
                _uiState.update { current ->
                    current.copy(
                        openThreadTabs = threads,
                        threadLoaded = true
                    )
                }
            }
        }
    }

    /**
     * 指定キーの [ThreadViewModel] を取得。
     * 存在しない場合は Factory から生成して登録する。
     */
    fun getOrCreateThreadViewModel(viewModelKey: String): ThreadViewModel {
        return threadViewModelMap.getOrPut(viewModelKey) {
            threadViewModelFactory.create(viewModelKey)
        }
    }

    /**
     * 指定URLの [BoardViewModel] を取得。
     * まだ生成されていなければ Factory から作成してキャッシュする。
     */
    fun getOrCreateBoardViewModel(
        boardUrl: String
    ): BoardViewModel {
        return boardViewModelMap.getOrPut(boardUrl) {
            boardViewModelFactory.create(boardUrl)
        }
    }

    fun setLastSelectedPage(page: Int) {
        viewModelScope.launch { tabsRepository.setLastSelectedPage(page) }
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
        viewModelScope.launch { tabsRepository.saveOpenBoardTabs(_uiState.value.openBoardTabs) }
        return index
    }

    fun ensureThreadTab(route: AppRoute.Thread): Int {
        val (host, board) = parseBoardUrl(route.boardUrl) ?: return -1
        val tabInfo = ThreadTabInfo(
            id = ThreadId.of(host, board, route.threadKey),
            title = route.threadTitle,
            boardName = route.boardName,
            boardUrl = route.boardUrl,
            boardId = route.boardId ?: 0L,
            resCount = route.resCount
        )
        return upsertThreadTab(tabInfo)
    }

    /**
     * 板タブを開く。すでに存在する場合は最新情報で上書きする。
     */
    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        upsertBoardTab(boardTabInfo)
        viewModelScope.launch { tabsRepository.saveOpenBoardTabs(_uiState.value.openBoardTabs) }
    }

    private fun upsertBoardTab(boardTabInfo: BoardTabInfo): Int {
        var targetIndex = -1
        _uiState.update { state ->
            val currentBoards = state.openBoardTabs
            val index = currentBoards.indexOfFirst { it.boardUrl == boardTabInfo.boardUrl }
            val updated = if (index != -1) {
                targetIndex = index
                currentBoards.toMutableList().apply {
                    this[index] = boardTabInfo.copy(
                        firstVisibleItemIndex = this[index].firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = this[index].firstVisibleItemScrollOffset
                    )
                }
            } else {
                targetIndex = currentBoards.size
                currentBoards + boardTabInfo
            }
            state.copy(openBoardTabs = updated)
        }
        return targetIndex
    }

    private fun upsertThreadTab(tabInfo: ThreadTabInfo): Int {
        var updatedTabs: List<ThreadTabInfo> = emptyList()
        var targetIndex = -1
        _uiState.update { state ->
            val current = state.openThreadTabs
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
                        resCount = if (tabInfo.resCount != 0) tabInfo.resCount else existing.resCount
                    )
                }
            } else {
                targetIndex = current.size
                current + tabInfo
            }
            updatedTabs = newList
            state.copy(openThreadTabs = newList)
        }
        viewModelScope.launch { tabsRepository.saveOpenThreadTabs(updatedTabs) }
        return targetIndex
    }

    fun setBoardCurrentPage(page: Int) {
        _boardCurrentPage.value = page
    }

    fun setThreadCurrentPage(page: Int) {
        _threadCurrentPage.value = page
    }

    fun moveBoardPage(offset: Int) {
        val tabs = _uiState.value.openBoardTabs
        if (tabs.isEmpty()) return
        val currentIndex = _boardCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            setBoardCurrentPage(targetIndex)
        }
    }

    fun animateBoardPage(offset: Int) {
        val tabs = _uiState.value.openBoardTabs
        if (tabs.isEmpty()) return
        val currentIndex = _boardCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            viewModelScope.launch { _boardPageAnimation.emit(targetIndex) }
        }
    }

    fun moveThreadPage(offset: Int) {
        val tabs = _uiState.value.openThreadTabs
        if (tabs.isEmpty()) return
        val currentIndex = _threadCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            setThreadCurrentPage(targetIndex)
        }
    }

    fun animateThreadPage(offset: Int) {
        val tabs = _uiState.value.openThreadTabs
        if (tabs.isEmpty()) return
        val currentIndex = _threadCurrentPage.value.takeIf { it in tabs.indices } ?: 0
        val targetIndex = currentIndex + offset
        if (targetIndex in tabs.indices) {
            viewModelScope.launch { _threadPageAnimation.emit(targetIndex) }
        }
    }

    fun closeBoardTabByUrl(boardUrl: String) {
        _uiState.value.openBoardTabs.find { it.boardUrl == boardUrl }?.let { tab ->
            closeBoardTab(tab)
        }
    }

    fun closeThreadTab(threadKey: String, boardUrl: String) {
        val (host, board) = parseBoardUrl(boardUrl) ?: return
        val id = ThreadId.of(host, board, threadKey)
        _uiState.value.openThreadTabs.find { it.id == id }?.let { tab ->
            closeThreadTab(tab)
        }
    }

    /**
     * 指定された板ID・URL・板名からBoardInfoを解決する。
     * - boardIdが有効ならそれを優先。
     * - BoardEntityからURL一致の板情報を検索。
     * - それ以外は板名を取得・登録してIDを確定。
     */
    suspend fun resolveBoardInfo(
        boardId: Long?,
        boardUrl: String,
        boardName: String
    ): BoardInfo? {
        boardId?.takeIf { it != 0L }?.let { return BoardInfo(it, boardName, boardUrl) }

        boardRepository.findBoardByUrl(boardUrl)?.let { entity ->
            return BoardInfo(entity.boardId, entity.name, entity.url)
        }

        val name = boardRepository.fetchBoardName("${boardUrl}SETTING.TXT") ?: return null
        val id = boardRepository.ensureBoard(BoardInfo(0L, name, boardUrl))
        return BoardInfo(id, name, boardUrl)
    }

    /**
     * スレッドタブを閉じ、関連する [ThreadViewModel] を解放する。
     */
    fun closeThreadTab(tab: ThreadTabInfo) {
        val mapKey = tab.id.value
        threadViewModelMap.remove(mapKey)?.release()

        var updatedTabs: List<ThreadTabInfo> = emptyList()
        val removedIndex = _uiState.value.openThreadTabs.indexOfFirst { it.id == tab.id }
        _uiState.update { state ->
            val newTabs = state.openThreadTabs.filterNot { it.id == tab.id }
            updatedTabs = newTabs
            state.copy(
                openThreadTabs = newTabs,
                newResCounts = state.newResCounts - mapKey
            )
        }

        updateCurrentPageAfterRemoval(
            currentPageFlow = _threadCurrentPage,
            removedIndex = removedIndex,
            updatedSize = updatedTabs.size
        )

        viewModelScope.launch { tabsRepository.saveOpenThreadTabs(updatedTabs) }
    }

    /**
     * 板タブを閉じ、対応する [BoardViewModel] を解放する。
     */
    fun closeBoardTab(tab: BoardTabInfo) {
        boardViewModelMap.remove(tab.boardUrl)?.release()

        var updatedTabs: List<BoardTabInfo> = emptyList()
        val removedIndex = _uiState.value.openBoardTabs.indexOfFirst { it.boardUrl == tab.boardUrl }
        _uiState.update { state ->
            val newTabs = state.openBoardTabs.filterNot { it.boardUrl == tab.boardUrl }
            updatedTabs = newTabs
            state.copy(openBoardTabs = newTabs)
        }

        updateCurrentPageAfterRemoval(
            currentPageFlow = _boardCurrentPage,
            removedIndex = removedIndex,
            updatedSize = updatedTabs.size
        )

        viewModelScope.launch { tabsRepository.saveOpenBoardTabs(updatedTabs) }
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

    /**
     * 板タブのスクロール位置を保存する。
     */
    fun updateBoardScrollPosition(
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
        _uiState.update { state ->
            val updated = state.openBoardTabs.map { tab ->
                if (tab.boardUrl == boardUrl) {
                    tab.copy(
                        firstVisibleItemIndex = firstVisibleIndex,
                        firstVisibleItemScrollOffset = scrollOffset
                    )
                } else {
                    tab
                }
            }
            state.copy(openBoardTabs = updated)
        }
        viewModelScope.launch { tabsRepository.saveOpenBoardTabs(_uiState.value.openBoardTabs) }
    }

    fun clearNewResCount(threadId: ThreadId) {
        val key = threadId.value
        _uiState.update { it.copy(newResCounts = it.newResCounts - key) }
    }

    fun refreshOpenThreads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val currentTabs = _uiState.value.openThreadTabs
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
                    firstNewResNo = newFirst
                )
            }
            _uiState.update { state ->
                state.copy(
                    openThreadTabs = updatedTabs,
                    newResCounts = resultMap,
                    isRefreshing = false
                )
            }
            tabsRepository.saveOpenThreadTabs(_uiState.value.openThreadTabs)
        }
    }

    /**
     * 指定キーのタブ情報を取得する。
     */
    fun getTabInfo(threadId: ThreadId): ThreadTabInfo? {
        return _uiState.value.openThreadTabs.find { it.id == threadId }
    }

    /**
     * ViewModel 自身が破棄される際に呼び出される処理。
     * 登録済みの子 ViewModel もすべて解放する。
     */
    override fun onCleared() {
        super.onCleared()
        threadViewModelMap.values.forEach { it.release() }
        threadViewModelMap.clear()
        boardViewModelMap.values.forEach { it.release() }
        boardViewModelMap.clear()
    }
}
