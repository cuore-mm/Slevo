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
                current // 既に開いている場合は何もしない
            } else {
                current + tab // 新しく追加
            }
        }
    }

    fun closeThread(tab: TabInfo) {
        _openTabs.update { current -> current - tab }
    }

    // 必要に応じて、タブをアクティブにする、順序を変更するなどの機能を追加
}
