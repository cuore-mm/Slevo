package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.websarva.wings.android.slevo.R
import java.net.URI

/**
 * タブ一覧カードのヘッダー右側に表示する内容を表す型。
 *
 * 板タブでは [None]、スレッドタブでは [ThreadResCount] を渡す。
 * 描画は [TabListCard] 内で一括して行い、呼び出し側の差異を吸収する。
 */
sealed class TabHeaderTrailingContent {
    data object None : TabHeaderTrailingContent()

    /**
     * @param resCount レス総数
     * @param newResCount 新着レス数。0 以下の場合は新着バッジを表示しない。
     */
    data class ThreadResCount(
        val resCount: Int,
        val newResCount: Int,
    ) : TabHeaderTrailingContent()
}

/**
 * タブ一覧カードの共通外枠と情報配置を提供する。
 *
 * 上部はタイトル・追加情報スロット・閉じるボタン、下部は主要タイトルを表示する。
 * 本文タイトルの行数上限は呼び出し側が指定する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TabListCard(
    modifier: Modifier = Modifier,
    bookmarkColor: Color?,
    onClick: () -> Unit,
    onLongPress: (IntRect) -> Unit = {},
    isHiddenForSelection: Boolean = false,
    isPinned: Boolean = false,
    isRemoving: Boolean = false,
    headerTitle: String,
    headerTrailingContent: TabHeaderTrailingContent = TabHeaderTrailingContent.None,
    bodyTitle: String,
    bodyMaxLines: Int = 2,
    onCloseClick: () -> Unit,
    onSwipeDelete: (() -> Unit)? = null,
    isSwipeDeleteEnabled: Boolean = true,
) {
    // --- Selection animation ---
    // 長押し中は透明化したまま拡大状態を保持し、解除時は元カードで縮小復帰を行う。
    val selectionScale by animateFloatAsState(
        targetValue = if (isHiddenForSelection) 1.04f else 1f,
        animationSpec = tween(durationMillis = if (isHiddenForSelection) 220 else 180),
        label = "tabSelectionScale",
    )

    // --- Swipe-to-delete state ---
    val canSwipe = isSwipeDeleteEnabled && !isPinned && onSwipeDelete != null && !isRemoving
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var cardWidthPx by remember { mutableStateOf(0f) }
    var isFlyingOut by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // 削除しきい値はカード幅の40%とする。カード幅未測定時はフォールバックとして100dpを使用。
    val swipeThreshold = remember(cardWidthPx) {
        if (cardWidthPx > 0f) cardWidthPx * 0.4f else with(density) { 100.dp.toPx() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .onGloballyPositioned { coordinates ->
                    cardWidthPx = coordinates.size.width.toFloat()
                }
                .then(
                    if (canSwipe && !isFlyingOut) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (offsetX.value < -swipeThreshold) {
                                        isFlyingOut = true
                                        coroutineScope.launch {
                                            offsetX.animateTo(
                                                targetValue = -cardWidthPx,
                                                animationSpec = tween(durationMillis = 200)
                                            )
                                            onSwipeDelete!!()
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            offsetX.animateTo(0f)
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        val newOffset = (offsetX.value + dragAmount)
                                            .coerceIn(-cardWidthPx, 0f)
                                        offsetX.snapTo(newOffset)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
                .graphicsLayer {
                    scaleX = selectionScale
                    scaleY = selectionScale
                    alpha = if (isHiddenForSelection) 0f else 1f
                    transformOrigin = TransformOrigin.Center
                },
            shape = MaterialTheme.shapes.largeIncreased,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 1.dp,
            ),
        ) {
        val layoutCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .combinedClickable(
                    enabled = !isRemoving && !isFlyingOut,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = {
                        val bounds = layoutCoordinates.value?.boundsInWindow()
                            ?.let {
                                IntRect(
                                    it.left.toInt(),
                                    it.top.toInt(),
                                    it.right.toInt(),
                                    it.bottom.toInt()
                                )
                            }
                            ?: IntRect.Zero

                        onLongPress(bounds)
                    },
                )
                .onGloballyPositioned { coordinates ->
                    layoutCoordinates.value = coordinates
                }
        ) {
            // --- Card body ---
            Column(
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // --- Header ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (bookmarkColor != null) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = bookmarkColor,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (headerTrailingContent) {
                            is TabHeaderTrailingContent.ThreadResCount -> {
                                Text(
                                    text = headerTrailingContent.resCount.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (headerTrailingContent.newResCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "+${headerTrailingContent.newResCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(999.dp),
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            TabHeaderTrailingContent.None -> Unit
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (isPinned) {
                            // 固定済みタブは固定アイコンを表示専用で表示する。
                            // 占有幅とアイコン本体サイズを閉じるボタンと統一する。
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = stringResource(R.string.pinned),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            IconButton(
                                enabled = !isRemoving && !isFlyingOut,
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape,
                                    )
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape,
                                    )
                                    .size(24.dp),
                                onClick = {
                                    // タブクローズ操作は一覧遷移より優先して処理する。
                                    onCloseClick()
                                }
                            ) {
                                Icon(
                                    modifier = Modifier.size(16.dp),
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                )
                            }
                        }
                    }
                }
                // --- Body ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    shape = MaterialTheme.shapes.largeIncreased,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    val bodyStyle = MaterialTheme.typography.bodyMedium
                    val density = LocalDensity.current
                    val verticalPadding = 8.dp
                    val textMinHeight =
                        with(density) { (bodyStyle.lineHeight * bodyMaxLines).toDp() } +
                                verticalPadding * 2

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = textMinHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = bodyTitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = verticalPadding),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = bodyMaxLines,
                            style = bodyStyle,
                        )
                    }
                }
            }
        }
    }
}
}

@Preview(showBackground = true)
@Composable
fun TabListCardPreview() {
    TabListCard(
        modifier = Modifier.padding(12.dp),
        bookmarkColor = null,
        onClick = {},
        headerTitle = "example.com",
        headerTrailingContent = TabHeaderTrailingContent.ThreadResCount(120, 3),
        bodyTitle = "カードのタイトル",
        onCloseClick = {},
    )
}

@Preview(showBackground = true)
@Composable
fun ColoredTabListCardPreview() {
    TabListCard(
        modifier = Modifier.padding(12.dp),
        bookmarkColor = MaterialTheme.colorScheme.primary,
        onClick = {},
        headerTitle = "example.com",
        headerTrailingContent = TabHeaderTrailingContent.ThreadResCount(120, 0),
        bodyTitle = "カードのタイトル",
        onCloseClick = {},
    )
}

/**
 * 板URLからサービス名に相当するホスト名を取り出す。
 */
internal fun extractServiceName(boardUrl: String): String {
    return runCatching { URI(boardUrl).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: boardUrl // URL解析に失敗した場合はそのまま表示する。
}
