package com.websarva.wings.android.bbsviewer.ui.drawer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.navigation.openThread

@Composable
fun AppDrawerContent(
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit
) {
    val openTabs by tabsViewModel.openTabs.collectAsState()

    ModalDrawerSheet { // ModalDrawerSheet で囲む
        OpenThreadsList(
            openTabs = openTabs,
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = closeDrawer
        )
    }
}

@Composable
fun OpenThreadsList(
    openTabs: List<TabInfo>,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.drawer_open_threads),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(openTabs, key = { it.key + it.boardUrl }) { tab ->
                NavigationDrawerItem(
                    label = { Text(tab.title, maxLines = 1) },
                    badge = {
                        IconButton(
                            onClick = {
                                tabsViewModel.closeThread(tab)
                                navController.popBackStack(
                                    AppRoute.Thread(
                                        threadKey = tab.key,
                                        boardUrl = tab.boardUrl,
                                        boardName = tab.boardName,
                                        boardId = tab.boardId,
                                        threadTitle = tab.title
                                    ),
                                    inclusive = true
                                )
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    selected = false, // TODO: 必要に応じて、現在表示中のスレッドを判定して selected を true にする
                    onClick = {
                        closeDrawer()
                        navController.openThread(
                            AppRoute.Thread(
                                threadKey = tab.key,
                                boardUrl = tab.boardUrl,
                                boardName = tab.boardName,
                                boardId = tab.boardId,
                                threadTitle = tab.title
                            )
                        )
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OpenThreadsListPreview() {
    val sampleTabs = listOf(
        TabInfo("1", "スレッド1", "板1", "https://example.com/board1", 1),
        TabInfo("2", "スレッド2", "板2", "https://example.com/board2", 2),
        TabInfo("3", "スレッド3", "板3", "https://example.com/board3", 3)
    )
    OpenThreadsList(
        openTabs = sampleTabs,
        tabsViewModel = TabsViewModel(),
        navController = rememberNavController(),
        closeDrawer = {}
    )
}
