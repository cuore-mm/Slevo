package com.websarva.wings.android.slevo.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreNotificationType
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme

/**
 * Root overlayとしてSnackbarを描画する。
 *
 * 呼び出し側が渡すbottom chromeの高さとsafe drawingだけを自身の配置へ適用し、RootNavHostの
 * content paddingや測定boundsには伝播させない。
 */
@Composable
internal fun RootSnackbarHost(
    snackbarHostState: SnackbarHostState,
    bottomChromeHeight: Dp,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(bottom = bottomChromeHeight),
    )
}

/**
 * root-level Snackbarへpending restore結果を一度だけ表示する。
 *
 * Snackbarの表示完了後にだけ[onDisplayed]を呼ぶため、表示前のprocess終了ではresult fileが残り、
 * 次回起動時に再通知できる。
 */
@Composable
internal fun PendingRestoreResultSnackbar(
    notification: PendingRestoreNotificationUiModel?,
    snackbarHostState: SnackbarHostState,
    onDisplayed: (String) -> Unit,
) {
    val successMessage = stringResource(R.string.restore_result_snackbar_success)
    val failureMessage = stringResource(R.string.restore_result_snackbar_failure)
    val currentOnDisplayed by rememberUpdatedState(onDisplayed)

    if (notification == null) return

    LaunchedEffect(notification.token, snackbarHostState, successMessage, failureMessage) {
        val message = when (notification.type) {
            PendingRestoreNotificationType.SUCCESS -> successMessage
            PendingRestoreNotificationType.FAILURE -> failureMessage
        }
        val duration = when (notification.type) {
            PendingRestoreNotificationType.SUCCESS -> SnackbarDuration.Short
            PendingRestoreNotificationType.FAILURE -> SnackbarDuration.Long
        }
        snackbarHostState.showSnackbar(message = message, duration = duration)
        currentOnDisplayed(notification.token)
    }
}

@Preview
@Composable
private fun PendingRestoreResultSnackbarPreview() {
    SlevoTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) {
            PendingRestoreResultSnackbar(
                notification = PendingRestoreNotificationUiModel(
                    token = "preview",
                    type = PendingRestoreNotificationType.SUCCESS,
                ),
                snackbarHostState = snackbarHostState,
                onDisplayed = {},
            )
        }
    }
}
