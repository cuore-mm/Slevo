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

    fun updateThreadTitleNg(filters: List<Pair<Long?, Regex>>) {
        threadTitleNg = filters
        applyFiltersAndSort()
    }

    fun setSearchQuery(query: String) {
        uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun setSearchMode(isActive: Boolean) {
        if (isActive) {
            uiState.update { it.copy(isSearchActive = true) }
        } else {
            uiState.update { it.copy(isSearchActive = false) }
            setSearchQuery("")
        }
    }

    fun setSortKey(sortKey: ThreadSortKey) {
        uiState.update { it.copy(currentSortKey = sortKey) }
        applyFiltersAndSort()
    }

    fun toggleSortOrder() {
        if (uiState.value.currentSortKey != ThreadSortKey.DEFAULT) {
            uiState.update { it.copy(isSortAscending = !it.isSortAscending) }
            applyFiltersAndSort()
        }
    }

    fun applyFiltersAndSort() {
        originalThreads?.let { allThreads ->
            val query = uiState.value.searchQuery.toHiragana()
            val searchFiltered = if (query.isNotBlank()) {
                allThreads.filter { it.title.toHiragana().contains(query, ignoreCase = true) }
            } else {
                allThreads
            }

            val filteredList = searchFiltered.filterNot { thread ->
                threadTitleNg.any { (boardId, regex) ->
                    (boardId == null || boardId == uiState.value.boardInfo.boardId) &&
                        regex.containsMatchIn(thread.title)
                }
            }

            val (normalThreads, largeKeyThreads) = filteredList.partition { thread ->
                thread.key.toLongOrNull()?.let { it < THREAD_KEY_THRESHOLD } ?: true
            }

            val sortedList = applySort(
                normalThreads,
                uiState.value.currentSortKey,
                uiState.value.isSortAscending
            ) + largeKeyThreads

            val (newThreads, existingThreads) = sortedList.partition { it.isNew }
            uiState.update { it.copy(threads = newThreads + existingThreads) }
        }
    }

    private fun applySort(
        list: List<ThreadInfo>,
        sortKey: ThreadSortKey,
        ascending: Boolean,
    ): List<ThreadInfo> {
        if (sortKey == ThreadSortKey.DEFAULT && uiState.value.searchQuery.isBlank()) {
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

    fun mergeHistory(historyMap: Map<String, Int>) {
        if (baseThreads.isEmpty()) return
        val merged = baseThreads.map { thread ->
            val oldRes = historyMap[thread.key]
            if (oldRes != null) {
                val diff = (thread.resCount - oldRes).coerceAtLeast(0)
                thread.copy(isVisited = true, newResCount = diff, isNew = false)
            } else {
                thread
            }
        }
        currentHistoryMap = historyMap
        originalThreads = merged
        applyFiltersAndSort()
    }

    fun startObservingThreads(boardId: Long, boardUrl: String) {
        if (isObservingThreads) return
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

    fun onRefreshCompleted() {
        mergeHistory(currentHistoryMap)
    }

    @AssistedFactory
    interface Factory {
        fun create(
            uiState: MutableStateFlow<BoardUiState>,
            scope: CoroutineScope,
        ): ThreadListCoordinator
    }
}
