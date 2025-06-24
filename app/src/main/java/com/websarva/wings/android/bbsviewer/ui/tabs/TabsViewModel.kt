package com.websarva.wings.android.bbsviewer.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModelFactory
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModel
import com.websarva.wings.android.bbsviewer.ui.board.BoardViewModelFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val threadViewModelFactory: ThreadViewModelFactory,
    private val boardViewModelFactory: BoardViewModelFactory
) : ViewModel() {
    // 開いているスレッドタブ一覧と、各タブに対応するThreadViewModelを保持
    private val _openThreadTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())
    val openThreadTabs: StateFlow<List<ThreadTabInfo>> = _openThreadTabs.asStateFlow()

    // 開いている板タブ一覧
    private val _openBoardTabs = MutableStateFlow<List<BoardTabInfo>>(emptyList())
    val openBoardTabs: StateFlow<List<BoardTabInfo>> = _openBoardTabs.asStateFlow()

    // boardUrl をキーとして BoardViewModel を保持
    private val boardViewModelMap: MutableMap<String, BoardViewModel> = mutableMapOf()

    // threadKey + boardUrl をキーとして ThreadViewModel を保持
    private val threadViewModelMap: MutableMap<String, ThreadViewModel> = mutableMapOf()

    /**
     * 指定キーの ThreadViewModel を取得。存在しなければ Factory から生成して登録する
     */
    fun getOrCreateThreadViewModel(viewModelKey: String): ThreadViewModel {
        return threadViewModelMap.getOrPut(viewModelKey) {
            threadViewModelFactory.create(viewModelKey)
        }
    }

    fun getOrCreateBoardViewModel(
        boardUrl: String,
        boardName: String,
        boardId: Long
    ): BoardViewModel {
        return boardViewModelMap.getOrPut(boardUrl) {
            boardViewModelFactory.create(boardId, boardName, boardUrl)
        }
    }

    fun openThreadTab(tabInfo: ThreadTabInfo) {
        _openThreadTabs.update { currentTabs ->
            val tabIndex =
                currentTabs.indexOfFirst { it.key == tabInfo.key && it.boardUrl == tabInfo.boardUrl }

            if (tabIndex != -1) {
                // 既存タブの情報を更新し、スクロール位置はそのまま維持する
                currentTabs.toMutableList().apply {
                    this[tabIndex] = this[tabIndex].copy(
                        title = tabInfo.title,
                        boardName = tabInfo.boardName,
                        boardId = tabInfo.boardId,
                        resCount = tabInfo.resCount
                    )
                }
            } else {
                // 新規タブとして追加
                currentTabs + tabInfo
            }
        }
    }

    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        _openBoardTabs.update { currentBoards ->
            val index = currentBoards.indexOfFirst { it.boardUrl == boardTabInfo.boardUrl }
            if (index != -1) {
                currentBoards.toMutableList().apply {
                    this[index] = boardTabInfo
                }
            } else {
                currentBoards + boardTabInfo
            }
        }
    }

    fun closeThreadTab(tab: ThreadTabInfo) {
        val mapKey = tab.key + tab.boardUrl
        val viewModelToDestroy = threadViewModelMap[mapKey]

        // onCleared()の代わりに、新しく作った公開メソッドrelease()を呼び出す
        viewModelToDestroy?.release()

        threadViewModelMap.remove(mapKey)
        _openThreadTabs.update { current ->
            current.filterNot { it.key == tab.key && it.boardUrl == tab.boardUrl }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun closeBoardTab(tab: BoardTabInfo) {
        boardViewModelMap.remove(tab.boardUrl)?.release()
        _openBoardTabs.update { current ->
            current.filterNot { it.boardUrl == tab.boardUrl }
        }
    }

    fun updateScrollPosition(
        tabKey: String,
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
//        Log.i("TabsViewModel", "Updating scroll position for tabKey: $tabKey, boardUrl: $boardUrl, index: $index, offset: $offset")
        _openThreadTabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.key == tabKey && tab.boardUrl == boardUrl) {
                    tab.copy(
                        firstVisibleItemIndex = firstVisibleIndex,
                        firstVisibleItemScrollOffset = scrollOffset
                    )
                } else {
                    tab
                }
            }
        }
    }

    fun getTabInfo(tabKey: String, boardUrl: String): ThreadTabInfo? {
        return _openThreadTabs.value.find { it.key == tabKey && it.boardUrl == boardUrl }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCleared() {
        super.onCleared()
        // 親が破棄されるときは、すべての子ViewModelも破棄する
        threadViewModelMap.values.forEach { it.release() }
        threadViewModelMap.clear()
        boardViewModelMap.values.forEach { it.release() }
        boardViewModelMap.clear()
    }
}
