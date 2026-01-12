package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
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

/**
 * ツリーポップアップの表示対象番号とインデントをまとめたデータ。
 *
 * [numbers] と [indentLevels] は同じ順序で対応する。
 */
internal data class TreePopupSelection(
    val numbers: List<Int>,
    val indentLevels: List<Int>,
)

internal fun deriveReplyMaps(posts: List<ThreadPostUiModel>): Triple<Map<String, Int>, List<Int>, Map<Int, List<Int>>> {
    val idCountMap = posts.groupingBy { it.header.id }.eachCount()
    val idIndexList = run {
        val indexMap = mutableMapOf<String, Int>()
        posts.map { reply ->
            val id = reply.header.id
            val idx = (indexMap[id] ?: 0) + 1
            indexMap[id] = idx
            idx
        }
    }
    val replySourceMap = run {
        val map = mutableMapOf<Int, MutableList<Int>>()
        val regex = Regex(">>(\\d+)")
        posts.forEachIndexed { idx, reply ->
            regex.findAll(reply.body.content).forEach { match ->
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

internal fun deriveTreeOrder(posts: List<ThreadPostUiModel>): Pair<List<Int>, Map<Int, Int>> {
    val children = mutableMapOf<Int, MutableList<Int>>()
    val parent = IntArray(posts.size + 1)
    val depthMap = mutableMapOf<Int, Int>()
    val regex = Regex("^>>(\\d+)")
    posts.forEachIndexed { idx, reply ->
        val current = idx + 1
        val match = regex.find(reply.body.content)
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

/**
 * 指定レスが属するツリー全体の投稿番号とインデントを抽出する。
 *
 * 最上位祖先から全子孫までを順序付きで返す。
 */
internal fun deriveTreePopupSelection(
    postNumber: Int,
    treeOrder: List<Int>,
    treeDepthMap: Map<Int, Int>,
): TreePopupSelection? {
    if (treeOrder.isEmpty()) {
        // ツリー順が空の場合は対象を作れない。
        return null
    }
    val index = treeOrder.indexOf(postNumber)
    if (index == -1) {
        // 対象レスがツリー順に存在しない場合は無視する。
        return null
    }

    var rootIndex = index
    while (rootIndex > 0) {
        val depth = treeDepthMap[treeOrder[rootIndex]] ?: 0
        if (depth == 0) break
        rootIndex--
    }

    val numbers = mutableListOf<Int>()
    val indentLevels = mutableListOf<Int>()
    for (i in rootIndex until treeOrder.size) {
        val num = treeOrder[i]
        val depth = treeDepthMap[num] ?: 0
        if (i != rootIndex && depth == 0) {
            break
        }
        numbers.add(num)
        indentLevels.add(depth)
    }
    if (numbers.size <= 1) {
        // 単独レスのみの場合はポップアップ対象にしない。
        return null
    }
    return TreePopupSelection(numbers = numbers, indentLevels = indentLevels)
}

internal fun buildOrderedPosts(
    posts: List<ThreadPostUiModel>,
    order: List<Int>,
    sortType: ThreadSortType,
    treeDepthMap: Map<Int, Int>,
    firstNewResNo: Int?,
    prevResCount: Int
): List<DisplayPost> {
    if (sortType == ThreadSortType.TREE && firstNewResNo != null) {
        val parentMap = mutableMapOf<Int, Int>()
        val childrenMap = mutableMapOf<Int, MutableList<Int>>()
        val stack = mutableListOf<Int>()
        order.forEach { num ->
            val depth = treeDepthMap[num] ?: 0
            while (stack.size > depth) stack.removeAt(stack.lastIndex)
            val parent = stack.lastOrNull() ?: 0
            parentMap[num] = parent
            childrenMap.getOrPut(parent) { mutableListOf() }.add(num)
            stack.add(num)
        }

        val beforeSet = linkedSetOf<Int>()
        val afterSet = linkedSetOf<Int>()
        for (num in 1..posts.size) {
            val parent = parentMap[num] ?: 0
            if (num < firstNewResNo || (parent in 1 until firstNewResNo && num <= prevResCount)) {
                beforeSet.add(num)
            } else {
                afterSet.add(num)
            }
        }

        val before = mutableListOf<DisplayPost>()
        order.forEach { num ->
            if (beforeSet.contains(num)) {
                posts.getOrNull(num - 1)?.let { post ->
                    val depth = treeDepthMap[num] ?: 0
                    before.add(DisplayPost(num, post, dimmed = false, isAfter = false, depth = depth))
                }
            }
        }

        val after = mutableListOf<DisplayPost>()
        val insertedParents = mutableSetOf<Int>()
        val visited = mutableSetOf<Int>()

        fun traverse(num: Int, shift: Int) {
            val isAfter = afterSet.contains(num)
            if (isAfter && !visited.add(num)) return

            if (isAfter) {
                posts.getOrNull(num - 1)?.let { post ->
                    val depth = (treeDepthMap[num] ?: 0) - shift
                    after.add(DisplayPost(num, post, dimmed = false, isAfter = true, depth = depth))
                }
            }
            childrenMap[num]?.forEach { child -> traverse(child, shift) }
        }

        val afterNums = afterSet.toList().sorted()
        afterNums.forEach { num ->
            if (visited.contains(num)) return@forEach
            val parent = parentMap[num] ?: 0
            if (parent in beforeSet) {
                if (insertedParents.add(parent)) {
                    posts.getOrNull(parent - 1)?.let { p ->
                        after.add(
                            DisplayPost(
                                parent,
                                p,
                                dimmed = true,
                                isAfter = true,
                                depth = 0
                            )
                        )
                    }
                }
                val shift = treeDepthMap[parent] ?: 0
                childrenMap[parent]?.forEach { child -> traverse(child, shift) }
            } else {
                val shift = treeDepthMap[num] ?: 0
                traverse(num, shift)
            }
        }

        return before + after
    } else {
        return order.mapNotNull { num ->
            posts.getOrNull(num - 1)?.let { post ->
                val isAfter = firstNewResNo != null && num >= firstNewResNo
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
