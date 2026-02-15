package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
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
 * メニューはアンカー上に重ねて表示し、画面外へはみ出す場合は画面内へ補正する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnchoredOverlayMenu(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect?,
    hazeState: HazeState?,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded || anchorBoundsInWindow == null) {
        // Guard: メニュー非表示時やアンカー未確定時は描画しない。
        return
    }

    val positionProvider = remember(anchorBoundsInWindow) {
        AnchoredOverlayMenuPositionProvider(anchorBoundsInWindow)
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
                color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.80f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
            ) {
                Column(content = content)
            }
        }
    }
}

/**
 * アンカー上に重ねるオーバーレイメニューの位置を計算する。
 */
private class AnchoredOverlayMenuPositionProvider(
    private val anchorBoundsInWindow: IntRect,
) : PopupPositionProvider {
    private val overlapPx = 12

    /**
     * アンカーとウィンドウサイズからポップアップ表示座標を返す。
     */
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)

        val centeredX = anchorBoundsInWindow.left +
                ((anchorBoundsInWindow.width - popupContentSize.width) / 2)
        val x = centeredX.coerceIn(0, maxX)

        // ボタンの上端付近にメニューを重ねる。画面外はクランプする。
        val desiredY = anchorBoundsInWindow.top - overlapPx
        val y = desiredY.coerceIn(0, maxY)

        return IntOffset(x, y)
    }
}

/**
 * アンカーメニュー向けの単一行メニュー項目。
 */
@Composable
fun AnchoredOverlayMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val textScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "menuItemTextScale",
    )

    Text(
        text = text,
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
        style = MaterialTheme.typography.bodyLarge,
    )

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
