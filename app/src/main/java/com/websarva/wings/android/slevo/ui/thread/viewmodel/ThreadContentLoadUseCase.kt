package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.util.ThreadInfoDerivedCalculator
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import javax.inject.Inject

/**
 * dat 取得結果からスレッド表示に必要な派生情報を構築するユースケース。
 *
 * 投稿本文の取得、投稿UIモデル変換、返信/ツリー派生情報、スレ情報の導出をまとめて扱う。
 */
class ThreadContentLoadUseCase @Inject constructor(
    private val threadRefreshUseCase: ThreadRefreshUseCase,
) {

    /**
     * スレッド本文を取得し、表示用の派生情報へ変換する。
     */
    suspend fun load(
        boardUrl: String,
        threadKey: String,
        onProgress: (Float) -> Unit,
        boardId: Long = 0L,
        boardName: String = "",
        threadTitle: String = "",
    ): ThreadContentLoadResult? {
        val (host, board) = parseBoardUrl(boardUrl) ?: return null
        val refreshed = threadRefreshUseCase.refresh(
            ThreadRefreshRequest(
                threadId = ThreadId.of(host, board, threadKey),
                boardUrl = boardUrl,
                boardId = boardId,
                boardName = boardName,
                threadKey = threadKey,
                threadTitle = threadTitle,
                onProgress = onProgress,
            )
        ) ?: return null
        return buildResult(refreshed.posts to refreshed.title, threadKey)
    }

    /**
     * 取得済みデータから、UI 反映に必要な派生情報を組み立てる。
     */
    fun buildResult(
        threadData: Pair<List<ReplyInfo>, String?>,
        threadKey: String,
    ): ThreadContentLoadResult {
        // --- 投稿一覧の変換 ---
        val (posts, title) = threadData
        val uiPosts = posts.map { it.toThreadPostUiModel() }

        // --- 派生マップ ---
        val (idCountMap, idIndexList, replySourceMap) = deriveReplyMaps(uiPosts)
        val (treeOrder, treeDepthMap) = deriveTreeOrder(uiPosts)
        val treeRootMap = deriveTreeRoots(treeOrder, treeDepthMap)

        // --- スレ情報 ---
        val resCount = uiPosts.size
        val derived = ThreadInfoDerivedCalculator.calculate(
            threadKey = threadKey,
            resCount = resCount,
        )

        return ThreadContentLoadResult(
            uiPosts = uiPosts,
            threadTitle = title,
            resCount = resCount,
            threadDate = derived.date,
            momentum = derived.momentum,
            idCountMap = idCountMap,
            idIndexList = idIndexList,
            replySourceMap = replySourceMap,
            treeOrder = treeOrder,
            treeDepthMap = treeDepthMap,
            treeRootMap = treeRootMap,
        )
    }
}

/**
 * スレッド本文取得後に UI 反映へ渡す派生情報の集合。
 */
data class ThreadContentLoadResult(
    val uiPosts: List<ThreadPostUiModel>,
    val threadTitle: String?,
    val resCount: Int,
    val threadDate: ThreadDate,
    val momentum: Double,
    val idCountMap: Map<String, Int>,
    val idIndexList: List<Int>,
    val replySourceMap: Map<Int, List<Int>>,
    val treeOrder: List<Int>,
    val treeDepthMap: Map<Int, Int>,
    val treeRootMap: Map<Int, Int>,
)
