package com.websarva.wings.android.bbsviewer.ui.tabs

import androidx.lifecycle.ViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModelFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val threadViewModelFactory: ThreadViewModelFactory
) : ViewModel() {
    // 開いているタブ一覧と合わせて、タブに紐づく ThreadViewModel も保持する
    private val _openTabs = MutableStateFlow<List<TabInfo>>(emptyList())
    val openTabs: StateFlow<List<TabInfo>> = _openTabs.asStateFlow()

    // 開いている板タブ一覧
    private val _openBoardTabs = MutableStateFlow<List<BoardTabInfo>>(emptyList())
    val openBoardTabs: StateFlow<List<BoardTabInfo>> = _openBoardTabs.asStateFlow()

    // threadKey + boardUrl をキーとして ThreadViewModel を保持
    private val threadViewModels: MutableMap<String, ThreadViewModel> = mutableMapOf()

    /**
     * 指定キーの ThreadViewModel を取得。存在しなければ Factory から生成して登録する
     */
    fun getOrCreateThreadViewModel(mapKey: String): ThreadViewModel {
        return threadViewModels.getOrPut(mapKey) {
            threadViewModelFactory.create(mapKey)
        }
    }

    fun openThread(newTabInfo: TabInfo) {
        _openTabs.update { currentTabs ->
            val tabIndex =
                currentTabs.indexOfFirst { it.key == newTabInfo.key && it.boardUrl == newTabInfo.boardUrl }

            if (tabIndex != -1) {
                // 既存タブの情報を更新し、スクロール位置はそのまま維持する
                currentTabs.toMutableList().apply {
                    this[tabIndex] = this[tabIndex].copy(
                        title = newTabInfo.title,
                        boardName = newTabInfo.boardName,
                        boardId = newTabInfo.boardId,
                        resCount = newTabInfo.resCount
                    )
                }
            } else {
                // 新規タブとして追加
                currentTabs + newTabInfo
            }
        }
    }

    fun openBoard(newTabInfo: BoardTabInfo) {
        _openBoardTabs.update { currentBoards ->
            val index = currentBoards.indexOfFirst { it.boardUrl == newTabInfo.boardUrl }
            if (index != -1) {
                currentBoards.toMutableList().apply {
                    this[index] = newTabInfo
                }
            } else {
                currentBoards + newTabInfo
            }
        }
    }

    fun closeThread(tab: TabInfo) {
        val key = tab.key + tab.boardUrl
        val viewModelToDestroy = threadViewModels[key]

        // onCleared()の代わりに、新しく作った公開メソッドrelease()を呼び出す
        viewModelToDestroy?.release()

        threadViewModels.remove(key)
        _openTabs.update { current ->
            current.filterNot { it.key == tab.key && it.boardUrl == tab.boardUrl }
        }
    }

    fun closeBoard(tab: BoardTabInfo) {
        _openBoardTabs.update { current ->
            current.filterNot { it.boardUrl == tab.boardUrl }
        }
    }

    fun updateScrollPosition(
        tabKey: String,
        boardUrl: String,
        index: Int,
        offset: Int
    ) {
//        Log.i("TabsViewModel", "Updating scroll position for tabKey: $tabKey, boardUrl: $boardUrl, index: $index, offset: $offset")
        _openTabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.key == tabKey && tab.boardUrl == boardUrl) {
                    tab.copy(
                        firstVisibleItemIndex = index,
                        firstVisibleItemScrollOffset = offset
                    )
                } else {
                    tab
                }
            }
        }
    }

    fun getTabInfo(tabKey: String, boardUrl: String): TabInfo? {
        return _openTabs.value.find { it.key == tabKey && it.boardUrl == boardUrl }
    }

    override fun onCleared() {
        super.onCleared()
        // 親が破棄されるときは、すべての子ViewModelも破棄する
        threadViewModels.values.forEach { it.release() }
        threadViewModels.clear()
    }
}
