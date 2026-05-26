package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    AnchoredOverlayMenu(
        expanded = expanded,
        anchorBoundsInWindow = anchorBoundsInWindow,
        hazeState = hazeState,
        horizontalAlignment = HorizontalAnchorAlignment.Start,
        verticalAlignment = VerticalAnchorAlignment.Auto,
        verticalSpacingPx = 8,
        onDismissRequest = onDismissRequest,
    ) {
        AnchoredOverlayMenuItem(
            text = stringResource(R.string.tab_action_detail),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = onSurfaceColor,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onDetailClick,
        )
        AnchoredOverlayMenuItem(
            text = stringResource(
                if (isPinned) R.string.tab_action_unpin else R.string.tab_action_pin
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = onSurfaceColor,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onPinClick,
        )
        AnchoredOverlayMenuItem(
            text = stringResource(R.string.tab_action_close),
            textColor = errorColor,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = errorColor,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onCloseClick,
        )
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
