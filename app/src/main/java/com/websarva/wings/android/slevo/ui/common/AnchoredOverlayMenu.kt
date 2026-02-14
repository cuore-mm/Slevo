package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * アンカー座標を基準に表示するオーバーレイメニュー。
 *
 * メニューはアンカー上に重ねて表示し、画面外へはみ出す場合は画面内へ補正する。
 */
@Composable
fun AnchoredOverlayMenu(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect?,
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
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
        ) {
            Column(content = content)
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
