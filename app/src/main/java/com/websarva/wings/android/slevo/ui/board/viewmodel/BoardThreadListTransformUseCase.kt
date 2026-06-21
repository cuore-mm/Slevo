package com.websarva.wings.android.slevo.ui.board.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.util.ThreadNewResCalculator
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.util.toHiragana
import javax.inject.Inject

/**
 * 板一覧の検索・NG・並び替え・既読統合を行うユースケース。
 */
class BoardThreadListTransformUseCase @Inject constructor() {

    /**
     * 検索、NG、ソート、新着優先を適用して表示用スレッド一覧を返す。
     */
    fun filterAndSort(
        allThreads: List<ThreadInfo>,
        searchQuery: String,
        threadTitleNg: List<Pair<Long?, Regex>>,
        boardId: Long,
        sortKey: ThreadSortKey,
        ascending: Boolean,
    ): List<ThreadInfo> {
        // --- Search ---
        val normalizedQuery = searchQuery.toHiragana()
        val searchFiltered = if (normalizedQuery.isNotBlank()) {
            allThreads.filter { it.title.toHiragana().contains(normalizedQuery, ignoreCase = true) }
        } else {
            allThreads
        }

        // --- NG filter ---
        val filteredList = searchFiltered.filterNot { thread ->
            threadTitleNg.any { (targetBoardId, regex) ->
                (targetBoardId == null || targetBoardId == boardId) && regex.containsMatchIn(thread.title)
            }
        }

        // --- Sort ---
        val (normalThreads, largeKeyThreads) = filteredList.partition { thread ->
            thread.key.toLongOrNull()?.let { it < THREAD_KEY_THRESHOLD } ?: true
        }
        val sortedList = applySort(
            list = normalThreads,
            sortKey = sortKey,
            ascending = ascending,
            searchQuery = searchQuery,
        ) + largeKeyThreads

        // --- New thread ordering ---
        val (newThreads, existingThreads) = sortedList.partition { it.isNew }
        return newThreads + existingThreads
    }

    /**
     * 履歴を統合して既読・新着数を反映した一覧へ変換する。
     */
    fun mergeHistory(
        baseThreads: List<ThreadInfo>,
        historyMap: Map<String, ThreadHistoryDao.HistorySimple>,
    ): List<ThreadInfo> {
        return baseThreads.map { thread ->
            val history = historyMap[thread.key]
            if (history != null) {
                val newResCount = ThreadNewResCalculator.calculate(
                    latestResCount = thread.resCount,
                    readState = history.readState,
                )
                thread.copy(isVisited = true, newResCount = newResCount)
            } else {
                thread
            }
        }
    }

    /**
     * 指定ソート条件でスレッド一覧を並び替える。
     */
    private fun applySort(
        list: List<ThreadInfo>,
        sortKey: ThreadSortKey,
        ascending: Boolean,
        searchQuery: String,
    ): List<ThreadInfo> {
        if (sortKey == ThreadSortKey.DEFAULT && searchQuery.isBlank()) {
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
}
