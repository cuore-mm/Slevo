package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.websarva.wings.android.slevo.ui.topbar.SmallTopAppBarScreen

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
            SmallTopAppBarScreen(
                title = stringResource(id = R.string.gesture_settings),
                onNavigateUp = onNavigateUp,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        toggleGesture(!uiState.isGestureEnabled)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                ) {
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                Text(
                    text = stringResource(id = R.string.gesture_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(uiState.gestureItems) { item ->
                val directionLabel = stringResource(id = item.direction.labelRes)
                val actionLabel = item.action?.let { stringResource(id = it.labelRes) }
                    ?: stringResource(id = R.string.gesture_action_unassigned)
                val itemModifier = if (uiState.isGestureEnabled) {
                    Modifier.clickable { onGestureItemClick(item.direction) }
                } else {
                    Modifier
                }
                ListItem(
                    modifier = itemModifier,
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = item.direction.iconRes),
                            contentDescription = directionLabel,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    headlineContent = { Text(directionLabel) },
                    trailingContent = { Text(actionLabel) }
                )
                HorizontalDivider()
            }
        }
    }

    uiState.selectedDirection?.let { direction ->
        val currentAction = uiState.gestureItems.firstOrNull { it.direction == direction }?.action
        val actions = GestureAction.entries.toList()
        AlertDialog(
            onDismissRequest = { dismissGestureDialog() },
            title = { Text(text = stringResource(id = direction.labelRes)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    item {
                        GestureActionSelectionRow(
                            label = stringResource(id = R.string.gesture_action_unassigned),
                            selected = currentAction == null,
                            onClick = { assignGestureAction(direction, null) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    itemsIndexed(actions) { index, action ->
                        GestureActionSelectionRow(
                            label = stringResource(id = action.labelRes),
                            selected = currentAction == action,
                            onClick = { assignGestureAction(direction, action) }
                        )
                        if (index != actions.lastIndex) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dismissGestureDialog() }) {
                    Text(text = stringResource(id = R.string.close))
                }
            }
        )
    }
}

@Composable
private fun GestureActionSelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label)
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
        selectedDirection = GestureDirection.entries.first(),
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
