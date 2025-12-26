package com.websarva.wings.android.slevo.ui.thread.res

import android.R.attr.name
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel

/**
 * 返信ポップアップ表示に必要な投稿と位置情報を保持する。
 *
 * 表示位置とサイズはポップアップのレイアウト計算に使用する。
 */
data class PopupInfo(
    val posts: List<ThreadPostUiModel>,
    val offset: IntOffset,
    val size: IntSize = IntSize.Zero,
)

// アニメーションの速度（ミリ秒）
private const val POPUP_ANIMATION_DURATION = 160

/**
 * 返信ポップアップの表示と操作イベントを管理する。
 *
 * 投稿の長押しメニューやダイアログは呼び出し側へ委譲する。
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReplyPopup(
    popupStack: SnapshotStateList<PopupInfo>,
    posts: List<ThreadPostUiModel>,
    replySourceMap: Map<Int, List<Int>>,
    idCountMap: Map<String, Int>,
    idIndexList: List<Int>,
    ngPostNumbers: Set<Int>,
    myPostNumbers: Set<Int>,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeight: Float,
    searchQuery: String = "",
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onImageClick: (String) -> Unit,
    onRequestMenu: (PostDialogTarget) -> Unit,
    onShowTextMenu: (String, NgType) -> Unit,
    onClose: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- 表示状態管理 ---
    val visibilityStates = remember { mutableStateListOf<MutableTransitionState<Boolean>>() }

    LaunchedEffect(popupStack.size) {
        while (visibilityStates.size < popupStack.size) {
            visibilityStates.add(MutableTransitionState(false).apply { targetState = true })
        }
        while (visibilityStates.size > popupStack.size) {
            visibilityStates.removeAt(visibilityStates.lastIndex)
        }
    }

    // --- 終了操作 ---
    val closeTopPopup: () -> Unit = {
        if (visibilityStates.isNotEmpty()) {
            visibilityStates.last().targetState = false
        }
    }

    // --- 戻るハンドリング ---
    BackHandler(enabled = popupStack.isNotEmpty()) {
        closeTopPopup()
    }

    // --- ポップアップ描画 ---
    val lastIndex = popupStack.lastIndex
    Box(modifier = Modifier.fillMaxSize()) {
        // --- 背景タップ ---
        if (popupStack.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(popupStack.size) {
                        detectTapGestures(onTap = { closeTopPopup() })
                    }
            )
        }
        popupStack.forEachIndexed { index, info ->
            val isTop = index == lastIndex
            val visibleState = visibilityStates.getOrNull(index)
                ?: MutableTransitionState(false).apply { targetState = true }

            LaunchedEffect(visibleState.currentState, visibleState.targetState) {
                if (!visibleState.currentState && !visibleState.targetState && index == popupStack.lastIndex) {
                    onClose()
                }
            }

            val popupOffset = IntOffset(
                info.offset.x,
                (info.offset.y - info.size.height).coerceAtLeast(0)
            )
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)) + scaleIn(
                    animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)) + scaleOut(
                    animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)
                )
            ) {
                Card(
                    modifier = Modifier
                        .offset { popupOffset }
                        .zIndex(index.toFloat())
                        .onGloballyPositioned { coords ->
                            val size = coords.size
                            if (size != info.size) {
                                popupStack[index] = info.copy(size = size)
                            }
                        }
                        .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
                        .then(
                            if (!isTop) {
                                // 上位のポップアップ以外は操作を無効化する。
                                Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown()
                                            down.consume()
                                            val up = waitForUpOrCancellation()
                                            up?.consume()
                                            // 最上位以外のタップは最上位を閉じる。
                                            if (up != null) {
                                                closeTopPopup()
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.75f
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        info.posts.forEachIndexed { i, p ->
                            val postNum = posts.indexOf(p) + 1
                            PostItem(
                                post = p,
                                postNum = postNum,
                                idIndex = idIndexList[posts.indexOf(p)],
                                idTotal = if (p.header.id.isBlank()) 1 else idCountMap[p.header.id] ?: 1,
                                headerTextScale = headerTextScale,
                                bodyTextScale = bodyTextScale,
                                lineHeight = lineHeight,
                                searchQuery = searchQuery,
                                onUrlClick = onUrlClick,
                                onThreadUrlClick = onThreadUrlClick,
                                onImageClick = onImageClick,
                                onRequestMenu = onRequestMenu,
                                onShowTextMenu = onShowTextMenu,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                isMyPost = postNum in myPostNumbers,
                                replyFromNumbers = replySourceMap[postNum]?.filterNot { it in ngPostNumbers }
                                    ?: emptyList(),
                                onReplyFromClick = { nums ->
                                    val off = IntOffset(
                                        popupStack[index].offset.x,
                                        (popupStack[index].offset.y - popupStack[index].size.height).coerceAtLeast(
                                            0
                                        )
                                    )
                                    val targets =
                                        nums.filterNot { it in ngPostNumbers }.mapNotNull { n ->
                                            posts.getOrNull(n - 1)
                                        }
                                    if (targets.isNotEmpty()) {
                                        popupStack.add(PopupInfo(targets, off))
                                    }
                                },
                                onReplyClick = { num ->
                                    if (num in 1..posts.size && num !in ngPostNumbers) {
                                        val target = posts[num - 1]
                                        val base = popupStack[index]
                                        val offset = IntOffset(
                                            base.offset.x,
                                            (base.offset.y - base.size.height).coerceAtLeast(0)
                                        )
                                        popupStack.add(PopupInfo(listOf(target), offset))
                                    }
                                },
                                onIdClick = { id ->
                                    val base = popupStack[index]
                                    val offset = IntOffset(
                                        base.offset.x,
                                        (base.offset.y - base.size.height).coerceAtLeast(0)
                                    )
                                    val targets = posts.mapIndexedNotNull { idx, post ->
                                        val num = idx + 1
                                        if (post.header.id == id && num !in ngPostNumbers) post else null
                                    }
                                    if (targets.isNotEmpty()) {
                                        popupStack.add(PopupInfo(targets, offset))
                                    }
                                }
                            )
                            if (i < info.posts.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
fun ReplyPopupPreview() {
    val dummyPosts = listOf(
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "名無しさん",
                email = "sage",
                date = "2025/08/23",
                id = "ID:12345",
            ),
            body = ThreadPostUiModel.Body(
                content = "これはテスト投稿です。",
            ),
        ),
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "テストユーザー",
                email = "",
                date = "2025/08/23",
                id = "ID:67890",
            ),
            body = ThreadPostUiModel.Body(
                content = "2つ目の投稿。",
            ),
        ),
    )
    val dummyReplySourceMap = mapOf(1 to listOf(2), 2 to listOf(1))
    val dummyIdCountMap = mapOf("ID:12345" to 1, "ID:67890" to 1)
    val dummyIdIndexList = listOf(0, 1)
    val dummyNgPostNumbers = setOf<Int>()
    val popupStack = mutableStateListOf(
        PopupInfo(
            posts = dummyPosts,
            offset = IntOffset(100, 400)
        )
    )
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            ReplyPopup(
                popupStack = popupStack,
                posts = dummyPosts,
                replySourceMap = dummyReplySourceMap,
                idCountMap = dummyIdCountMap,
                idIndexList = dummyIdIndexList,
                ngPostNumbers = dummyNgPostNumbers,
                myPostNumbers = emptySet(),
                headerTextScale = 0.85f,
                bodyTextScale = 1f,
                lineHeight = DEFAULT_THREAD_LINE_HEIGHT,
                onUrlClick = {},
                onThreadUrlClick = {},
                onImageClick = {},
                onRequestMenu = {},
                onShowTextMenu = { _, _ -> },
                onClose = {},
                searchQuery = "",
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
