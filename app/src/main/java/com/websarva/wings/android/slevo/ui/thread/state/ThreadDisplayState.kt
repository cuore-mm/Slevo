package com.websarva.wings.android.slevo.ui.thread.state

/**
 * 画面表示用に整形された状態を保持するデータクラス
 */
data class DisplayPost(
    val num: Int,
    val post: ReplyInfo,
    val dimmed: Boolean,
    val isAfter: Boolean,
)

data class ThreadDisplayState(
    val visiblePosts: List<DisplayPost>,
    val displayPosts: List<ReplyInfo>,
    val replyCounts: List<Int>,
    val firstAfterIndex: Int,
)

fun ThreadUiState.toDisplayState(
    firstNewResNo: Int?,
    prevResCount: Int,
): ThreadDisplayState {
    val posts = this.posts ?: emptyList()
    val order = if (sortType == ThreadSortType.TREE) {
        treeOrder
    } else {
        (1..posts.size).toList()
    }

    val orderedPosts = if (sortType == ThreadSortType.TREE && firstNewResNo != null) {
        val parentMap = mutableMapOf<Int, Int>()
        val stack = mutableListOf<Int>()
        order.forEach { num ->
            val depth = treeDepthMap[num] ?: 0
            while (stack.size > depth) stack.removeLast()
            parentMap[num] = stack.lastOrNull() ?: 0
            stack.add(num)
        }
        val before = mutableListOf<DisplayPost>()
        val after = mutableListOf<DisplayPost>()
        val insertedParents = mutableSetOf<Int>()
        order.forEach { num ->
            val parent = parentMap[num] ?: 0
            val post = posts.getOrNull(num - 1) ?: return@forEach
            if (num < firstNewResNo) {
                before.add(DisplayPost(num, post, false, false))
            } else {
                val parentOld = parent in 1 until firstNewResNo
                if (parentOld && num <= prevResCount) {
                    before.add(DisplayPost(num, post, false, false))
                } else {
                    if (parentOld) {
                        if (insertedParents.add(parent)) {
                            posts.getOrNull(parent - 1)?.let { p ->
                                after.add(DisplayPost(parent, p, true, true))
                            }
                        }
                    }
                    after.add(DisplayPost(num, post, false, true))
                }
            }
        }
        before + after
    } else {
        order.mapNotNull { num ->
            posts.getOrNull(num - 1)?.let { post ->
                val isAfter = firstNewResNo != null && num >= firstNewResNo
                DisplayPost(num, post, false, isAfter)
            }
        }
    }

    val filteredPosts =
        if (searchQuery.isNotBlank()) {
            orderedPosts.filter { it.post.content.contains(searchQuery, ignoreCase = true) }
        } else {
            orderedPosts
        }
    val visiblePosts = filteredPosts.filterNot { it.num in ngPostNumbers }
    val displayPosts = visiblePosts.map { it.post }
    val replyCounts = visiblePosts.map { p -> replySourceMap[p.num]?.size ?: 0 }
    val firstAfterIndex = visiblePosts.indexOfFirst { it.isAfter }

    return ThreadDisplayState(
        visiblePosts = visiblePosts,
        displayPosts = displayPosts,
        replyCounts = replyCounts,
        firstAfterIndex = firstAfterIndex,
    )
}

