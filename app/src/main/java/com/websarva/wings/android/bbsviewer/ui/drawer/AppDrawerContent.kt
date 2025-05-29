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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute

@Composable
fun AppDrawerContent(
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit
) {
    val openTabs by tabsViewModel.openTabs.collectAsState()

    ModalDrawerSheet { // ModalDrawerSheet で囲む
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "開いているスレッド",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(openTabs, key = { it.key + it.boardUrl }) { tab ->
                    NavigationDrawerItem(
                        label = { Text(tab.title, maxLines = 1) },
                        badge = {
                            IconButton(onClick = { tabsViewModel.closeThread(tab) }) {
                                Icon(Icons.Default.Close, contentDescription = "閉じる")
                            }
                        },
                        selected = false, // TODO: 必要に応じて、現在表示中のスレッドを判定して selected を true にする
                        onClick = {
                            closeDrawer()
                            navController.navigate(
                                AppRoute.Thread(
                                    threadKey = tab.key,
                                    boardUrl = tab.boardUrl,
                                    boardName = tab.boardName,
                                    boardId = tab.boardId,
                                    threadTitle = tab.title
                                )
                            ) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}
