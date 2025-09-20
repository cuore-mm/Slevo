package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.parseThreadUrl

@Composable
fun TabScreenContent(
    modifier: Modifier = Modifier,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {}
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    val uiState by tabsViewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showUrlDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.open_url)
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier
                .fillMaxSize()
                .padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            TabsPagerContent(
                modifier = Modifier.padding(innerPadding),
                tabsViewModel = tabsViewModel,
                navController = navController,
                closeDrawer = closeDrawer,
                initialPage = initialPage,
                onPageChanged = onPageChanged
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
                        val route = AppRoute.Thread(
                            threadKey = key,
                            boardUrl = boardUrl,
                            boardName = board,
                            threadTitle = url
                        )
                        tabsViewModel.ensureThreadTab(route).let { index ->
                            if (index >= 0) {
                                tabsViewModel.setThreadCurrentPage(index)
                            }
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    } else {
                        parseBoardUrl(url)?.let { (host, board) ->
                            val boardUrl = "https://$host/$board/"
                            val route = AppRoute.Board(
                                boardName = boardUrl,
                                boardUrl = boardUrl
                            )
                            tabsViewModel.ensureBoardTab(route).let { index ->
                                if (index >= 0) {
                                    tabsViewModel.setBoardCurrentPage(index)
                                }
                            }
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                    showUrlDialog = false
                    closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                }
            )
        }
    }
}
