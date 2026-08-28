package com.websarva.wings.android.slevo.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.ui.common.SelectionMenuOption
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme

/**
 * 全般設定画面のテーマ選択と 5ch ドメイン切り替え設定を表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    themeMode: ThemeMode,
    isRedirect5chNetToIoEnabled: Boolean,
    isReplyNotificationEnabled: Boolean = false,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleRedirect5chNetToIoEnabled: (Boolean) -> Unit,
    onToggleReplyNotification: (Boolean) -> Unit = {},
    onReplyNotificationPermissionResult: (Boolean) -> Unit = onToggleReplyNotification,
    onNavigateUp: () -> Unit,
) {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onReplyNotificationPermissionResult(granted)
    }

    /** 返信通知を有効化し、必要なOS権限があれば先に要求する。 */
    fun toggleReplyNotification(enabled: Boolean) {
        if (!enabled) {
            onToggleReplyNotification(false)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onToggleReplyNotification(true)
        }
    }

    val themeOptions = listOf(
        SelectionMenuOption(ThemeMode.LIGHT, stringResource(R.string.theme_mode_light)),
        SelectionMenuOption(ThemeMode.DARK, stringResource(R.string.theme_mode_dark)),
        SelectionMenuOption(ThemeMode.SYSTEM, stringResource(R.string.theme_mode_system)),
    )

    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(R.string.settings_general),
                onNavigateUp = onNavigateUp,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            item {
                SettingsCardWithListItems(
                    items = listOf(
                        listItemSpecOfSelectionMenu(
                            headlineText = stringResource(R.string.theme_setting),
                            supportingText = themeMode.toDisplayLabel(),
                            selectedValue = themeMode,
                            options = themeOptions,
                            onSelect = onSelectThemeMode,
                        )
                    )
                )
            }
            item {
                SettingsCardWithListItems(
                    items = listOf(
                        listItemSpecOfBasic(
                            headlineText = stringResource(R.string.reply_notification_setting_title),
                            supportingText = stringResource(R.string.reply_notification_setting_description),
                            supportingTextRole = SupportingTextRole.Description,
                            switchSpec = SwitchSpec(
                                checked = isReplyNotificationEnabled,
                                onCheckedChange = ::toggleReplyNotification,
                            ),
                            onClick = {
                                toggleReplyNotification(!isReplyNotificationEnabled)
                            },
                        )
                    )
                )
            }
            item {
                SettingsCardWithListItems(
                    items = listOf(
                        listItemSpecOfBasic(
                            headlineText = stringResource(R.string.redirect_5ch_net_to_io_title),
                            supportingText = stringResource(R.string.redirect_5ch_net_to_io_description),
                            supportingTextRole = SupportingTextRole.Description,
                            switchSpec = SwitchSpec(
                                checked = isRedirect5chNetToIoEnabled,
                                onCheckedChange = onToggleRedirect5chNetToIoEnabled,
                            ),
                            onClick = {
                                onToggleRedirect5chNetToIoEnabled(!isRedirect5chNetToIoEnabled)
                            },
                        )
                    )
                )
            }
        }
    }
}

/**
 * [ThemeMode] を設定画面で表示する文字列へ変換する。
 */
@Composable
private fun ThemeMode.toDisplayLabel(): String {
    return when (this) {
        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsGeneralScreenPreview() {
    SlevoTheme {
        SettingsGeneralScreen(
            themeMode = ThemeMode.SYSTEM,
            isRedirect5chNetToIoEnabled = true,
            isReplyNotificationEnabled = false,
            onSelectThemeMode = {},
            onToggleRedirect5chNetToIoEnabled = {},
            onToggleReplyNotification = {},
            onNavigateUp = {},
        )
    }
}
