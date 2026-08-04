package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenu
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenuItem
import com.websarva.wings.android.slevo.ui.common.HorizontalAnchorAlignment
import com.websarva.wings.android.slevo.ui.common.VerticalAnchorAlignment
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dev.chrisbanes.haze.HazeState

/**
 * タブ専用のアンカードアクションメニュー。
 *
 * `AnchoredOverlayMenu` を再利用し、長押ししたタブ位置に紐づく
 * 「詳細」「固定切替」「閉じる」を表示する。
 * 「タブを閉じる」は破壊的操作として赤字で表示する。
 * メニューはタブ左端揃え・上下自動配置でタブと重ならないように表示する。
 */
@Composable
fun AnchoredTabActionMenu(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect?,
    hazeState: HazeState?,
    isPinned: Boolean,
    onDismissRequest: () -> Unit,
    onDetailClick: () -> Unit,
    onPinClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    val iconSize = 20.dp

    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    AnchoredOverlayMenu(
        expanded = expanded,
        anchorBoundsInWindow = anchorBoundsInWindow,
        hazeState = hazeState,
        horizontalAlignment = HorizontalAnchorAlignment.Start,
        verticalAlignment = VerticalAnchorAlignment.Auto,
        verticalSpacing = 8.dp,
        onDismissRequest = onDismissRequest,
    ) {
        AnchoredOverlayMenuItem(
            text = stringResource(R.string.tab_action_detail),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = onSurfaceColor,
                    modifier = Modifier.size(iconSize),
                )
            },
            onClick = onDetailClick,
        )
        AnchoredOverlayMenuItem(
            text = stringResource(
                if (isPinned) R.string.tab_action_unpin else R.string.tab_action_pin
            ),
            leadingIcon = {
                if (isPinned) {
                    PushPinOffIcon(
                        tint = onSurfaceColor,
                        modifier = Modifier.size(iconSize),
                        backgroundColor = MaterialTheme.colorScheme.surfaceBright,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = onSurfaceColor,
                        modifier = Modifier.size(iconSize),
                    )
                }
            },
            onClick = onPinClick,
        )
        AnchoredOverlayMenuItem(
            text = stringResource(R.string.tab_action_close),
            textColor = errorColor,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = errorColor,
                    modifier = Modifier.size(iconSize),
                )
            },
            onClick = onCloseClick,
        )
    }
}

/**
 * タブ一覧全体の操作を表示するアンカードメニュー。
 *
 * 破壊的操作である「全てのタブを閉じる」だけを表示し、通常の単一タブ用メニューとは
 * 項目を共有しない。表示位置と dismiss 動作は [AnchoredOverlayMenu] に委譲する。
 */
@Composable
fun AnchoredTabActionMenu(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect?,
    hazeState: HazeState?,
    onDismissRequest: () -> Unit,
    onCloseAllClick: () -> Unit,
) {
    AnchoredOverlayMenu(
        expanded = expanded,
        anchorBoundsInWindow = anchorBoundsInWindow,
        hazeState = hazeState,
        horizontalAlignment = HorizontalAnchorAlignment.Start,
        verticalAlignment = VerticalAnchorAlignment.Auto,
        verticalSpacing = 8.dp,
        onDismissRequest = onDismissRequest,
    ) {
        AnchoredOverlayMenuItem(
            text = stringResource(R.string.tab_action_close_all),
            textColor = MaterialTheme.colorScheme.error,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onCloseAllClick,
        )
    }
}

@Composable
private fun PushPinOffIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
) {
    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.PushPin,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val slashStrokeWidth = 1.dp.toPx()
            val cutoutStrokeWidth = 3.dp.toPx()

            val start = Offset(size.width * 0.18f, size.height * 0.18f)
            val end = Offset(size.width * 0.82f, size.height * 0.82f)

            // --- Cutout ---
            drawLine(
                color = backgroundColor,
                start = start,
                end = end,
                strokeWidth = cutoutStrokeWidth,
                cap = StrokeCap.Round,
            )

            // --- Slash ---
            drawLine(
                color = tint,
                start = start,
                end = end,
                strokeWidth = slashStrokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Preview(showSystemUi = true, showBackground = false)
@Composable
private fun AnchoredTabActionMenuPreview() {
    SlevoTheme {
        AnchoredTabActionMenu(
            expanded = true,
            anchorBoundsInWindow = IntRect(320, 80, 368, 128),
            hazeState = null,
            isPinned = false,
            onDismissRequest = {},
            onDetailClick = {},
            onPinClick = {},
            onCloseClick = {},
        )
    }
}

@Preview(showSystemUi = true, showBackground = false)
@Composable
private fun AnchoredTabActionMenuPinnedPreview() {
    SlevoTheme {
        AnchoredTabActionMenu(
            expanded = true,
            anchorBoundsInWindow = IntRect(320, 80, 368, 128),
            hazeState = null,
            isPinned = true,
            onDismissRequest = {},
            onDetailClick = {},
            onPinClick = {},
            onCloseClick = {},
        )
    }
}

@Preview(showSystemUi = true, showBackground = false)
@Composable
private fun AnchoredTabActionMenuBulkPreview() {
    SlevoTheme {
        AnchoredTabActionMenu(
            expanded = true,
            anchorBoundsInWindow = IntRect(320, 80, 368, 128),
            hazeState = null,
            onDismissRequest = {},
            onCloseAllClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PushPinOffIconPreview() {
    SlevoTheme {
        PushPinOffIcon(
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(40.dp),
        )
    }
}
