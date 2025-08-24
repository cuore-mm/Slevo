package com.websarva.wings.android.bbsviewer.ui.more

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
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.topbar.HomeTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HomeTopAppBarScreen(
                title = stringResource(R.string.more),
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
                    modifier = Modifier.clickable(onClick = onBoardListClick),
                    headlineContent = { Text(text = stringResource(R.string.boardList)) }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onHistoryClick),
                    headlineContent = { Text(text = stringResource(R.string.history)) }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onSettingsClick),
                    headlineContent = { Text(text = stringResource(R.string.settings)) }
                )
                HorizontalDivider()
            }
        }
    }
}

