package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
    onBackupClick: () -> Unit,
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
            item {
                val generalGroup = listOf(
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.settings_general),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = stringResource(id = R.string.settings_general),
                            )
                        },
                        onClick = onGeneralClick,
                    )
                )
                SettingsCardWithListItems(items = generalGroup)
            }

            item {
                val threadGroup = listOf(
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.settings_thread),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ViewList,
                                contentDescription = stringResource(id = R.string.settings_thread),
                            )
                        },
                        onClick = onThreadClick,
                    ),
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.ng_label),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Block,
                                contentDescription = stringResource(id = R.string.ng_label),
                            )
                        },
                        onClick = onNgClick,
                    ),
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.gesture_settings),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Gesture,
                                contentDescription = stringResource(id = R.string.gesture_settings),
                            )
                        },
                        onClick = onGestureClick,
                    ),
                )
                SettingsCardWithListItems(items = threadGroup)
            }

            item {
                val otherGroup = listOf(
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.cookie_management),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Cookie,
                                contentDescription = stringResource(id = R.string.cookie_management),
                            )
                        },
                        onClick = onCookieClick,
                    ),
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.backup_title),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = stringResource(id = R.string.backup_title),
                            )
                        },
                        onClick = onBackupClick,
                    ),
                )
                SettingsCardWithListItems(items = otherGroup)
            }
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
            onBackupClick = {},
            onNavigateUp = {}
        )
    }
}
