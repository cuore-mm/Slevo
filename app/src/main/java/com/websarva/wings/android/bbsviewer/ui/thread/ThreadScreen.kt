package com.websarva.wings.android.bbsviewer.ui.thread

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.websarva.wings.android.bbsviewer.ui.theme.idColor
import com.websarva.wings.android.bbsviewer.ui.theme.replyColor
import com.websarva.wings.android.bbsviewer.ui.util.buildUrlAnnotatedString
import com.websarva.wings.android.bbsviewer.ui.util.extractImageUrls
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PopupInfo(
    val post: ReplyInfo,
    val offset: IntOffset,
    val size: IntSize = IntSize.Zero,
)

// URLをエンコードするためのヘルパー関数
private fun encodeUrl(url: String): String {
    return URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
    listState: LazyListState = rememberLazyListState(),
    navController: NavHostController,
) {
    val popupStack = remember { mutableStateListOf<PopupInfo>() }
    val idCountMap = remember(posts) { posts.groupingBy { it.id }.eachCount() }
    val idIndexList = remember(posts) {
        val indexMap = mutableMapOf<String, Int>()
        posts.map { reply ->
            val idx = (indexMap[reply.id] ?: 0) + 1
            indexMap[reply.id] = idx
            idx
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
            ) {
                if (posts.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                    }
                }

                itemsIndexed(posts) { index, post ->
                    var itemOffset by remember { mutableStateOf(IntOffset.Zero) }
                    PostItem(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            itemOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                        },
                        post = post,
                        postNum = index + 1,
                        idIndex = idIndexList[index],
                        idTotal = idCountMap[post.id] ?: 1,
                        navController = navController,
                        onReplyClick = { num ->
                            if (num in 1..posts.size) {
                                val target = posts[num - 1]
                                val baseOffset = itemOffset
                                val offset = if (popupStack.isEmpty()) {
                                    baseOffset
                                } else {
                                    val last = popupStack.last()
                                    IntOffset(
                                        last.offset.x,
                                        (last.offset.y - last.size.height).coerceAtLeast(0)
                                    )
                                }
                                popupStack.add(PopupInfo(target, offset))
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
            // 中央の区切り線
            VerticalDivider()

            // 右側: 固定の勢いバー
            MomentumBar(
                modifier = Modifier
                    .width(32.dp) // 勢いバー全体の幅を指定
                    .fillMaxHeight(),
                posts = posts,
                lazyListState = listState
            )
        }

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
                onDismissRequest = {
                    if (popupStack.isNotEmpty()) popupStack.removeLast()
                }
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
}

@Composable
fun PostItem(
    modifier: Modifier = Modifier,
    post: ReplyInfo,
    postNum: Int,
    idIndex: Int,
    idTotal: Int,
    navController: NavHostController,
    onReplyClick: ((Int) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { /* クリック処理が必要な場合はここに実装 */ })
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val idColor = idColor(idTotal)
        val headerText = buildAnnotatedString {
            // postNumとname、email、dateを結合
            append("${post.name} ${post.email} ${post.date} ")

            // ID部分にだけ色を適用
            withStyle(style = SpanStyle(color = idColor)) {
                append(if (idTotal > 1) "${post.id} (${idIndex}/${idTotal})" else post.id)
            }
        }

        Row {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = postNum.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                modifier = Modifier.alignByBaseline(),
                text = headerText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val uriHandler = LocalUriHandler.current
        val annotatedText = buildUrlAnnotatedString(
            text = post.content,
            onOpenUrl = { uriHandler.openUri(it) },
            replyColor = replyColor()
        )
        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            onClick = { offset ->
                annotatedText.getStringAnnotations("URL", offset, offset).firstOrNull()
                    ?.let { ann ->
                        uriHandler.openUri(ann.item)
                    }
                annotatedText.getStringAnnotations("REPLY", offset, offset).firstOrNull()
                    ?.let { ann ->
                        ann.item.toIntOrNull()?.let { onReplyClick?.invoke(it) }
                    }
            }
        )

        val imageUrls = remember(post.content) { extractImageUrls(post.content) }
        imageUrls.forEach { url ->
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clickable { // 画像クリック時の処理
                        navController.navigate(
                            AppRoute.ImageViewer(imageUrl = encodeUrl(url))
                        )
                    }
            )
        }
    }
}

@Composable
private fun MomentumBar(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
    lazyListState: LazyListState
) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val maxBarWidthDp = 24.dp

    Canvas(modifier = modifier) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        val maxBarWidthPx = maxBarWidthDp.toPx()

        if (posts.size > 1) {
            val postHeight = canvasHeight / posts.size

            // 1. 滑らかな勢いバーの図形（Path）を作成
            val path = Path().apply {
                // 左上からスタート
                moveTo(0f, 0f)

                // 各投稿の勢いの点を滑らかな曲線で結ぶ
                for (i in 0 until posts.size - 1) {
                    val currentMomentum = posts[i].momentum
                    val nextMomentum = posts[i + 1].momentum

                    // X座標の計算を「勢いの幅」そのものに変更
                    val currentX = maxBarWidthPx * currentMomentum
                    val currentY = i * postHeight

                    val nextX = maxBarWidthPx * nextMomentum
                    val nextY = (i + 1) * postHeight

                    val controlPointX = currentX
                    val controlPointY = (currentY + nextY) / 2
                    val endPointX = (currentX + nextX) / 2
                    val endPointY = (currentY + nextY) / 2

                    // 最初の点のみlineToを使い、以降はベジェ曲線でつなぐ
                    if (i == 0) {
                        lineTo(currentX, currentY)
                    }

                    quadraticBezierTo(
                        x1 = controlPointX,
                        y1 = controlPointY,
                        x2 = endPointX,
                        y2 = endPointY
                    )
                }

                // パスの最後を閉じる
                val lastX = maxBarWidthPx * posts.last().momentum
                lineTo(lastX, canvasHeight) // 最後の勢いの点から真下へ
                lineTo(0f, canvasHeight) // 左下へ
                close() // パスを閉じる
            }

            // 作成したパスを描画
            drawPath(path, color = barColor)

            // 2. 現在のスクロール位置を示すインジケーターを描画
            val firstVisible = lazyListState.firstVisibleItemIndex
            val visibleCount = lazyListState.layoutInfo.visibleItemsInfo.size
            if (visibleCount > 0) {
                val indicatorTop = firstVisible * postHeight
                val indicatorHeight = visibleCount * postHeight
                drawRect(
                    color = indicatorColor,
                    topLeft = Offset(x = 0f, y = indicatorTop),
                    size = Size(width = canvasWidth, height = indicatorHeight)
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Preview(showBackground = true)
@Composable
fun ThreadScreenPreview() {
    ThreadScreen(
        posts = listOf(
            ReplyInfo(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:40:25.769",
                id = "test1",
                content = "これはテスト投稿です。"
            ),
            ReplyInfo(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:41:00.123",
                id = "test2",
                content = "別のテスト投稿です。"
            )
        ),
        navController = NavHostController(LocalContext.current),
    )
}

@Preview(showBackground = true)
@Composable
fun ReplyCardPreview() {
    PostItem(
        post = ReplyInfo(
            name = "風吹けば名無し (ｵｰﾊﾟｲW ddad-g3Sx [2001:268:98f4:c793:*])",
            email = "sage",
            date = "1/21(月) 15:43:45.34",
            id = "testnanjj",
            content = "ガチで終わった模様"
        ),
        postNum = 1,
        idIndex = 1,
        idTotal = 1,
        navController = NavHostController(LocalContext.current),
    )
}
