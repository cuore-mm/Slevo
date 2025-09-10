package com.websarva.wings.android.slevo.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
/**
 * スレッドタブ・板タブの一覧を管理し永続化する ViewModel。
 * 各画面の ViewModel 生成・破棄はそれぞれの画面側に任せる。
 */
class TabListViewModel @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val bookmarkBoardRepo: BookmarkBoardRepository,
    private val threadBookmarkRepo: ThreadBookmarkRepository,
    private val boardRepository: BoardRepository,
    private val datRepository: DatRepository,
) : ViewModel() {
    // 画面用のUIステートのみを保持
    private val _uiState = MutableStateFlow(TabListUiState())
    val uiState: StateFlow<TabListUiState> = _uiState.asStateFlow()

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
     * スレッドタブを閉じる。
     */
    fun closeThreadTab(tab: ThreadTabInfo) {
        viewModelScope.launch {
            val current = tabsRepository.observeOpenThreadTabs().first()
            val updated = current.filterNot { it.key == tab.key && it.boardUrl == tab.boardUrl }
            tabsRepository.saveOpenThreadTabs(updated)
        }
    }

    /**
     * 板タブを閉じる。
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun closeBoardTab(tab: BoardTabInfo) {
        _uiState.update { state ->
            state.copy(openBoardTabs = state.openBoardTabs.filterNot { it.boardUrl == tab.boardUrl })
        }
        viewModelScope.launch { tabsRepository.saveOpenBoardTabs(_uiState.value.openBoardTabs) }
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
    fun getTabInfo(tabKey: String, boardUrl: String): ThreadTabInfo? {
        return _uiState.value.openThreadTabs.find { it.key == tabKey && it.boardUrl == boardUrl }
    }

    /**
     * ViewModel 自身が破棄される際に呼び出される処理。
     * 登録済みの子 ViewModel もすべて解放する。
     */
    // 子 ViewModel の解放処理は各画面の ViewModel に委譲する
}
