package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenu
import com.websarva.wings.android.slevo.ui.common.AnchoredOverlayMenuItem
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dev.chrisbanes.haze.HazeState

/**
 * タブ専用のアンカードアクションメニュー。
 *
 * `AnchoredOverlayMenu` を再利用し、長押ししたタブ位置に紐づく
 * 「詳細」「固定切替」「閉じる」を表示する。
 * 「タブを閉じる」は破壊的操作として赤字で表示する。
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
    AnchoredOverlayMenu(
        expanded = expanded,
        anchorBoundsInWindow = anchorBoundsInWindow,
        hazeState = hazeState,
        onDismissRequest = onDismissRequest,
    ) {
        AnchoredOverlayMenuItem(
            text = stringResource(R.string.tab_action_detail),
            onClick = onDetailClick,
        )
        AnchoredOverlayMenuItem(
            text = stringResource(
                if (isPinned) R.string.tab_action_unpin else R.string.tab_action_pin
            ),
            onClick = onPinClick,
        )
        AnchoredOverlayMenuItem(
            text = stringResource(R.string.tab_action_close),
            textColor = MaterialTheme.colorScheme.error,
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
