package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    private val searchInputValueState = MutableStateFlow(TextFieldValue(""))
    private val pendingSearchFocusRequestIdState = MutableStateFlow<Long?>(null)
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
    private var nextSearchFocusRequestId: Long = 0L

    val uiState: StateFlow<TabListUiState> = combine(
        isSearchModeState,
        searchInputValueState,
        pendingSearchFocusRequestIdState,
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
            searchInputValue = array[1] as TextFieldValue,
            pendingSearchFocusRequestId = array[2] as Long?,
            pendingScrollToTopRequest = array[3] as TabListScrollToTopRequest?,
            selectedBoardTab = (array[4] as TabSelectionState).selectedBoardTab,
            selectedThreadTab = (array[4] as TabSelectionState).selectedThreadTab,
            selectedTabBounds = (array[4] as TabSelectionState).selectedTabBounds,
            pendingCloseBoardTab = array[5] as BoardTabInfo?,
            pendingCloseThreadTab = array[6] as ThreadTabInfo?,
            detailBoardTab = array[7] as BoardTabInfo?,
            detailThreadTab = array[8] as ThreadTabInfo?,
            showBoardInfoBottomSheet = array[9] as Boolean,
            showThreadInfoBottomSheet = array[10] as Boolean,
            isUrlValidating = array[11] as Boolean,
            showUrlDialog = array[12] as Boolean,
            urlErrorMessage = array[13] as String?,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, TabListUiState())

    // --- Search ---

    fun enterSearchMode() {
        cancelTabSelection()
        isSearchModeState.value = true
        nextSearchFocusRequestId += 1
        pendingSearchFocusRequestIdState.value = nextSearchFocusRequestId
    }

    /**
     * 検索モードを終了し、検索入力と未消費の UI 要求を初期状態へ戻す。
     */
    fun closeSearchMode() {
        isSearchModeState.value = false
        searchInputValueState.value = TextFieldValue("")
        pendingSearchFocusRequestIdState.value = null
        pendingScrollToTopRequestState.value = null
    }

    /**
     * 検索バー入力の text と selection を更新し、必要なら検索結果の先頭表示要求を発行する。
     *
     * クエリ文字列の遷移で先頭表示要求を判断しつつ、selection だけが変わる場合も
     * 入力 state 全体は常に保持する。
     */
    fun updateSearchInput(inputValue: TextFieldValue, currentPage: Int) {
        val oldQuery = searchInputValueState.value.text
        val newQuery = inputValue.text

        // --- Query transition handling ---
        when {
            oldQuery.isBlank() && newQuery.isNotBlank() -> {
                searchInputValueState.value = inputValue
                pendingScrollToTopRequestState.value = TabListScrollToTopRequest(
                    page = currentPage,
                    query = newQuery,
                )
            }

            oldQuery.isNotBlank() && newQuery.isNotBlank() && oldQuery != newQuery -> {
                searchInputValueState.value = inputValue
                pendingScrollToTopRequestState.value = TabListScrollToTopRequest(
                    page = currentPage,
                    query = newQuery,
                )
            }

            oldQuery.isNotBlank() && newQuery.isBlank() -> {
                searchInputValueState.value = inputValue
                pendingScrollToTopRequestState.value = null
            }

            else -> {
                searchInputValueState.value = inputValue
            }
        }
    }

    /**
     * 検索クエリを更新し、必要なら検索結果の先頭表示要求を発行する。
     *
     * 空から非空、または別の非空クエリへ変わると、現在表示中ページの検索結果リストを
     * 先頭表示する要求を保持する。非空から空へ戻る場合は、通常リスト復元要求を発行せず
     * 検索結果向けの要求だけをクリアする。
     */
    fun updateSearchQuery(query: String, currentPage: Int) {
        updateSearchInput(
            inputValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            ),
            currentPage = currentPage,
        )
    }

    /**
     * 未消費の検索バー自動フォーカス要求を消費する。
     */
    fun consumePendingSearchFocusRequest() {
        pendingSearchFocusRequestIdState.value = null
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
        searchInputValueState.value = TextFieldValue("")
        pendingSearchFocusRequestIdState.value = null
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
