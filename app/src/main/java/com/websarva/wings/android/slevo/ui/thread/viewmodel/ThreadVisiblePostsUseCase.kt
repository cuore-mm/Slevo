package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.ThreadListItem
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.util.toHiragana
import javax.inject.Inject

/**
 * スレッド表示用の投稿行リストを構築するユースケース。
 *
 * 新着グループ、検索、NG、ツリー派生情報を適用し、LazyColumn 用の最終表示行へ変換する。
 */
class ThreadVisiblePostsUseCase @Inject constructor() {

    /**
     * 表示用の投稿行、返信数、新着開始位置をまとめて構築する。
     */
    fun buildVisibleRows(
        posts: List<ThreadPostUiModel>,
        groups: List<ThreadPostGroup>,
        sortType: ThreadSortType,
        treeOrder: List<Int>,
        treeDepthMap: Map<Int, Int>,
        treeRootMap: Map<Int, Int>,
        latestArrivalGroupIndex: Int?,
        searchQuery: String,
        ngPostNumbers: Set<Int>,
        replySourceMap: Map<Int, List<Int>>,
    ): ThreadVisiblePostsResult {
        // --- グループ反映 ---
        val groupedPosts = buildGroupedDisplayPosts(
            posts = posts,
            groups = groups,
            sortType = sortType,
            treeOrder = treeOrder,
            treeDepthMap = treeDepthMap,
            treeRootMap = treeRootMap,
            latestArrivalGroupIndex = latestArrivalGroupIndex,
        )

        // --- 検索フィルタ ---
        val normalizedQuery = searchQuery.toHiragana()
        val filteredPosts = if (normalizedQuery.isNotBlank()) {
            groupedPosts.filter {
                it.second.post.body.content.toHiragana().contains(normalizedQuery, ignoreCase = true)
            }
        } else {
            groupedPosts
        }

        // --- NGフィルタ ---
        val visibleGroupedPosts = filteredPosts.filterNot { it.second.num in ngPostNumbers }

        // --- 最終表示行に変換 ---
        val visiblePostRows = buildThreadListPostRows(visibleGroupedPosts)
        val replyCounts = visiblePostRows.map { row -> replySourceMap[row.displayPost.num]?.size ?: 0 }
        val firstAfterIndex = visiblePostRows.indexOfFirst { it.displayPost.isAfter }

        return ThreadVisiblePostsResult(
            visiblePostRows = visiblePostRows,
            replyCounts = replyCounts,
            firstAfterIndex = firstAfterIndex,
        )
    }

    /**
     * グループ情報から表示対象の投稿リストを組み立てる。
     */
    private fun buildGroupedDisplayPosts(
        posts: List<ThreadPostUiModel>,
        groups: List<ThreadPostGroup>,
        sortType: ThreadSortType,
        treeOrder: List<Int>,
        treeDepthMap: Map<Int, Int>,
        treeRootMap: Map<Int, Int>,
        latestArrivalGroupIndex: Int?,
    ): List<Pair<Int, DisplayPost>> {
        // --- グループ毎の変換 ---
        val result = mutableListOf<Pair<Int, DisplayPost>>()
        groups.forEachIndexed { index, group ->
            val endResNo = group.endResNo.coerceAtMost(posts.size)
            if (endResNo <= 0 || group.startResNo > endResNo) {
                // 無効な範囲はスキップする。
                return@forEachIndexed
            }
            val targetPosts = posts.take(endResNo)
            val order = if (sortType == ThreadSortType.TREE && treeOrder.isNotEmpty()) {
                treeOrder.filter { it <= endResNo }
            } else {
                (1..endResNo).toList()
            }
            val firstNewResNo = if (group.prevResCount == 0) null else group.startResNo
            val groupPosts = buildGroupDisplayPosts(
                posts = targetPosts,
                order = order,
                sortType = sortType,
                treeDepthMap = treeDepthMap,
                treeRootMap = treeRootMap,
                firstNewResNo = firstNewResNo,
                prevResCount = group.prevResCount,
            )
            val markAsAfter = latestArrivalGroupIndex != null && index == latestArrivalGroupIndex
            val adjusted = groupPosts.map { post -> post.copy(isAfter = markAsAfter) }
            result.addAll(adjusted.map { index to it })
        }
        return result
    }
}

/**
 * スレッド表示用の最終行リストと補助値。
 */
data class ThreadVisiblePostsResult(
    val visiblePostRows: List<ThreadListItem.PostRow>,
    val replyCounts: List<Int>,
    val firstAfterIndex: Int,
)
