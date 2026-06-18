package com.websarva.wings.android.slevo.ui.board.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.common.bookmark.BoardTarget
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogSuccess
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 板画面 route 単位で `BoardUiState` を直接合成する ViewModel。
 *
 * 板タブ情報、SessionState、Repository Flow、ブックマーク、設定、NG を結合し、
 * route レベルでタブ key ごとの `BoardUiState` を遅延生成する。
 */
@HiltViewModel
class BoardRouteViewModel @Inject constructor(
    private val tabSessionStore: TabSessionStore,
    private val boardRepository: BoardRepository,
    private val bookmarkBoardRepository: BookmarkBoardRepository,
    private val ngRepository: NgRepository,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val threadStateRepository: ThreadStateRepository,
    private val boardThreadListTransformUseCase: BoardThreadListTransformUseCase,
    private val logger: AppLogger,
) : ViewModel() {

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }

    private val uiStateCache = mutableMapOf<String, StateFlow<BoardUiState>>()
    private val initializationJobs = mutableMapOf<String, Job>()
    private val postSuccessCollectJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            tabSessionStore.openBoardTabs.collect { tabs ->
                evictClosedTabs(tabs.map { it.boardUrl }.toSet())
                attachPostSuccessCollectors(tabs)
            }
        }
    }

    /** 現在選択中の板タブ key。 */
    val selectedTabKey: StateFlow<String?> = tabSessionStore.selectedBoardTabKey

    /** 選択中タブの `UiState` を返す。 */
    val selectedUiState: StateFlow<BoardUiState> = selectedTabKey
        .flatMapLatest { tabKey -> if (tabKey == null) flowOf(BoardUiState()) else uiStateFor(tabKey) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = BoardUiState(),
        )

    /** 指定タブ key の `UiState` Flow を返す。 */
    fun uiStateFor(tabKey: String): StateFlow<BoardUiState> {
        return uiStateCache.getOrPut(tabKey) { createUiStateFlow(tabKey) }
    }

    /** `uiStateFor` と同じ内容を `Flow` として公開する互換 API。 */
    fun observeUiState(tabKey: String): Flow<BoardUiState> = uiStateFor(tabKey)

    /** 指定板タブのブックマークシート holder を返す。 */
    fun bookmarkSheetHolderFor(tabKey: String): BookmarkBottomSheetStateHolder =
        tabSessionStore.boardBookmarkSheetHolder(tabKey)

    /** 指定板タブの投稿ダイアログコントローラを返す。 */
    fun postDialogActionsFor(tabKey: String): PostDialogController =
        tabSessionStore.boardPostDialogController(tabKey)

    /** 指定板タブの投稿ダイアログに画像をアップロードする。 */
    fun uploadPostDialogImage(tabKey: String, context: Context, uri: Uri) {
        tabSessionStore.boardUploadPostDialogImage(tabKey, context, uri)
    }

    /** 指定板タブのブックマークシートを開く。 */
    fun openBookmarkSheet(tabKey: String) {
        val state = uiStateFor(tabKey).value
        val boardInfo = state.boardInfo
        if (boardInfo.url.isBlank()) return
        tabSessionStore.boardBookmarkSheetHolder(tabKey).open(
            listOf(
                BoardTarget(
                    boardInfo = boardInfo,
                    currentGroupId = state.bookmarkStatusState.selectedGroup?.id,
                )
            )
        )
    }

    /** 指定板タブのスレッド一覧を更新する。 */
    fun refreshBoard(tabKey: String) {
        val tab = tabSessionStore.openBoardTabs.value.find { it.boardUrl == tabKey } ?: return
        updateBoardSessionState(tabKey) { it.copy(isLoading = true, loadProgress = 0f) }
        viewModelScope.launch { refreshBoardInternal(tab, isManual = true) }
    }

    /** 選択中タブ key を更新する。 */
    fun selectTab(boardUrl: String?) {
        tabSessionStore.selectBoardTab(boardUrl)
    }

    /** 検索入力状態を更新する。 */
    fun updateSearchInput(tabKey: String, inputValue: TextFieldValue) {
        updateBoardSessionState(tabKey) { it.copy(searchInputValue = inputValue) }
    }

    /** 検索モードの ON/OFF を切り替える。 */
    fun setSearchMode(tabKey: String, isActive: Boolean) {
        updateBoardSessionState(tabKey) {
            if (isActive) {
                it.copy(isSearchActive = true, isTabSwipeEnabled = false)
            } else {
                it.copy(
                    isSearchActive = false,
                    isTabSwipeEnabled = true,
                    searchInputValue = TextFieldValue(""),
                )
            }
        }
    }

    /** ソートキーを更新する。 */
    fun setSortKey(tabKey: String, sortKey: ThreadSortKey) {
        updateBoardSessionState(tabKey) { it.copy(currentSortKey = sortKey) }
    }

    /** 昇順/降順を切り替える。 */
    fun toggleSortOrder(tabKey: String) {
        val session = tabSessionStore.getBoardSessionState(tabKey)
        if (session.currentSortKey == ThreadSortKey.DEFAULT) return
        updateBoardSessionState(tabKey) { it.copy(isSortAscending = !it.isSortAscending) }
    }

    /** ソートシートを開く。 */
    fun openSortBottomSheet(tabKey: String) {
        updateBoardSessionState(tabKey) { it.copy(showSortSheet = true) }
    }

    /** ソートシートを閉じる。 */
    fun closeSortBottomSheet(tabKey: String) {
        updateBoardSessionState(tabKey) { it.copy(showSortSheet = false) }
    }

    /** スレッド情報シートを開く。 */
    fun openThreadInfoSheet(tabKey: String, threadInfo: ThreadInfo) {
        val boardUrl = uiStateFor(tabKey).value.boardInfo.url
        if (boardUrl.isBlank()) return
        updateBoardSessionState(tabKey) {
            it.copy(showThreadInfoSheet = true, threadInfoSheetTarget = threadInfo.copy(url = boardUrl))
        }
    }

    /** スレッド情報シートを閉じる。 */
    fun closeThreadInfoSheet(tabKey: String) {
        updateBoardSessionState(tabKey) { it.copy(showThreadInfoSheet = false) }
    }

    /** 板情報シートを開く。 */
    fun openBoardInfoSheet(tabKey: String) {
        updateBoardSessionState(tabKey) { it.copy(showBoardInfoSheet = true) }
    }

    /** 板情報シートを閉じる。 */
    fun closeBoardInfoSheet(tabKey: String) {
        updateBoardSessionState(tabKey) { it.copy(showBoardInfoSheet = false) }
    }

    /** スクロールリセットを消費する。 */
    fun consumeResetScroll(tabKey: String) {
        updateBoardSessionState(tabKey) { it.copy(resetScroll = false) }
    }

    /** Toast を消費する。 */
    fun consumeToast(tabKey: String) {
        updateBoardSessionState(tabKey) { it.copy(pendingToastResId = null) }
    }

    /** タブごとの共有 `UiState` Flow を組み立てる。 */
    private fun createUiStateFlow(tabKey: String): StateFlow<BoardUiState> {
        val tabFlow = tabSessionStore.openBoardTabs.map { tabs -> tabs.find { it.boardUrl == tabKey } }
        val sessionFlow = tabSessionStore.boardSessionStates.map { states -> states[tabKey] ?: BoardSessionState() }
        val bookmarkSheetStateFlow = tabSessionStore.boardBookmarkSheetHolder(tabKey).uiState
        val bookmarkStatusFlow = tabFlow.flatMapLatest { tab ->
            if (tab == null) {
                flowOf(BookmarkStatusState())
            } else {
                bookmarkBoardRepository.getBoardWithBookmarkAndGroupByUrlFlow(tab.boardUrl).map { boardWithBookmark ->
                    val group = boardWithBookmark?.bookmarkWithGroup?.group
                    BookmarkStatusState(isBookmarked = group != null, selectedGroup = group)
                }
            }
        }
        val settingsFlow = settingsRepository.observeGestureSettings()
        val threadsFlow = tabFlow.flatMapLatest { tab ->
            if (tab == null) {
                flowOf(emptyList())
            } else {
                combine(
                    boardRepository.observeThreads(tab.boardId),
                    historyRepository.observeHistoryReadStateMap(tab.boardUrl),
                    threadStateRepository.observeThreadStateMapByBoard(tab.boardId),
                    ngRepository.observeNgs(),
                    sessionFlow,
                ) { baseThreads, historyMap, stateMap, ngList, session ->
                    val mergedStateThreads = baseThreads.map { thread ->
                        val state = stateMap[thread.key]
                        if (state != null) {
                            // thread_states の客観状態を一覧表示へ合成する。
                            thread.copy(title = state.title, resCount = state.latestResCount)
                        } else {
                            thread
                        }
                    }
                    val historyMerged = boardThreadListTransformUseCase.mergeHistory(mergedStateThreads, historyMap)
                    val titleNg = ngList.filter { it.type == com.websarva.wings.android.slevo.data.model.NgType.THREAD_TITLE }
                        .mapNotNull { ng ->
                            runCatching {
                                val regex = if (ng.isRegex) Regex(ng.pattern) else Regex(Regex.escape(ng.pattern))
                                ng.boardId to regex
                            }.getOrNull()
                        }
                    boardThreadListTransformUseCase.filterAndSort(
                        allThreads = historyMerged,
                        searchQuery = session.searchQuery,
                        threadTitleNg = titleNg,
                        boardId = tab.boardId,
                        sortKey = session.currentSortKey,
                        ascending = session.isSortAscending,
                    )
                }
            }
        }

        val baseUiStateFlow = combine(
            tabFlow,
            sessionFlow,
            bookmarkStatusFlow,
            settingsFlow,
            threadsFlow,
        ) { tab, session, bookmarkStatus, gestureSettings, threads ->
            BoardRouteBaseUiStateInput(
                tab = tab,
                session = session,
                bookmarkStatus = bookmarkStatus,
                gestureSettings = gestureSettings,
                threads = threads,
            )
        }

        return combine(baseUiStateFlow, bookmarkSheetStateFlow) { baseInput, bookmarkSheetState ->
            val tab = baseInput.tab ?: return@combine BoardUiState()
            val session = baseInput.session
            val boardInfo = BoardInfo(boardId = tab.boardId, name = tab.boardName, url = tab.boardUrl)
            BoardUiState(
                threads = baseInput.threads,
                boardInfo = boardInfo,
                bookmarkStatusState = baseInput.bookmarkStatus,
                bookmarkSheetState = bookmarkSheetState,
                showSortSheet = session.showSortSheet,
                showThreadInfoSheet = session.showThreadInfoSheet,
                threadInfoSheetTarget = session.threadInfoSheetTarget,
                serviceName = tab.serviceName.ifBlank { parseServiceName(tab.boardUrl) },
                showBoardInfoSheet = session.showBoardInfoSheet,
                currentSortKey = session.currentSortKey,
                isSortAscending = session.isSortAscending,
                isSearchActive = session.isSearchActive,
                searchInputValue = session.searchInputValue,
                postDialogState = session.postDialogState,
                resetScroll = session.resetScroll,
                pendingToastResId = session.pendingToastResId,
                loadProgress = session.loadProgress,
                gestureSettings = baseInput.gestureSettings,
                isLoading = session.isLoading,
                isTabSwipeEnabled = session.isTabSwipeEnabled,
            )
        }
            .onStart {
                ensureBoardInitialized(tabKey)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
                initialValue = BoardUiState(),
            )
    }

    /** 指定タブの metadata 補完と初回ロードを必要時だけ開始する。 */
    private fun ensureBoardInitialized(tabKey: String) {
        val tab = tabSessionStore.openBoardTabs.value.find { it.boardUrl == tabKey } ?: return
        if (initializationJobs.containsKey(tabKey)) return
        initializationJobs[tabKey] = viewModelScope.launch {
            val ensuredId = boardRepository.ensureBoard(BoardInfo(tab.boardId, tab.boardName, tab.boardUrl))
            boardRepository.fetchBoardNoname("${tab.boardUrl}SETTING.TXT")?.let { noname ->
                updateBoardSessionState(tabKey) { state ->
                    state.copy(postDialogState = state.postDialogState.copy(namePlaceholder = noname))
                }
            }
            tabSessionStore.boardPostDialogController(tabKey).prepareIdentityHistory(ensuredId)
            if (tabSessionStore.getBoardSessionState(tabKey).loadProgress == 0f && !tabSessionStore.getBoardSessionState(tabKey).isLoading) {
                updateBoardSessionState(tabKey) { it.copy(isLoading = true, loadProgress = 0f) }
                refreshBoardInternal(tab.copy(boardId = ensuredId), isManual = false)
            }
        }
    }

    /** subject.txt を取得して一覧更新を反映する。 */
    private suspend fun refreshBoardInternal(tab: BoardTabInfo, isManual: Boolean) {
        val boardUrl = tab.boardUrl.trimEnd('/')
        val refreshStartAt = System.currentTimeMillis()
        try {
            val success = boardRepository.refreshThreadList(
                boardId = tab.boardId,
                subjectUrl = "$boardUrl/subject.txt",
                refreshStartAt = refreshStartAt,
                isManual = isManual,
            ) { progress ->
                updateBoardSessionState(tab.boardUrl) { state -> state.copy(loadProgress = progress) }
            }
            if (!success) {
                updateBoardSessionState(tab.boardUrl) { it.copy(pendingToastResId = R.string.board_load_failed) }
            }
        } catch (error: Exception) {
            logger.e(message = "Failed to refresh board threads: ${tab.boardUrl}", throwable = error)
            updateBoardSessionState(tab.boardUrl) { it.copy(pendingToastResId = R.string.board_load_failed) }
        } finally {
            updateBoardSessionState(tab.boardUrl) { it.copy(isLoading = false, loadProgress = 1f, resetScroll = true) }
        }
    }

    /** 投稿成功イベントを監視し、対象板の一覧を更新する。 */
    private fun attachPostSuccessCollectors(tabs: List<BoardTabInfo>) {
        val currentKeys = tabs.map { it.boardUrl }.toSet()
        postSuccessCollectJobs.keys.filterNot { it in currentKeys }.forEach { key ->
            postSuccessCollectJobs.remove(key)?.cancel()
        }
        tabs.forEach { tab ->
            if (postSuccessCollectJobs.containsKey(tab.boardUrl)) return@forEach
            postSuccessCollectJobs[tab.boardUrl] = viewModelScope.launch {
                tabSessionStore.boardPostDialogSuccessEvents(tab.boardUrl).collect {
                    refreshBoard(tab.boardUrl)
                }
            }
        }
    }

    /** 指定板タブの SessionState を更新する。 */
    private fun updateBoardSessionState(tabKey: String, transform: (BoardSessionState) -> BoardSessionState) {
        tabSessionStore.updateBoardSessionState(tabKey, transform)
    }

    /** 閉じたタブのキャッシュと baseline 同期を行う。 */
    private fun evictClosedTabs(openKeys: Set<String>) {
        val removedKeys = uiStateCache.keys.filterNot(openKeys::contains)
        removedKeys.forEach { key ->
            val boardId = tabSessionStore.openBoardTabs.value.find { it.boardUrl == key }?.boardId
            if (boardId != null && boardId != 0L) {
                viewModelScope.launch { boardRepository.updateBaseline(boardId, System.currentTimeMillis()) }
            }
            initializationJobs.remove(key)?.cancel()
            postSuccessCollectJobs.remove(key)?.cancel()
            uiStateCache.remove(key)
        }
    }

    /** route ViewModel 終了時に内部ジョブを解放する。 */
    override fun onCleared() {
        initializationJobs.values.forEach(Job::cancel)
        postSuccessCollectJobs.values.forEach(Job::cancel)
        initializationJobs.clear()
        postSuccessCollectJobs.clear()
        uiStateCache.clear()
        super.onCleared()
    }
}

/**
 * BoardUiState 合成前の結合済み入力。
 */
private data class BoardRouteBaseUiStateInput(
    val tab: BoardTabInfo?,
    val session: BoardSessionState,
    val bookmarkStatus: BookmarkStatusState,
    val gestureSettings: com.websarva.wings.android.slevo.data.model.GestureSettings,
    val threads: List<ThreadInfo>,
)
