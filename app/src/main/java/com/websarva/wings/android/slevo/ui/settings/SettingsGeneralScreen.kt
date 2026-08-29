package com.websarva.wings.android.slevo.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.ui.common.SelectionMenuOption
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme

/**
 * 全般設定画面のテーマ、5chドメイン切り替え、返信通知設定を表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    themeMode: ThemeMode,
    isRedirect5chNetToIoEnabled: Boolean,
    isReplyNotificationEnabled: Boolean = false,
    isNotificationAllowed: Boolean = true,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleRedirect5chNetToIoEnabled: (Boolean) -> Unit,
    onToggleReplyNotification: (Boolean) -> Unit = {},
    onReplyNotificationPermissionResult: (Boolean) -> Unit = onToggleReplyNotification,
    onRefreshNotificationPermission: () -> Unit = {},
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current

    // --- Lifecycle ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        onRefreshNotificationPermission()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- Permission request ---
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
            // アプリ設定を先に保存し、権限拒否後もユーザーの有効化意図を維持する。
            onToggleReplyNotification(true)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onToggleReplyNotification(true)
        }
    }

    // --- Display data ---
    val themeOptions = listOf(
        SelectionMenuOption(ThemeMode.LIGHT, stringResource(R.string.theme_mode_light)),
        SelectionMenuOption(ThemeMode.DARK, stringResource(R.string.theme_mode_dark)),
        SelectionMenuOption(ThemeMode.SYSTEM, stringResource(R.string.theme_mode_system)),
    )
    val showNotificationPermissionWarning = isReplyNotificationEnabled && !isNotificationAllowed
    val replyNotificationDescription = stringResource(
        if (showNotificationPermissionWarning) {
            R.string.reply_notification_permission_warning
        } else {
            R.string.reply_notification_setting_description
        },
    )
    val replyNotificationDescriptionStyle = if (showNotificationPermissionWarning) {
        MaterialTheme.typography.labelLarge.copy(
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Normal,
        )
    } else {
        null
    }

    // --- Screen ---
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
                            supportingText = replyNotificationDescription,
                            supportingTextRole = SupportingTextRole.Description,
                            customSupportingStyle = replyNotificationDescriptionStyle,
                            switchSpec = SwitchSpec(
                                checked = isReplyNotificationEnabled,
                                onCheckedChange = ::toggleReplyNotification,
                            ),
                            onClick = {
                                if (showNotificationPermissionWarning) {
                                    openNotificationSettings(context)
                                } else {
                                    toggleReplyNotification(!isReplyNotificationEnabled)
                                }
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
            isNotificationAllowed = true,
            onSelectThemeMode = {},
            onToggleRedirect5chNetToIoEnabled = {},
            onToggleReplyNotification = {},
            onNavigateUp = {},
        )
    }
}

/** 現在のAPIレベルに応じたアプリ通知設定画面を開くIntentを作成する。 */
internal fun notificationSettingsIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        applicationDetailsSettingsIntent(context)
    }
}

/** アプリ詳細設定画面を開くIntentを作成する。 */
private fun applicationDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${context.packageName}".toUri()
    }

/** 通知設定画面を開き、端末が専用画面を提供しない場合はアプリ詳細へフォールバックする。 */
private fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(notificationSettingsIntent(context))
    } catch (_: ActivityNotFoundException) {
        // 一部端末ではアプリ通知専用画面がないため、汎用のアプリ詳細設定を開く。
        context.startActivity(applicationDetailsSettingsIntent(context))
    }
}
