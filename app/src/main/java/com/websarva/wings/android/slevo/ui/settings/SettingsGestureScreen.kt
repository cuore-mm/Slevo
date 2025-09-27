package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.SmallTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGestureScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsGestureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.enable_gesture_feature)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isGestureEnabled,
                            onCheckedChange = { viewModel.toggleGesture(it) }
                        )
                    }
                )
                HorizontalDivider()
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
                    Modifier.clickable { viewModel.onGestureItemClick(item.direction) }
                } else {
                    Modifier
                }
                ListItem(
                    modifier = itemModifier,
                    headlineContent = { Text(directionLabel) },
                    trailingContent = { Text(actionLabel) }
                )
                HorizontalDivider()
            }
        }
    }

    uiState.selectedDirection?.let { direction ->
        val currentAction = uiState.gestureItems.firstOrNull { it.direction == direction }?.action
        val actions = enumValues<GestureAction>()
        AlertDialog(
            onDismissRequest = { viewModel.dismissGestureDialog() },
            title = { Text(text = stringResource(id = direction.labelRes)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GestureActionSelectionRow(
                        label = stringResource(id = R.string.gesture_action_unassigned),
                        selected = currentAction == null,
                        onClick = { viewModel.assignGestureAction(direction, null) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    actions.forEachIndexed { index, action ->
                        GestureActionSelectionRow(
                            label = stringResource(id = action.labelRes),
                            selected = currentAction == action,
                            onClick = { viewModel.assignGestureAction(direction, action) }
                        )
                        if (index != actions.lastIndex) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissGestureDialog() }) {
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
