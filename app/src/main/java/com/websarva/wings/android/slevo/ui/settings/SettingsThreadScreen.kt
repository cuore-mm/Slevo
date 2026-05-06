package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.AnchoredSelectionMenu
import com.websarva.wings.android.slevo.ui.common.SelectionMenuOption
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import kotlin.math.roundToInt

/**
 * スレッド表示設定画面と [SettingsThreadViewModel] を接続する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThreadScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsThreadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsThreadScreenContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onSelectSort = { viewModel.updateSort(it) },
        onToggleMinimapScrollbar = { viewModel.updateMinimapScrollbar(it) },
    )
}

/**
 * スレッド設定画面のカードUIと選択メニューを描画する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThreadScreenContent(
    uiState: SettingsThreadUiState,
    onNavigateUp: () -> Unit,
    onSelectSort: (Boolean) -> Unit,
    onToggleMinimapScrollbar: (Boolean) -> Unit,
) {
    // --- Menu state ---
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuAnchorBounds by remember { mutableStateOf<IntRect?>(null) }

    val sortOptions = listOf(
        SelectionMenuOption(false, stringResource(R.string.number_order)),
        SelectionMenuOption(true, stringResource(R.string.tree_order)),
    )

    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(R.string.thread),
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
                            sortMenuAnchorBounds = IntRect(
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
                                headlineText = stringResource(R.string.default_thread_sort_order),
                                supportingText = if (uiState.isTreeSort) {
                                    stringResource(R.string.tree_order)
                                } else {
                                    stringResource(R.string.number_order)
                                },
                                onClick = { isSortMenuExpanded = true },
                            ),
                            listItemSpecOfBasic(
                                headlineText = stringResource(R.string.show_minimap_scrollbar),
                                supportingText = stringResource(R.string.show_minimap_scrollbar_description),
                                switchSpec = SwitchSpec(
                                    checked = uiState.showMinimapScrollbar,
                                    onCheckedChange = onToggleMinimapScrollbar,
                                ),
                                onClick = {
                                    onToggleMinimapScrollbar(!uiState.showMinimapScrollbar)
                                }
                            )
                        )
                    )
                    AnchoredSelectionMenu(
                        expanded = isSortMenuExpanded,
                        anchorBoundsInWindow = sortMenuAnchorBounds,
                        options = sortOptions,
                        selectedValue = uiState.isTreeSort,
                        onSelect = onSelectSort,
                        onDismissRequest = { isSortMenuExpanded = false },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsThreadScreenContentPreview() {
    SlevoTheme {
        SettingsThreadScreenContent(
            uiState = SettingsThreadUiState(),
            onNavigateUp = {},
            onSelectSort = {},
            onToggleMinimapScrollbar = {},
        )
    }
}
