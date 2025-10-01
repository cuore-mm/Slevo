package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.topbar.HomeTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onGeneralClick: () -> Unit,
    onGestureClick: () -> Unit,
    onThreadClick: () -> Unit,
    onNgClick: () -> Unit,
    onCookieClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HomeTopAppBarScreen(
                title = "設定",
                modifier = Modifier,
                scrollBehavior = null
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
                    modifier = Modifier.clickable(onClick = onGeneralClick),
                    headlineContent = { Text("全般") }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onGestureClick),
                    headlineContent = { Text(stringResource(id = R.string.gesture_settings)) }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onThreadClick),
                    headlineContent = { Text("スレッド") }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onNgClick),
                    headlineContent = { Text("NG") }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onCookieClick),
                    headlineContent = { Text("Cookie管理") }
                )
                HorizontalDivider()
            }
        }
    }
}

