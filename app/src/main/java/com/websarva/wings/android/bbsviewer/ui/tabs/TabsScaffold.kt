package com.websarva.wings.android.bbsviewer.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl
import com.websarva.wings.android.bbsviewer.ui.util.parseThreadUrl

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TabsScaffold(
    parentPadding: PaddingValues,
    tabsViewModel: TabsViewModel,
    navController: NavHostController
) {
    var showUrlDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(parentPadding),
        floatingActionButton = {
            FloatingActionButton(onClick = { showUrlDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.open_url)
                )
            }
        }
    ) { innerPadding ->
        TabsPagerContent(
//            modifier = Modifier.padding(
//                // 左右と下は親のpadding、上は子のpaddingを使用
//                start = parentPadding.calculateStartPadding(LayoutDirection.Ltr),
//                top = innerPadding.calculateTopPadding(),
//                end = parentPadding.calculateEndPadding(LayoutDirection.Ltr),
//                bottom = parentPadding.calculateBottomPadding()
//            ),
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = {}
        )

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
                }
            )
        }
    }
}
