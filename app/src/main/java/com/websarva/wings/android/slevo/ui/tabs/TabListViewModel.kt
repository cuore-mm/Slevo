package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * タブ一覧画面専用の UI 状態と操作を管理する ViewModel。
 *
 * 検索モード、長押し選択、詳細 BottomSheet、URL入力ダイアログなど、
 * タブ一覧画面固有の一時 UI 状態を画面ライフサイクルに紐付けて管理する。
 * タブセッション状態は [TabSessionStore] を正本として参照し、
 * セッション操作は [TabSessionStore] へ委譲する。
 */
@HiltViewModel
class TabListViewModel @Inject constructor(
    val tabSessionStore: TabSessionStore,
) : ViewModel() {

    private val isSearchModeState = MutableStateFlow(false)
    private val searchQueryState = MutableStateFlow("")
    private val tabSelectionState = MutableStateFlow(TabSelectionState())
    private val detailBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val detailThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)
    private val showBoardInfoBottomSheetState = MutableStateFlow(false)
    private val showThreadInfoBottomSheetState = MutableStateFlow(false)
    private val pendingCloseBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val pendingCloseThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)
    private val urlValidationState = MutableStateFlow(false)
    private val urlDialogState = MutableStateFlow(false)
    private val urlErrorState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TabListUiState> = combine(
        isSearchModeState,
        searchQueryState,
        tabSelectionState,
        pendingCloseBoardTabState,
        pendingCloseThreadTabState,
        detailBoardTabState,
        detailThreadTabState,
        showBoardInfoBottomSheetState,
        showThreadInfoBottomSheetState,
        urlValidationState,
        urlDialogState,
        urlErrorState,
    ) { array ->
        TabListUiState(
            isSearchMode = array[0] as Boolean,
            searchQuery = array[1] as String,
            selectedBoardTab = (array[2] as TabSelectionState).selectedBoardTab,
            selectedThreadTab = (array[2] as TabSelectionState).selectedThreadTab,
            selectedTabBounds = (array[2] as TabSelectionState).selectedTabBounds,
            pendingCloseBoardTab = array[3] as BoardTabInfo?,
            pendingCloseThreadTab = array[4] as ThreadTabInfo?,
            detailBoardTab = array[5] as BoardTabInfo?,
            detailThreadTab = array[6] as ThreadTabInfo?,
            showBoardInfoBottomSheet = array[7] as Boolean,
            showThreadInfoBottomSheet = array[8] as Boolean,
            isUrlValidating = array[9] as Boolean,
            showUrlDialog = array[10] as Boolean,
            urlErrorMessage = array[11] as String?,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, TabListUiState())

    // --- Search ---

    fun enterSearchMode() {
        cancelTabSelection()
        isSearchModeState.value = true
    }

    fun closeSearchMode() {
        isSearchModeState.value = false
        searchQueryState.value = ""
    }

    fun updateSearchQuery(query: String) {
        searchQueryState.value = query
    }

    // --- Long-press selection ---

    fun onBoardTabLongPressed(tab: BoardTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(
            selectedBoardTab = tab,
            selectedTabBounds = bounds,
        )
    }

    fun onThreadTabLongPressed(tab: ThreadTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(
            selectedThreadTab = tab,
            selectedTabBounds = bounds,
        )
    }

    fun cancelTabSelection() {
        tabSelectionState.value = TabSelectionState()
        showBoardInfoBottomSheetState.value = false
        showThreadInfoBottomSheetState.value = false
    }

    fun toggleSelectedTabPin() {
        tabSelectionState.value.selectedBoardTab?.let { tab ->
            tabSessionStore.togglePinBoardTab(tab.boardUrl)
        }
        tabSelectionState.value.selectedThreadTab?.let { tab ->
            tabSessionStore.togglePinThreadTab(tab.id)
        }
        cancelTabSelection()
    }

    fun openSelectedTabDetail() {
        tabSelectionState.value.selectedBoardTab?.let {
            detailBoardTabState.value = it
            showBoardInfoBottomSheetState.value = true
        }
        tabSelectionState.value.selectedThreadTab?.let {
            detailThreadTabState.value = it
            showThreadInfoBottomSheetState.value = true
        }
        tabSelectionState.value = TabSelectionState()
    }

    fun requestCloseSelectedTab() {
        tabSelectionState.value.selectedBoardTab?.let { tab ->
            pendingCloseBoardTabState.value = tab
        }
        tabSelectionState.value.selectedThreadTab?.let { tab ->
            pendingCloseThreadTabState.value = tab
        }
        cancelTabSelection()
    }

    fun consumePendingCloseRequest() {
        pendingCloseBoardTabState.value = null
        pendingCloseThreadTabState.value = null
    }

    fun onPageChanged() {
        cancelTabSelection()
    }

    // --- BottomSheet ---

    fun dismissBoardInfoBottomSheet() {
        showBoardInfoBottomSheetState.value = false
    }

    fun dismissThreadInfoBottomSheet() {
        showThreadInfoBottomSheetState.value = false
    }

    // --- URL Dialog ---

    fun startUrlValidation() {
        urlValidationState.value = true
    }

    fun finishUrlValidation() {
        urlValidationState.value = false
    }

    fun setUrlDialogVisible(visible: Boolean) {
        urlDialogState.value = visible
        if (!visible) {
            urlErrorState.value = null
        }
    }

    fun setUrlErrorMessage(message: String?) {
        urlErrorState.value = message
    }

    // --- Internal ---

    private data class TabSelectionState(
        val selectedBoardTab: BoardTabInfo? = null,
        val selectedThreadTab: ThreadTabInfo? = null,
        val selectedTabBounds: IntRect? = null,
    )
}
