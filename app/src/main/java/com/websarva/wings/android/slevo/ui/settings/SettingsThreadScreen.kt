package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.SmallTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsThreadScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsThreadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SmallTopAppBarScreen(
                title = stringResource(R.string.thread),
                onNavigateUp = onNavigateUp,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Text(
                text = stringResource(R.string.default_thread_sort_order),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            ListItem(
                modifier = Modifier.clickable { viewModel.updateSort(false) },
                headlineContent = { Text(stringResource(R.string.number_order)) },
                trailingContent = {
                    RadioButton(
                        selected = !uiState.isTreeSort,
                        onClick = { viewModel.updateSort(false) }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable { viewModel.updateSort(true) },
                headlineContent = { Text(stringResource(R.string.tree_order)) },
                trailingContent = {
                    RadioButton(
                        selected = uiState.isTreeSort,
                        onClick = { viewModel.updateSort(true) }
                    )
                }
            )
            HorizontalDivider()
        }
    }
}
