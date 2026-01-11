package com.websarva.wings.android.slevo.ui.board.viewmodel

import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.util.toHiragana
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
    @Assisted private val uiState: MutableStateFlow<BoardUiState>,
    @Assisted private val scope: CoroutineScope,
) {

    private var originalThreads: List<ThreadInfo>? = null
    private var baseThreads: List<ThreadInfo> = emptyList()
    private var currentHistoryMap: Map<String, Int> = emptyMap()
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
    fun setSearchQuery(query: String) {
        uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    /**
     * 検索モードのON/OFFを切り替える。
     *
     * OFF時は検索クエリをクリアしてから再反映する。
     */
    fun setSearchMode(isActive: Boolean) {
        if (isActive) {
            uiState.update { it.copy(isSearchActive = true) }
        } else {
            uiState.update { it.copy(isSearchActive = false) }
            setSearchQuery("")
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
            // --- Search ---
            val query = uiState.value.searchQuery.toHiragana()
            val searchFiltered = if (query.isNotBlank()) {
                allThreads.filter { it.title.toHiragana().contains(query, ignoreCase = true) }
            } else {
                allThreads
            }

            // --- NG filter ---
            val filteredList = searchFiltered.filterNot { thread ->
                threadTitleNg.any { (boardId, regex) ->
                    (boardId == null || boardId == uiState.value.boardInfo.boardId) &&
                            regex.containsMatchIn(thread.title)
                }
            }

            // --- Sort ---
            val (normalThreads, largeKeyThreads) = filteredList.partition { thread ->
                thread.key.toLongOrNull()?.let { it < THREAD_KEY_THRESHOLD } ?: true
            }

            val sortedList = applySort(
                normalThreads,
                uiState.value.currentSortKey,
                uiState.value.isSortAscending
            ) + largeKeyThreads

            // --- New thread ordering ---
            val (newThreads, existingThreads) = sortedList.partition { it.isNew }
            uiState.update { it.copy(threads = newThreads + existingThreads) }
        }
    }

    /**
     * 指定のソートキーと順序でスレッド一覧を並び替える。
     */
    private fun applySort(
        list: List<ThreadInfo>,
        sortKey: ThreadSortKey,
        ascending: Boolean,
    ): List<ThreadInfo> {
        if (sortKey == ThreadSortKey.DEFAULT && uiState.value.searchQuery.isBlank()) {
            // デフォルト表示かつ検索なしの場合は並び替えを省略する。
            return list
        }
        val sortedList = when (sortKey) {
            ThreadSortKey.DEFAULT -> list
            ThreadSortKey.MOMENTUM -> list.sortedBy { it.momentum }
            ThreadSortKey.RES_COUNT -> list.sortedBy { it.resCount }
            ThreadSortKey.DATE_CREATED -> list.sortedBy { it.key.toLongOrNull() ?: 0L }
        }
        return if (ascending) sortedList else sortedList.reversed()
    }

    /**
     * 既読履歴を突き合わせて未読数・既読状態を更新する。
     */
    fun mergeHistory(historyMap: Map<String, Int>) {
        if (baseThreads.isEmpty()) {
            // 表示対象がない場合は統合処理を行わない。
            return
        }
        val merged = baseThreads.map { thread ->
            val oldRes = historyMap[thread.key]
            if (oldRes != null) {
                val diff = (thread.resCount - oldRes).coerceAtLeast(0)
                thread.copy(isVisited = true, newResCount = diff)
            } else {
                thread
            }
        }
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
                historyRepository.observeHistoryMap(boardUrl)
            ) { threads, historyMap ->
                threads to historyMap
            }.collect { (threads, historyMap) ->
                baseThreads = threads
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
