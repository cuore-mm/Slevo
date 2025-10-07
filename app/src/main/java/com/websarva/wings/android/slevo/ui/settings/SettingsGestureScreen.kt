package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
                SettingsCard(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    toggleGesture(!uiState.isGestureEnabled)
                }) {
                    ListItem(
                        modifier = Modifier
                            .padding(horizontal = 8.dp),
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            val stateText =
                                if (uiState.isGestureEnabled) stringResource(id = R.string.switch_on)
                                else stringResource(id = R.string.switch_off)
                            Text(
                                text = stateText,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isGestureEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            // Switch は個別にもタップ可能
                            Switch(
                                modifier = Modifier
                                    .scale(0.8f),
                                checked = uiState.isGestureEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toggleGesture(it)
                                }
                            )
                        }
                    )
                }
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
    SettingsCard(onClick = null, enabled = isGestureEnabled) {
        Column {
            gestureItems.forEachIndexed { index, item ->
                val directionLabel = stringResource(id = item.direction.labelRes)
                val actionLabel = item.action?.let { stringResource(id = it.labelRes) }
                    ?: stringResource(id = R.string.gesture_action_unassigned)
                val itemModifier = if (isGestureEnabled) {
                    Modifier
                        .fillMaxWidth()
                        .clickable { onGestureItemClick(item.direction) }
                } else {
                    Modifier.fillMaxWidth()
                }
                ListItem(
                    modifier = itemModifier,
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = item.direction.iconRes),
                            contentDescription = directionLabel,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    headlineContent = {
                        Text(
                            text = directionLabel,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = actionLabel,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                if (index != gestureItems.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp, end = 16.dp))
                }
            }
        }
    }
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
            onGestureItemClick = {},
            dismissGestureDialog = {},
            assignGestureAction = { _, _ -> }
        )
    }
}
