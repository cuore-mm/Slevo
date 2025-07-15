package com.websarva.wings.android.bbsviewer.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl
import com.websarva.wings.android.bbsviewer.ui.util.parseThreadUrl

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TabScreenContent(
    modifier: Modifier = Modifier,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    val isLoading by tabsViewModel.isTabsLoading.collectAsState()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showUrlDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.open_url)
                )
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            TabsPagerContent(
                modifier = Modifier.padding(innerPadding),
                tabsViewModel = tabsViewModel,
                navController = navController,
                closeDrawer = closeDrawer
            )
        }

        if (showUrlDialog) {
            UrlOpenDialog(
                onDismissRequest = { showUrlDialog = false },
                onOpen = { url ->
                    val thread = parseThreadUrl(url)
                    if (thread != null) {
                        val (host, board, key) = thread
                        val boardUrl = "https://$host/$board/"
                        navController.navigate(
                            AppRoute.Thread(
                                threadKey = key,
                                boardUrl = boardUrl,
                                boardName = board,
                                boardId = 0L,
                                threadTitle = url
                            )
                        ) { launchSingleTop = true }
                    } else {
                        parseBoardUrl(url)?.let { (host, board) ->
                            val boardUrl = "https://$host/$board/"
                            navController.navigate(
                                AppRoute.Board(
                                    boardId = 0L,
                                    boardName = boardUrl,
                                    boardUrl = boardUrl
                                )
                            ) { launchSingleTop = true }
                        }
                    }
                    showUrlDialog = false
                    closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                }
            )
        }
    }
}
