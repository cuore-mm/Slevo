package com.websarva.wings.android.bbsviewer.ui.thread.dialog

import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.thread.item.PostItem
import com.websarva.wings.android.bbsviewer.ui.thread.state.ReplyInfo

data class PopupInfo(
    val posts: List<ReplyInfo>,
    val offset: IntOffset,
    val size: IntSize = IntSize.Zero,
)

@Composable
fun ReplyPopup(
    popupStack: SnapshotStateList<PopupInfo>,
    posts: List<ReplyInfo>,
    replySourceMap: Map<Int, List<Int>>,
    idCountMap: Map<String, Int>,
    idIndexList: List<Int>,
    ngPostNumbers: Set<Int>,
    navController: NavHostController,
    boardName: String,
    boardId: Long,
    onClose: () -> Unit
) {
    popupStack.forEachIndexed { index, info ->
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    return IntOffset(
                        info.offset.x,
                        (info.offset.y - popupContentSize.height).coerceAtLeast(0)
                    )
                }
            },
            onDismissRequest = onClose
        ) {
            Card(
                modifier = Modifier.onGloballyPositioned { coords ->
                    val size = coords.size
                    if (size != info.size) {
                        popupStack[index] = info.copy(size = size)
                    }
                }
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
                            idTotal = if (p.id.isBlank()) 1 else idCountMap[p.id] ?: 1,
                            navController = navController,
                            boardName = boardName,
                            boardId = boardId,
                            replyFromNumbers = replySourceMap[postNum]?.filterNot { it in ngPostNumbers } ?: emptyList(),
                            onReplyFromClick = { nums ->
                                val off = IntOffset(
                                    popupStack[index].offset.x,
                                    (popupStack[index].offset.y - popupStack[index].size.height).coerceAtLeast(0)
                                )
                                val targets = nums.filterNot { it in ngPostNumbers }.mapNotNull { n ->
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
                            }
                        )
                        if (i < info.posts.size - 1) {
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
