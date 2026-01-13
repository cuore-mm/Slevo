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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import kotlinx.coroutines.launch

/**
 * タブ一覧・URL入力のUIをまとめて提供する。
 *
 * URL入力は検証に失敗した場合、ダイアログ内にエラーを表示する。
 */
@Composable
fun TabScreenContent(
    modifier: Modifier = Modifier,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    closeDrawer: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {}
) {
    // --- Dialog state ---
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val uiState by tabsViewModel.uiState.collectAsState()
    val invalidUrlMessage = stringResource(R.string.invalid_url)
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                urlError = null
                showUrlDialog = true
            }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.open_url)
                )
            }
        }
    ) { innerPadding ->
        // --- Content ---
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

        // --- URL dialog ---
        if (showUrlDialog) {
            UrlOpenDialog(
                onDismissRequest = {
                    showUrlDialog = false
                    urlError = null
                },
                isError = urlError != null,
                errorMessage = urlError,
                isValidating = uiState.isUrlValidating,
                onValueChange = {
                    if (urlError != null) {
                        urlError = null
                    }
                },
                onOpen = { url ->
                    tabsViewModel.startUrlValidation()
                    val resolved = resolveUrl(url)
                    // --- itest board handling ---
                    if (resolved is ResolvedUrl.ItestBoard) {
                        // itest URLはホスト解決が必要なため非同期で処理する。
                        urlError = null
                        coroutineScope.launch {
                            try {
                                val host = tabsViewModel.resolveBoardHost(resolved.boardKey)
                                if (host != null) {
                                    val boardUrl = "https://$host/${resolved.boardKey}/"
                                    val route = AppRoute.Board(
                                        boardName = boardUrl,
                                        boardUrl = boardUrl
                                    )
                                    navController.navigateToBoard(
                                        route = route,
                                        tabsViewModel = tabsViewModel,
                                    )
                                    urlError = null
                                    showUrlDialog = false
                                    closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                                } else {
                                    // URL解析に失敗したため、エラーを表示して閉じない。
                                    urlError = invalidUrlMessage
                                }
                            } finally {
                                tabsViewModel.finishUrlValidation()
                            }
                        }
                        return@UrlOpenDialog
                    }
                    // --- Thread URL handling ---
                    if (resolved is ResolvedUrl.Thread) {
                        val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                        val route = AppRoute.Thread(
                            threadKey = resolved.threadKey,
                            boardUrl = boardUrl,
                            boardName = resolved.boardKey,
                            threadTitle = url
                        )
                        navController.navigateToThread(
                            route = route,
                            tabsViewModel = tabsViewModel,
                        )
                        urlError = null
                        showUrlDialog = false
                        closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                        tabsViewModel.finishUrlValidation()
                        return@UrlOpenDialog
                    }
                    // --- Board URL handling ---
                    if (resolved is ResolvedUrl.Board) {
                        val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                        val route = AppRoute.Board(
                            boardName = boardUrl,
                            boardUrl = boardUrl
                        )
                        navController.navigateToBoard(
                            route = route,
                            tabsViewModel = tabsViewModel,
                        )
                        urlError = null
                        showUrlDialog = false
                        closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                        tabsViewModel.finishUrlValidation()
                        return@UrlOpenDialog
                    }
                    // --- Invalid URL ---
                    // URL解析に失敗したため、エラーを表示して閉じない。
                    urlError = invalidUrlMessage
                    tabsViewModel.finishUrlValidation()
                }
            )
        }
    }
}
