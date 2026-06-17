package com.websarva.wings.android.slevo.ui.board.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 板のスレッド一覧に対して検索・NG・並び替え・既読統合を行う調整役。
 *
 * Repository の出力と履歴を統合し、UI に提示するリストを生成する。
 */
class ThreadListCoordinator @AssistedInject constructor(
    private val repository: BoardRepository,
    private val historyRepository: ThreadHistoryRepository,
    private val threadStateRepository: ThreadStateRepository,
    private val boardThreadListTransformUseCase: BoardThreadListTransformUseCase,
    @Assisted private val uiState: MutableStateFlow<BoardUiState>,
    @Assisted private val scope: CoroutineScope,
) {

    private var originalThreads: List<ThreadInfo>? = null
    private var baseThreads: List<ThreadInfo> = emptyList()
    private var currentHistoryMap: Map<String, ThreadHistoryDao.HistorySimple> = emptyMap()
    private var isObservingThreads: Boolean = false
    private var threadTitleNg: List<Pair<Long?, Regex>> = emptyList()

    /**
     * スレッドタイトルのNGフィルタを更新し、一覧へ反映する。
     */
    fun updateThreadTitleNg(filters: List<Pair<Long?, Regex>>) {
        threadTitleNg = filters
        applyFiltersAndSort()
    }

    /**
     * 検索クエリを更新し、一覧へ反映する。
     */
    fun updateSearchInput(inputValue: TextFieldValue) {
        uiState.update { it.copy(searchInputValue = inputValue) }
        applyFiltersAndSort()
    }

    /**
     * 文字列クエリだけを使う既存導線から検索入力を更新する。
     */
    fun setSearchQuery(query: String) {
        updateSearchInput(TextFieldValue(query))
    }

    /**
     * 検索モードのON/OFFを切り替える。
     *
     * OFF時は検索クエリをクリアしてから再反映する。
     */
    fun setSearchMode(isActive: Boolean) {
        if (isActive) {
            uiState.update { it.copy(isSearchActive = true, isTabSwipeEnabled = false) }
        } else {
            uiState.update {
                it.copy(
                    isSearchActive = false,
                    isTabSwipeEnabled = true,
                    searchInputValue = TextFieldValue(""),
                )
            }
            applyFiltersAndSort()
        }
    }

    /**
     * 並び替えキーを更新し、一覧へ反映する。
     */
    fun setSortKey(sortKey: ThreadSortKey) {
        uiState.update { it.copy(currentSortKey = sortKey) }
        applyFiltersAndSort()
    }

    /**
     * 昇順/降順を切り替え、一覧へ反映する。
     */
    fun toggleSortOrder() {
        // DEFAULTソートでは順序の反転を行わない。
        if (uiState.value.currentSortKey != ThreadSortKey.DEFAULT) {
            uiState.update { it.copy(isSortAscending = !it.isSortAscending) }
            applyFiltersAndSort()
        }
    }

    /**
     * 検索・NG・ソート・新着優先の順でスレッド一覧を再構成する。
     */
    fun applyFiltersAndSort() {
        originalThreads?.let { allThreads ->
            val threads = boardThreadListTransformUseCase.filterAndSort(
                allThreads = allThreads,
                searchQuery = uiState.value.searchQuery,
                threadTitleNg = threadTitleNg,
                boardId = uiState.value.boardInfo.boardId,
                sortKey = uiState.value.currentSortKey,
                ascending = uiState.value.isSortAscending,
            )
            uiState.update { it.copy(threads = threads) }
        }
    }

    /**
     * 既読履歴を突き合わせて未読数・既読状態を更新する。
     */
    fun mergeHistory(historyMap: Map<String, ThreadHistoryDao.HistorySimple>) {
        if (baseThreads.isEmpty()) {
            // 表示対象がない場合は統合処理を行わない。
            return
        }
        val merged = boardThreadListTransformUseCase.mergeHistory(baseThreads, historyMap)
        currentHistoryMap = historyMap
        originalThreads = merged
        applyFiltersAndSort()
    }

    /**
     * スレッド一覧と履歴を監視し、UI用の一覧を更新し続ける。
     */
    fun startObservingThreads(boardId: Long, boardUrl: String) {
        if (isObservingThreads) {
            // 二重監視を避ける。
            return
        }
        isObservingThreads = true
        scope.launch {
            combine(
                repository.observeThreads(boardId),
                historyRepository.observeHistoryReadStateMap(boardUrl),
                threadStateRepository.observeThreadStateMapByBoard(boardId),
            ) { threads, historyMap, stateMap ->
                Triple(threads, historyMap, stateMap)
            }.collect { (threads, historyMap, stateMap) ->
                baseThreads = threads.map { thread ->
                    val state = stateMap[thread.key]
                    if (state != null) {
                        // thread_states の客観状態を、板一覧キャッシュ由来の表示行へ合成する。
                        thread.copy(title = state.title, resCount = state.latestResCount)
                    } else {
                        thread
                    }
                }
                if (!uiState.value.isLoading) {
                    mergeHistory(historyMap)
                } else {
                    currentHistoryMap = historyMap
                }
            }
        }
    }

    /**
     * リフレッシュ完了後に履歴統合を再実行する。
     */
    fun onRefreshCompleted() {
        mergeHistory(currentHistoryMap)
    }

    /**
     * ThreadListCoordinator を生成するためのファクトリ。
     */
    @AssistedFactory
    interface Factory {
        fun create(
            uiState: MutableStateFlow<BoardUiState>,
            scope: CoroutineScope,
        ): ThreadListCoordinator
    }
}
