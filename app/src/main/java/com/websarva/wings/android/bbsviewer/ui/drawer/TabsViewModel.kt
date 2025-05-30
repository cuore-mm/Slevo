package com.websarva.wings.android.bbsviewer.ui.drawer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor() : ViewModel() {
    private val _openTabs = MutableStateFlow<List<TabInfo>>(emptyList())
    val openTabs: StateFlow<List<TabInfo>> = _openTabs.asStateFlow()

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
}
