package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGestureScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsGestureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsGestureScreenContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        toggleGesture = { viewModel.toggleGesture(it) },
        toggleShowActionHints = { viewModel.toggleGestureShowActionHints(it) },
        onGestureItemClick = { viewModel.onGestureItemClick(it) },
        dismissGestureDialog = { viewModel.dismissGestureDialog() },
        assignGestureAction = { direction, action ->
            viewModel.assignGestureAction(
                direction,
                action
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsGestureScreenContent(
    uiState: SettingsGestureUiState,
    onNavigateUp: () -> Unit,
    toggleGesture: (Boolean) -> Unit,
    toggleShowActionHints: (Boolean) -> Unit,
    onGestureItemClick: (GestureDirection) -> Unit,
    dismissGestureDialog: () -> Unit,
    assignGestureAction: (GestureDirection, GestureAction?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(id = R.string.gesture_settings),
                onNavigateUp = onNavigateUp,
            )
        }
    ) { innerPadding ->
        val rightDirections = setOf(
            GestureDirection.Right,
            GestureDirection.RightUp,
            GestureDirection.RightLeft,
            GestureDirection.RightDown,
        )
        val leftDirections = setOf(
            GestureDirection.Left,
            GestureDirection.LeftUp,
            GestureDirection.LeftRight,
            GestureDirection.LeftDown,
        )

        val rightGestureItems = uiState.gestureItems.filter { it.direction in rightDirections }
        val leftGestureItems = uiState.gestureItems.filter { it.direction in leftDirections }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                SettingsCardWithListItems(
                    items = listOf(
                        listItemSpecOfBasic(
                            headlineText =
                                if (uiState.isGestureEnabled) stringResource(id = R.string.switch_on)
                                else stringResource(id = R.string.switch_off),
                            headlineStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isGestureEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            ),
                            switchSpec = SwitchSpec(
                                checked = uiState.isGestureEnabled,
                                onCheckedChange = { toggleGesture(it) },
                                enabled = true, // ジェスチャー設定全体の有効/無効に関わらず、常に切り替え可能にしておく
                            ),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggleGesture(!uiState.isGestureEnabled)
                            },
                        )
                    ),
                    // このカード自体は常にクリック可能にしておく（ジェスチャーの有効化/無効化用）
                    cardEnabled = true,
                )
            }
            item {
                Text(
                    text = stringResource(id = R.string.gesture_supporting_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            if (rightGestureItems.isNotEmpty()) {
                item {
                    GestureDirectionGroupCard(
                        gestureItems = rightGestureItems,
                        isGestureEnabled = uiState.isGestureEnabled,
                        onGestureItemClick = onGestureItemClick,
                    )
                }
            }
            if (leftGestureItems.isNotEmpty()) {
                item {
                    GestureDirectionGroupCard(
                        gestureItems = leftGestureItems,
                        isGestureEnabled = uiState.isGestureEnabled,
                        onGestureItemClick = onGestureItemClick,
                    )
                }
            }
            item {
                SettingsCardWithListItems(
                    items = listOf(
                        listItemSpecOfBasic(
                            headlineText = stringResource(id = R.string.gesture_show_action_hint),
                            switchSpec = SwitchSpec(
                                checked = uiState.showActionHints,
                                onCheckedChange = { toggleShowActionHints(it) },
                                enabled = uiState.isGestureEnabled,
                            ),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggleShowActionHints(!uiState.showActionHints)
                            }
                        )
                    ),
                    cardEnabled = uiState.isGestureEnabled,
                )
            }
        }
    }

    uiState.selectedDirection?.let { direction ->
        val currentAction = uiState.gestureItems.firstOrNull { it.direction == direction }?.action
        val actions = GestureAction.entries.toList()
        GestureActionDialog(
            direction = direction,
            currentAction = currentAction,
            actions = actions,
            onDismissRequest = { dismissGestureDialog() },
            onActionSelected = { action -> assignGestureAction(direction, action) }
        )
    }
}

@Composable
private fun GestureDirectionGroupCard(
    gestureItems: List<GestureItem>,
    isGestureEnabled: Boolean,
    onGestureItemClick: (GestureDirection) -> Unit,
) {
    val specs = gestureItems.map { item ->
        val directionLabel = stringResource(id = item.direction.labelRes)
        val actionLabel = item.action?.let { stringResource(id = it.labelRes) }
            ?: stringResource(id = R.string.gesture_action_unassigned)

        listItemSpecOfBasic(
            leadingContent = {
                Icon(
                    painter = painterResource(id = item.direction.iconRes),
                    contentDescription = directionLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            headlineText = directionLabel,
            supportingText = actionLabel,
            supportingStyle = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.primary
            ),
            onClick = if (isGestureEnabled) {
                { onGestureItemClick(item.direction) }
            } else null,
        )
    }

    SettingsCardWithListItems(items = specs, cardEnabled = isGestureEnabled)
}

@Preview(showBackground = true)
@Composable
private fun SettingsGestureScreenPreview() {
    val sampleState = SettingsGestureUiState(
        isGestureEnabled = true,
        gestureItems = GestureDirection.entries.map { direction ->
            GestureItem(direction = direction, action = GestureAction.entries.firstOrNull())
        },
//        selectedDirection = GestureDirection.entries.first(),
    )

    MaterialTheme {
        SettingsGestureScreenContent(
            uiState = sampleState,
            onNavigateUp = {},
            toggleGesture = {},
            toggleShowActionHints = {},
            onGestureItemClick = {},
            dismissGestureDialog = {},
            assignGestureAction = { _, _ -> }
        )
    }
}
