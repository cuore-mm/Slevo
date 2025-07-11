package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.material3.Card
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

data class PopupInfo(
    val post: ReplyInfo,
    val offset: IntOffset,
    val size: IntSize = IntSize.Zero,
)

@Composable
fun ReplyPopup(
    popupStack: SnapshotStateList<PopupInfo>,
    posts: List<ReplyInfo>,
    idCountMap: Map<String, Int>,
    idIndexList: List<Int>,
    navController: NavHostController,
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
                PostItem(
                    post = info.post,
                    postNum = posts.indexOf(info.post) + 1,
                    idIndex = idIndexList[posts.indexOf(info.post)],
                    idTotal = idCountMap[info.post.id] ?: 1,
                    navController = navController,
                    onReplyClick = { num ->
                        if (num in 1..posts.size) {
                            val target = posts[num - 1]
                            val base = popupStack[index]
                            val offset = IntOffset(
                                base.offset.x,
                                (base.offset.y - base.size.height).coerceAtLeast(0)
                            )
                            popupStack.add(PopupInfo(target, offset))
                        }
                    }
                )
            }
        }
    }
}
