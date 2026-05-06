package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.ui.common.AnchoredSelectionMenu
import com.websarva.wings.android.slevo.ui.common.SelectionMenuOption
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import kotlin.math.roundToInt

/**
 * 全般設定画面のテーマ選択と 5ch ドメイン切り替え設定を表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    themeMode: ThemeMode,
    isRedirect5chNetToIoEnabled: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleRedirect5chNetToIoEnabled: (Boolean) -> Unit,
    onNavigateUp: () -> Unit,
) {
    // --- Menu state ---
    var isThemeMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
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
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            val rect = coordinates.boundsInWindow()
                            themeMenuAnchorBounds = IntRect(
                                left = rect.left.roundToInt(),
                                top = rect.top.roundToInt(),
                                right = rect.right.roundToInt(),
                                bottom = rect.bottom.roundToInt(),
                            )
                        }
                ) {
                    SettingsCardWithListItems(
                        items = listOf(
                            listItemSpecOfBasic(
                                headlineText = stringResource(R.string.theme_setting),
                                supportingText = themeMode.toDisplayLabel(),
                                supportingTextRole = SupportingTextRole.SelectedValue,
                                onClick = { isThemeMenuExpanded = true },
                            )
                        )
                    )
                    AnchoredSelectionMenu(
                        expanded = isThemeMenuExpanded,
                        anchorBoundsInWindow = themeMenuAnchorBounds,
                        options = themeOptions,
                        selectedValue = themeMode,
                        onSelect = onSelectThemeMode,
                        onDismissRequest = { isThemeMenuExpanded = false },
                    )
                }
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
            onSelectThemeMode = {},
            onToggleRedirect5chNetToIoEnabled = {},
            onNavigateUp = {},
        )
    }
}
