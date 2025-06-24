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
/**
 * スレッドタブ・板タブをまとめて管理する ViewModel。
 * 各タブに紐づく ViewModel の生成や破棄もここで行う。
 */
class TabsViewModel @Inject constructor(
    private val threadViewModelFactory: ThreadViewModelFactory,
    private val boardViewModelFactory: BoardViewModelFactory
) : ViewModel() {
    // 開いているスレッドタブ一覧と、各タブに紐づく ViewModel を保持
    private val _openThreadTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())
    val openThreadTabs: StateFlow<List<ThreadTabInfo>> = _openThreadTabs.asStateFlow()

    // 開いている板タブ一覧
    private val _openBoardTabs = MutableStateFlow<List<BoardTabInfo>>(emptyList())
    val openBoardTabs: StateFlow<List<BoardTabInfo>> = _openBoardTabs.asStateFlow()

    // boardUrl をキーに BoardViewModel をキャッシュ
    private val boardViewModelMap: MutableMap<String, BoardViewModel> = mutableMapOf()

    // threadKey + boardUrl をキーに ThreadViewModel をキャッシュ
    private val threadViewModelMap: MutableMap<String, ThreadViewModel> = mutableMapOf()

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

    /**
     * 板タブを開く。すでに存在する場合は最新情報で上書きする。
     */
    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        _openBoardTabs.update { currentBoards ->
            val index = currentBoards.indexOfFirst { it.boardUrl == boardTabInfo.boardUrl }
            if (index != -1) {
                currentBoards.toMutableList().apply {
                    this[index] = boardTabInfo.copy(
                        firstVisibleItemIndex = this[index].firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = this[index].firstVisibleItemScrollOffset
                    )
                }
            } else {
                currentBoards + boardTabInfo
            }
        }
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
        _openThreadTabs.update { current ->
            current.filterNot { it.key == tab.key && it.boardUrl == tab.boardUrl }
        }
    }

    /**
     * 板タブを閉じ、対応する [BoardViewModel] を解放する。
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun closeBoardTab(tab: BoardTabInfo) {
        boardViewModelMap.remove(tab.boardUrl)?.release()
        _openBoardTabs.update { current ->
            current.filterNot { it.boardUrl == tab.boardUrl }
        }
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

    /**
     * 板タブのスクロール位置を保存する。
     */
    fun updateBoardScrollPosition(
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int
    ) {
        _openBoardTabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.boardUrl == boardUrl) {
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

    /**
     * 指定キーのタブ情報を取得する。
     */
    fun getTabInfo(tabKey: String, boardUrl: String): ThreadTabInfo? {
        return _openThreadTabs.value.find { it.key == tabKey && it.boardUrl == boardUrl }
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
