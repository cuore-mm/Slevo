package com.websarva.wings.android.bbsviewer.ui.drawer

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

    // threadKey + boardUrl をキーとして ThreadViewModel を保持
    private val threadViewModels: MutableMap<String, ThreadViewModel> = mutableMapOf()

    fun openThread(tab: TabInfo) {
        _openTabs.update { current ->
            if (current.any { it.key == tab.key && it.boardUrl == tab.boardUrl }) {
                // 既に開いているタブがあれば、その情報を更新する（スクロール位置は新しいタブの情報を使うか、既存のを維持するか選択）
                current.map {
                    if (it.key == tab.key && it.boardUrl == tab.boardUrl) {
                        // ここでは新しいタブ情報で上書きする例（スクロール位置は引数で渡されたもの、またはデフォルト）
                        tab
                    } else {
                        it
                    }
                }
            } else {
                current + tab // 新しく追加
            }
        }
    }

    fun closeThread(tab: TabInfo) {
        _openTabs.update { current -> current - tab }
        val mapKey = tab.key + tab.boardUrl
        // タブを閉じたら対応する ViewModel も破棄
        threadViewModels.remove(mapKey)
    }

    fun updateScrollPosition(
        tabKey: String,
        boardUrl: String,
        index: Int,
        offset: Int
    ) {
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

    /**
     * 指定キーの ThreadViewModel を取得。存在しなければ Factory から生成して登録する
     */
    fun getOrCreateThreadViewModel(mapKey: String): ThreadViewModel {
        return threadViewModels.getOrPut(mapKey) {
            threadViewModelFactory.create(mapKey)
        }
    }
}
