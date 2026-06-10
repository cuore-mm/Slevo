package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.resolveUrl
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
    private val pendingScrollToTopRequestState = MutableStateFlow<TabListScrollToTopRequest?>(null)
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
        pendingScrollToTopRequestState,
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
            pendingScrollToTopRequest = array[2] as TabListScrollToTopRequest?,
            selectedBoardTab = (array[3] as TabSelectionState).selectedBoardTab,
            selectedThreadTab = (array[3] as TabSelectionState).selectedThreadTab,
            selectedTabBounds = (array[3] as TabSelectionState).selectedTabBounds,
            pendingCloseBoardTab = array[4] as BoardTabInfo?,
            pendingCloseThreadTab = array[5] as ThreadTabInfo?,
            detailBoardTab = array[6] as BoardTabInfo?,
            detailThreadTab = array[7] as ThreadTabInfo?,
            showBoardInfoBottomSheet = array[8] as Boolean,
            showThreadInfoBottomSheet = array[9] as Boolean,
            isUrlValidating = array[10] as Boolean,
            showUrlDialog = array[11] as Boolean,
            urlErrorMessage = array[12] as String?,
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
        pendingScrollToTopRequestState.value = null
    }

    /**
     * 検索クエリを更新し、必要なら検索結果の先頭表示要求を発行する。
     *
     * 空から非空、または別の非空クエリへ変わると、現在表示中ページの検索結果リストを
     * 先頭表示する要求を保持する。非空から空へ戻る場合は、通常リスト復元要求を発行せず
     * 検索結果向けの要求だけをクリアする。
     */
    fun updateSearchQuery(query: String, currentPage: Int) {
        val oldQuery = searchQueryState.value
        val newQuery = query

        when {
            // 空 → 非空: 検索開始。現在表示中ページの検索結果を先頭表示する。
            oldQuery.isBlank() && newQuery.isNotBlank() -> {
                searchQueryState.value = newQuery
                pendingScrollToTopRequestState.value = TabListScrollToTopRequest(
                    page = currentPage,
                    query = newQuery,
                )
            }

            // 非空 → 別の非空: 新しい検索結果を先頭表示する。
            oldQuery.isNotBlank() && newQuery.isNotBlank() && oldQuery != newQuery -> {
                searchQueryState.value = newQuery
                pendingScrollToTopRequestState.value = TabListScrollToTopRequest(
                    page = currentPage,
                    query = newQuery,
                )
            }

            // 非空 → 空: 通常リストへ戻る。復元要求は発行しない。
            oldQuery.isNotBlank() && newQuery.isBlank() -> {
                searchQueryState.value = ""
                pendingScrollToTopRequestState.value = null
            }

            // その他（空→空、同じ非空）: 単純に更新
            else -> {
                searchQueryState.value = newQuery
            }
        }
    }

    /**
     * 復元待ちの先頭表示要求を消費する。
     *
     * UI 側が検索結果の先頭表示を実行した後に呼び出し、
     * 同じクエリに対する再実行を防ぐ。
     */
    fun consumePendingScrollToTopRequest() {
        pendingScrollToTopRequestState.value = null
    }

    /**
     * 検索状態を完全に破棄する。
     *
     * 検索モード、検索クエリ、未消費の検索結果先頭表示要求をすべてクリアする。
     * BottomSheet dismiss 時など、表示コンテキストを終了するときに使用する。
     */
    fun resetSearchState() {
        isSearchModeState.value = false
        searchQueryState.value = ""
        pendingScrollToTopRequestState.value = null
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

    /**
     * URL入力文字列を解決し、板またはスレッドの遷移先を決定する。
     *
     * 解決に失敗した場合はエラーメッセージを設定し、[UrlOpenResult.Error] を返す。
     */
    suspend fun openUrlInput(url: String, invalidUrlMessage: String): UrlOpenResult {
        startUrlValidation()
        return try {
            when (val resolved = resolveUrl(url)) {
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.ItestBoard -> {
                    val host = tabSessionStore.resolveBoardHost(resolved.boardKey, resolved.rawUrl)
                    if (host != null) {
                        val boardUrl = "https://$host/${resolved.boardKey}/"
                        val route = tabSessionStore.normalizeBoardRouteForNavigation(
                            AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl)
                        )
                        setUrlErrorMessage(null)
                        setUrlDialogVisible(false)
                        UrlOpenResult.NavigateBoard(route)
                    } else {
                        UrlOpenResult.Error(invalidUrlMessage)
                    }
                }
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.Thread -> {
                    val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                    val route = tabSessionStore.normalizeThreadRouteForNavigation(
                        AppRoute.Thread(
                            threadKey = resolved.threadKey,
                            boardUrl = boardUrl,
                            boardName = resolved.boardKey,
                            threadTitle = null,
                        )
                    )
                    setUrlErrorMessage(null)
                    setUrlDialogVisible(false)
                    UrlOpenResult.NavigateThread(route)
                }
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.Board -> {
                    val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                    val route = tabSessionStore.normalizeBoardRouteForNavigation(
                        AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl)
                    )
                    setUrlErrorMessage(null)
                    setUrlDialogVisible(false)
                    UrlOpenResult.NavigateBoard(route)
                }
                else -> UrlOpenResult.Error(invalidUrlMessage)
            }
        } finally {
            finishUrlValidation()
        }
    }

    // --- Internal ---

    private data class TabSelectionState(
        val selectedBoardTab: BoardTabInfo? = null,
        val selectedThreadTab: ThreadTabInfo? = null,
        val selectedTabBounds: IntRect? = null,
    )
}
