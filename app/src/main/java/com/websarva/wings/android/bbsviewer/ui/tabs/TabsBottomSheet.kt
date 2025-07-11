package com.websarva.wings.android.bbsviewer.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsPagerContent
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl
import com.websarva.wings.android.bbsviewer.ui.util.parseThreadUrl
import com.websarva.wings.android.bbsviewer.ui.tabs.UrlOpenDialog

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    onDismissRequest: () -> Unit,
) {
    var showUrlDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Scaffold(
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
                tabsViewModel = tabsViewModel,
                navController = navController,
                closeDrawer = onDismissRequest
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
                                    threadTitle = ""
                                )
                            ) { launchSingleTop = true }
                        } else {
                            parseBoardUrl(url)?.let { (host, board) ->
                                val boardUrl = "https://$host/$board/"
                                navController.navigate(
                                    AppRoute.Board(
                                        boardId = 0L,
                                        boardName = board,
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
}
