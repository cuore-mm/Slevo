package com.websarva.wings.android.bbsviewer.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute

@Composable
fun OpenThreadsList(
    modifier: Modifier = Modifier,
    openTabs: List<ThreadTabInfo>,
    onCloseClick: (ThreadTabInfo) -> Unit = {},
    navController: NavHostController,
    closeDrawer: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(openTabs, key = { it.key + it.boardUrl }) { tab ->
                ListItem(
                    headlineContent = {
                        Column {
                            Text(tab.title)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tab.boardName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = tab.resCount.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onCloseClick(tab) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        closeDrawer()
                        navController.navigate(
                            AppRoute.Thread(
                                threadKey = tab.key,
                                boardUrl = tab.boardUrl,
                                boardName = tab.boardName,
                                boardId = tab.boardId,
                                threadTitle = tab.title,
                                resCount = tab.resCount
                            )
                        ) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OpenThreadsListPreview() {
    val sampleTabs = listOf(
        ThreadTabInfo("1", "スレッド1", "板1", "https://example.com/board1", 1, 100),
        ThreadTabInfo("2", "スレッド2", "板2", "https://example.com/board2", 2, 200),
        ThreadTabInfo("3", "スレッド3", "板3", "https://example.com/board3", 3, 300)
    )
    OpenThreadsList(
        openTabs = sampleTabs,
        onCloseClick = {},
        navController = rememberNavController(),
        closeDrawer = {}
    )
}
