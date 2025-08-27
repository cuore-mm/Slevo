package com.websarva.wings.android.slevo.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModelFactory
import com.websarva.wings.android.slevo.ui.board.BoardViewModel
import com.websarva.wings.android.slevo.ui.board.BoardViewModelFactory
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.model.BoardInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

    // boardUrl をキーに BoardViewModel をキャッシュ
    private val boardViewModelMap: MutableMap<String, BoardViewModel> = mutableMapOf()

    // threadKey + boardUrl をキーに ThreadViewModel をキャッシュ
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
                        colorMap[t.threadKey + t.boardUrl] = color
                    }
                }
                tabs.map { it.copy(bookmarkColorName = colorMap[it.key + it.boardUrl]) }
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

    /**
     * スレッドタブを開く。既に同じキーのタブが存在する場合は情報のみ更新する。
     */
    fun openThreadTab(tabInfo: ThreadTabInfo) {
        _uiState.update { state ->
            val currentTabs = state.openThreadTabs
            val tabIndex = currentTabs.indexOfFirst { it.key == tabInfo.key && it.boardUrl == tabInfo.boardUrl }
            val updated = if (tabIndex != -1) {
                currentTabs.toMutableList().apply {
                    this[tabIndex] = this[tabIndex].copy(
                        title = tabInfo.title,
                        boardName = tabInfo.boardName,
                        boardId = tabInfo.boardId,
                        resCount = tabInfo.resCount
                    )
                }
            } else {
                currentTabs + tabInfo
            }
            state.copy(openThreadTabs = updated)
        }
        viewModelScope.launch { tabsRepository.saveOpenThreadTabs(_uiState.value.openThreadTabs) }
    }

    fun setLastSelectedPage(page: Int) {
        viewModelScope.launch { tabsRepository.setLastSelectedPage(page) }
    }

    /**
     * 板タブを開く。すでに存在する場合は最新情報で上書きする。
     */
    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        _uiState.update { state ->
            val currentBoards = state.openBoardTabs
            val index = currentBoards.indexOfFirst { it.boardUrl == boardTabInfo.boardUrl }
            val updated = if (index != -1) {
                currentBoards.toMutableList().apply {
                    this[index] = boardTabInfo.copy(
                        firstVisibleItemIndex = this[index].firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = this[index].firstVisibleItemScrollOffset
                    )
                }
            } else {
                currentBoards + boardTabInfo
            }
            state.copy(openBoardTabs = updated)
        }
        viewModelScope.launch { tabsRepository.saveOpenBoardTabs(_uiState.value.openBoardTabs) }
    }

    /**
     * 指定された板ID・URL・板名からBoardInfoを解決する。
     * - boardIdが有効ならそれを優先。
     * - ブックマークからURL一致の板情報を検索。
     * - それ以外は板名を取得・登録してIDを確定。
     */
    suspend fun resolveBoardInfo(
        boardId: Long?,
        boardUrl: String,
        boardName: String
    ): BoardInfo {
        boardId?.takeIf { it != 0L }?.let { return BoardInfo(it, boardName, boardUrl) }

        bookmarkBoardRepo.findBoardByUrl(boardUrl)?.let { entity ->
            return BoardInfo(entity.boardId, entity.name, entity.url)
        }

        val name = boardRepository.fetchBoardName("${boardUrl}SETTING.TXT") ?: boardName
        val id = boardRepository.ensureBoard(BoardInfo(0L, name, boardUrl))
        return BoardInfo(id, name, boardUrl)
    }

    /**
     * スレッドタブを閉じ、関連する [ThreadViewModel] を解放する。
     */
    fun closeThreadTab(tab: ThreadTabInfo) {
        val mapKey = tab.key + tab.boardUrl
        val viewModelToDestroy = threadViewModelMap[mapKey]

        // onCleared()の代わりに、新しく作った公開メソッドrelease()を呼び出す
        viewModelToDestroy?.release()

        threadViewModelMap.remove(mapKey)
        _uiState.update { state ->
            state.copy(
                openThreadTabs = state.openThreadTabs.filterNot { it.key == tab.key && it.boardUrl == tab.boardUrl }
            )
        }
        viewModelScope.launch { tabsRepository.saveOpenThreadTabs(_uiState.value.openThreadTabs) }
    }

    /**
     * 板タブを閉じ、対応する [BoardViewModel] を解放する。
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun closeBoardTab(tab: BoardTabInfo) {
        boardViewModelMap.remove(tab.boardUrl)?.release()
        _uiState.update { state ->
            state.copy(openBoardTabs = state.openBoardTabs.filterNot { it.boardUrl == tab.boardUrl })
        }
        viewModelScope.launch { tabsRepository.saveOpenBoardTabs(_uiState.value.openBoardTabs) }
    }

    /**
     * スレッドタブの情報を更新する
     */
    fun updateThreadTabInfo(key: String, boardUrl: String, title: String, resCount: Int) {
        _uiState.update { state ->
            val updated = state.openThreadTabs.map { tab ->
                if (tab.key == key && tab.boardUrl == boardUrl) {
                    val newFirst = if (resCount > tab.resCount) {
                        if (tab.firstNewResNo <= tab.lastReadResNo) tab.lastReadResNo + 1 else tab.firstNewResNo
                    } else {
                        tab.firstNewResNo
                    }
                    tab.copy(title = title, resCount = resCount, firstNewResNo = newFirst)
                } else {
                    tab
                }
            }
            state.copy(openThreadTabs = updated, newResCounts = state.newResCounts - (key + boardUrl))
        }
        viewModelScope.launch { tabsRepository.saveOpenThreadTabs(_uiState.value.openThreadTabs) }
    }

    /**
     * スレッドタブのスクロール位置を保存する。
     */
    fun updateThreadScrollPosition(
        tabKey: String,
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
        _uiState.update { state ->
            val updated = state.openThreadTabs.map { tab ->
                if (tab.key == tabKey && tab.boardUrl == boardUrl) {
                    tab.copy(
                        firstVisibleItemIndex = firstVisibleIndex,
                        firstVisibleItemScrollOffset = scrollOffset
                    )
                } else {
                    tab
                }
            }
            state.copy(openThreadTabs = updated)
        }
        viewModelScope.launch { tabsRepository.saveOpenThreadTabs(_uiState.value.openThreadTabs) }
    }

    /**
     * 最後に読んだレス番号を保存する。
     */
    fun updateThreadLastRead(tabKey: String, boardUrl: String, lastReadResNo: Int) {
        _uiState.update { state ->
            val updated = state.openThreadTabs.map { tab ->
                if (tab.key == tabKey && tab.boardUrl == boardUrl && lastReadResNo > tab.lastReadResNo) {
                    tab.copy(lastReadResNo = lastReadResNo)
                } else {
                    tab
                }
            }
            state.copy(openThreadTabs = updated)
        }
        viewModelScope.launch { tabsRepository.saveOpenThreadTabs(_uiState.value.openThreadTabs) }
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

    fun clearNewResCount(threadKey: String, boardUrl: String) {
        val key = threadKey + boardUrl
        _uiState.update { it.copy(newResCounts = it.newResCounts - key) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshOpenThreads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val currentTabs = _uiState.value.openThreadTabs
            val resultMap = mutableMapOf<String, Int>()
            val updatedTabs = currentTabs.map { tab ->
                val res = datRepository.getThread(tab.boardUrl, tab.key)
                val size = res?.first?.size ?: tab.resCount
                val diff = size - tab.resCount
                if (diff > 0) {
                    resultMap[tab.key + tab.boardUrl] = diff
                }
                val newFirst = if (diff > 0) {
                    if (tab.firstNewResNo <= tab.lastReadResNo) tab.lastReadResNo + 1 else tab.firstNewResNo
                } else {
                    tab.firstNewResNo
                }
                tab.copy(resCount = size, firstNewResNo = newFirst)
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
    fun getTabInfo(tabKey: String, boardUrl: String): ThreadTabInfo? {
        return _uiState.value.openThreadTabs.find { it.key == tabKey && it.boardUrl == boardUrl }
    }

    /**
     * ViewModel 自身が破棄される際に呼び出される処理。
     * 登録済みの子 ViewModel もすべて解放する。
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCleared() {
        super.onCleared()
        threadViewModelMap.values.forEach { it.release() }
        threadViewModelMap.clear()
        boardViewModelMap.values.forEach { it.release() }
        boardViewModelMap.clear()
    }
}
