package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onGeneralClick: () -> Unit,
    onGestureClick: () -> Unit,
    onThreadClick: () -> Unit,
    onNgClick: () -> Unit,
    onCookieClick: () -> Unit,
    onNavigateUp: (() -> Unit),
) {
    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(id = R.string.settings),
                modifier = Modifier,
                onNavigateUp = onNavigateUp,
                scrollBehavior = null
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            val generalGroup = listOf(
                ListItemSpec(
                    headlineContent = { Text(stringResource(id = R.string.settings_general)) },
                    onClick = onGeneralClick,
                )
            )
            val threadGroup = listOf(
                ListItemSpec(
                    headlineContent = { Text(stringResource(id = R.string.thread_display)) },
                    onClick = onThreadClick,
                ),
                ListItemSpec(
                    headlineContent = { Text(stringResource(id = R.string.ng_label)) },
                    onClick = onNgClick,
                ),
                ListItemSpec(
                    headlineContent = { Text(stringResource(id = R.string.gesture_settings)) },
                    onClick = onGestureClick,
                ),
            )
            val otherGroup = listOf(
                ListItemSpec(
                    headlineContent = { Text(stringResource(id = R.string.cookie_management)) },
                    onClick = onCookieClick,
                )
            )

            item { SettingsCardWithListItems(items = generalGroup) }
            item { SettingsCardWithListItems(items = threadGroup) }
            item { SettingsCardWithListItems(items = otherGroup) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            onGeneralClick = {},
            onGestureClick = {},
            onThreadClick = {},
            onNgClick = {},
            onCookieClick = {},
            onNavigateUp = {}
        )
    }
}
