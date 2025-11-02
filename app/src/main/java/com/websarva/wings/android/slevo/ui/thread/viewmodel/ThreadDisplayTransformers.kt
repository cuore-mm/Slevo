package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ThreadViewModel の表示変換ロジックをテストしやすいように分離したヘルパー群。
 */
internal val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
    timeZone = TimeZone.getTimeZone("Asia/Tokyo")
}

internal fun deriveReplyMaps(posts: List<ReplyInfo>): Triple<Map<String, Int>, List<Int>, Map<Int, List<Int>>> {
    val idCountMap = posts.groupingBy { it.id }.eachCount()
    val idIndexList = run {
        val indexMap = mutableMapOf<String, Int>()
        posts.map { reply ->
            val idx = (indexMap[reply.id] ?: 0) + 1
            indexMap[reply.id] = idx
            idx
        }
    }
    val replySourceMap = run {
        val map = mutableMapOf<Int, MutableList<Int>>()
        val regex = Regex(">>(\\d+)")
        posts.forEachIndexed { idx, reply ->
            regex.findAll(reply.content).forEach { match ->
                val num = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (num in 1..posts.size) {
                    map.getOrPut(num) { mutableListOf() }.add(idx + 1)
                }
            }
        }
        map.mapValues { it.value.toList() }
    }
    return Triple(idCountMap, idIndexList, replySourceMap)
}

internal fun deriveTreeOrder(posts: List<ReplyInfo>): Pair<List<Int>, Map<Int, Int>> {
    val children = mutableMapOf<Int, MutableList<Int>>()
    val parent = IntArray(posts.size + 1)
    val depthMap = mutableMapOf<Int, Int>()
    val regex = Regex("^>>(\\d+)")
    posts.forEachIndexed { idx, reply ->
        val current = idx + 1
        val match = regex.find(reply.content)
        val p = match?.groupValues?.get(1)?.toIntOrNull()
        if (p != null && p in 1 until current) {
            parent[current] = p
            children.getOrPut(p) { mutableListOf() }.add(current)
        }
    }
    val order = mutableListOf<Int>()
    fun dfs(num: Int, depth: Int) {
        order.add(num)
        depthMap[num] = depth
        children[num]?.forEach { child -> dfs(child, depth + 1) }
    }
    for (i in 1..posts.size) {
        if (parent[i] == 0) {
            dfs(i, 0)
        }
    }
    return order to depthMap
}

internal fun buildOrderedPosts(
    posts: List<ReplyInfo>,
    order: List<Int>,
    sortType: ThreadSortType,
    treeDepthMap: Map<Int, Int>,
    prevResCount: Int
): List<DisplayPost> {
    val threshold = prevResCount.coerceIn(0, posts.size)
    return if (sortType == ThreadSortType.TREE) {
        val before = order
            .filter { num -> num <= threshold }
            .mapNotNull { num ->
                posts.getOrNull(num - 1)?.let { post ->
                    val depth = treeDepthMap[num] ?: 0
                    DisplayPost(num, post, dimmed = false, isAfter = false, depth = depth)
                }
            }
        val after = order
            .filter { num -> num > threshold }
            .mapNotNull { num ->
                posts.getOrNull(num - 1)?.let { post ->
                    val depth = treeDepthMap[num] ?: 0
                    DisplayPost(num, post, dimmed = false, isAfter = true, depth = depth)
                }
            }
        before + after
    } else {
        order.mapNotNull { num ->
            posts.getOrNull(num - 1)?.let { post ->
                val isAfter = num > threshold
                val depth = if (sortType == ThreadSortType.TREE) treeDepthMap[num] ?: 0 else 0
                DisplayPost(num, post, false, isAfter, depth)
            }
        }
    }
}

internal fun parseDateToUnix(dateString: String): Long {
    val sanitized = dateString
        .replace(Regex("\\([^)]*\\)"), "")
        .replace(Regex("\\.\\d+"), "")
        .trim()
    return try {
        DATE_FORMAT.parse(sanitized)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}
