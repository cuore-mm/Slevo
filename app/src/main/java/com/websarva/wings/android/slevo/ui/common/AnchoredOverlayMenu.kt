package com.websarva.wings.android.slevo.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * アンカー座標を基準に表示するオーバーレイメニュー。
 *
 * メニューはアンカー上/下への配置や重ね表示を選べ、画面外へはみ出す場合は画面内へ補正する。
 */
enum class HorizontalAnchorAlignment {
    Start,
    Center,
    End,
}

/**
 * アンカー基準の縦位置指定。
 *
 * `Above` / `Below` はアンカーと重ならない配置、`OverlapTop` / `OverlapBottom` は
 * アンカーに重ねる配置を表す。`Auto` は上下の空きが大きい側を選ぶ。
 */
enum class VerticalAnchorAlignment {
    Above,
    Below,
    Auto,
    OverlapTop,
    OverlapBottom,
}

/**
 * アンカー座標を基準に表示するオーバーレイメニュー。
 *
 * [horizontalAlignment] と [verticalAlignment] で表示位置の意図を指定し、
 * [offset] と [verticalSpacingPx] でアンカー基準位置からの微調整ができる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnchoredOverlayMenu(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect?,
    hazeState: HazeState?,
    horizontalAlignment: HorizontalAnchorAlignment = HorizontalAnchorAlignment.Center,
    verticalAlignment: VerticalAnchorAlignment = VerticalAnchorAlignment.Above,
    verticalSpacingPx: Int = -12,
    offset: DpOffset = DpOffset.Zero,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // --- Visibility state ---
    val visibleState = remember {
        MutableTransitionState(false)
    }
    visibleState.targetState = expanded

    // Guard: 非表示完了時、またはアンカー未確定時は描画しない。
    if ((!visibleState.currentState && !visibleState.targetState) || anchorBoundsInWindow == null) {
        return
    }

    // --- Position setup ---
    val density = LocalDensity.current
    val offsetPx = with(density) {
        IntOffset(
            x = offset.x.roundToPx(),
            y = offset.y.roundToPx(),
        )
    }
    val positionProvider = remember(
        anchorBoundsInWindow,
        horizontalAlignment,
        verticalAlignment,
        verticalSpacingPx,
        offsetPx
    ) {
        AnchoredOverlayMenuPositionProvider(
            anchorBoundsInWindow = anchorBoundsInWindow,
            horizontalAlignment = horizontalAlignment,
            verticalAlignment = verticalAlignment,
            verticalSpacingPx = verticalSpacingPx,
            offsetPx = offsetPx,
        )
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val menuShape = MaterialTheme.shapes.largeIncreased
        val menuColor = if (hazeState != null) {
            MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.80f)
        } else {
            MaterialTheme.colorScheme.surfaceBright
        }

        // --- Animated content ---
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 140)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 180),
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                    scaleOut(
                        animationSpec = tween(durationMillis = 140),
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ),
        ) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = menuShape,
                        clip = false,
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .clip(menuShape)
                        .let { baseModifier ->
                            if (hazeState != null) {
                                baseModifier.hazeEffect(state = hazeState)
                            } else {
                                baseModifier
                            }
                        },
                    shape = menuShape,
                    color = menuColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 3.dp,
                ) {
                    Column(content = content)
                }
            }
        }
    }
}

/**
 * アンカー上に重ねるオーバーレイメニューの位置を計算する。
 */
class AnchoredOverlayMenuPositionProvider(
    private val anchorBoundsInWindow: IntRect,
    private val horizontalAlignment: HorizontalAnchorAlignment,
    private val verticalAlignment: VerticalAnchorAlignment,
    private val verticalSpacingPx: Int,
    private val offsetPx: IntOffset,
) : PopupPositionProvider {

    /**
     * アンカーとウィンドウサイズからポップアップ表示座標を返す。
     *
     * - `Above` / `Below`: 正の spacing で gap、負の spacing で重なり。
     * - `OverlapTop` / `OverlapBottom`: アンカー端からのオフセット量として spacing を使う。
     */
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // --- Bounds ---
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)

        // --- Horizontal ---
        val alignedX = when (horizontalAlignment) {
            HorizontalAnchorAlignment.Start -> anchorBoundsInWindow.left
            HorizontalAnchorAlignment.Center -> {
                anchorBoundsInWindow.left +
                        ((anchorBoundsInWindow.width - popupContentSize.width) / 2)
            }

            HorizontalAnchorAlignment.End -> anchorBoundsInWindow.right - popupContentSize.width
        }
        val x = (alignedX + offsetPx.x).coerceIn(0, maxX)

        // --- Vertical ---
        val baseY = when (verticalAlignment) {
            VerticalAnchorAlignment.Above -> {
                anchorBoundsInWindow.top - popupContentSize.height - verticalSpacingPx
            }

            VerticalAnchorAlignment.Below -> {
                anchorBoundsInWindow.bottom + verticalSpacingPx
            }

            VerticalAnchorAlignment.Auto -> {
                val spaceAbove = anchorBoundsInWindow.top
                val spaceBelow = windowSize.height - anchorBoundsInWindow.bottom
                if (spaceAbove >= spaceBelow) {
                    anchorBoundsInWindow.top - popupContentSize.height - verticalSpacingPx
                } else {
                    anchorBoundsInWindow.bottom + verticalSpacingPx
                }
            }

            VerticalAnchorAlignment.OverlapTop -> {
                anchorBoundsInWindow.top + verticalSpacingPx
            }

            VerticalAnchorAlignment.OverlapBottom -> {
                anchorBoundsInWindow.bottom - popupContentSize.height + verticalSpacingPx
            }
        }
        val desiredY = baseY + offsetPx.y
        val y = desiredY.coerceIn(0, maxY)

        return IntOffset(x, y)
    }
}

/**
 * アンカーメニュー向けの単一行メニュー項目。
 *
 * [textColor] を指定しない場合は親 Surface の contentColor（既定では onSurface）を使う。
 * 破壊的操作などで色を変えたい場合は [textColor] に明示的に渡す。
 * [leadingIcon] を指定すると、テキストの先頭にアイコンを表示する。
 */
@Composable
fun AnchoredOverlayMenuItem(
    text: String,
    textColor: Color = Color.Unspecified,
    leadingIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val textScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "menuItemTextScale",
    )

    val color = if (textColor != Color.Unspecified) {
        textColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = textScale
                scaleY = textScale
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

@Composable
fun AnchoredOverlayMenuDriver() {
    val color = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(1.dp)
    ) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(1f, 12f),
                0f
            )
        )
    }
}

@Preview(showSystemUi = true, showBackground = false)
@Composable
private fun AnchoredOverlayMenuPreview() {
    SlevoTheme {
        AnchoredOverlayMenu(
            expanded = true,
            anchorBoundsInWindow = IntRect(320, 80, 368, 128),
            hazeState = null,
            onDismissRequest = {},
        ) {
            AnchoredOverlayMenuItem(text = "画像を保存", onClick = {})
            AnchoredOverlayMenuDriver()
            AnchoredOverlayMenuItem(text = "画像URLをコピー", onClick = {})
            AnchoredOverlayMenuItem(text = "ウェブで画像を検索", onClick = {})
        }
    }
}
