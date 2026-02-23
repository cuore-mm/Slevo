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

/**
 * 投稿一覧からID集計と返信関係の派生情報を作成する。
 *
 * IDの総数・通番・返信元の逆引きを同時に算出する。
 */
internal fun deriveReplyMaps(posts: List<ThreadPostUiModel>): Triple<Map<String, Int>, List<Int>, Map<Int, List<Int>>> {
    // --- ID集計 ---
    val idCountMap = posts.groupingBy { it.header.id }.eachCount()
    // --- ID通番 ---
    val idIndexList = run {
        val indexMap = mutableMapOf<String, Int>()
        posts.map { reply ->
            val id = reply.header.id
            val idx = (indexMap[id] ?: 0) + 1
            indexMap[id] = idx
            idx
        }
    }
    // --- 返信元マップ ---
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

/**
 * 投稿一覧からツリー順と深さマップを生成する。
 *
 * `>>n` を親とみなし、親子関係を元にDFS順で並べる。
 */
internal fun deriveTreeOrder(posts: List<ThreadPostUiModel>): Pair<List<Int>, Map<Int, Int>> {
    // --- 親子関係の抽出 ---
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
    // --- DFS順の構築 ---
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

/**
 * 並び順と新着境界を反映した表示用投稿リストを生成する。
 *
 * ツリー表示では必要に応じて親投稿を dimmed として挿入する。
 */
internal fun buildOrderedPosts(
    posts: List<ThreadPostUiModel>,
    order: List<Int>,
    sortType: ThreadSortType,
    treeDepthMap: Map<Int, Int>,
    firstNewResNo: Int?,
    prevResCount: Int
): List<DisplayPost> {
    if (sortType == ThreadSortType.TREE && firstNewResNo != null) {
        // --- 親子リレーション構築 ---
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

        // --- 新着境界の分類 ---
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

        // --- 旧レス側 ---
        val before = mutableListOf<DisplayPost>()
        order.forEach { num ->
            if (beforeSet.contains(num)) {
                posts.getOrNull(num - 1)?.let { post ->
                    val depth = treeDepthMap[num] ?: 0
                    before.add(DisplayPost(num, post, dimmed = false, isAfter = false, depth = depth))
                }
            }
        }

        // --- 新着側 ---
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
        // --- 通常順の生成 ---
        return order.mapNotNull { num ->
            posts.getOrNull(num - 1)?.let { post ->
                val isAfter = firstNewResNo != null && num >= firstNewResNo
                val depth = if (sortType == ThreadSortType.TREE) treeDepthMap[num] ?: 0 else 0
                DisplayPost(num, post, false, isAfter, depth)
            }
        }
    }
}

/**
 * 新着境界から「グループ内の表示用投稿一覧」を切り出す。
 *
 * firstNewResNo が null の場合は全件を返し、それ以外は新着側のみを返す。
 */
internal fun buildGroupDisplayPosts(
    posts: List<ThreadPostUiModel>,
    order: List<Int>,
    sortType: ThreadSortType,
    treeDepthMap: Map<Int, Int>,
    firstNewResNo: Int?,
    prevResCount: Int
): List<DisplayPost> {
    val ordered = buildOrderedPosts(
        posts = posts,
        order = order,
        sortType = sortType,
        treeDepthMap = treeDepthMap,
        firstNewResNo = firstNewResNo,
        prevResCount = prevResCount
    )
    if (firstNewResNo == null) {
        // 初回グループは全件を返す。
        return ordered
    }

    // 先頭の新着位置を検出して新着側のみ返す。
    val firstAfterIndex = ordered.indexOfFirst { it.isAfter }
    return if (firstAfterIndex == -1) emptyList() else ordered.drop(firstAfterIndex)
}

/**
 * スレッド一覧の表示行キーを生成する。
 *
 * 同一レス番号の再登場があっても、行インデックスを含めてキー重複を防ぐ。
 */
internal fun buildThreadListItemKey(index: Int, displayPost: DisplayPost): String {
    return "${index}_${displayPost.num}_${displayPost.dimmed}"
}

/**
 * 投稿ヘッダ日付文字列をUnix時間に変換する。
 *
 * ミリ秒や括弧表記を除去し、失敗時は現在時刻へフォールバックする。
 */
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
