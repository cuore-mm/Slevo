package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import kotlinx.coroutines.launch

/**
 * タブ一覧とURL入力ダイアログを統合した画面を提供する。
 *
 * URL入力は検証に失敗した場合、ダイアログ内にエラーを表示する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    // --- Haze state ---
    val hazeState = rememberHazeState()

    // --- Pager state ---
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onPageChanged(it) }
    }

    // --- Scaffold ---
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            TabListBottomControls(
                modifier = Modifier.fillMaxWidth(),
                pagerState = pagerState,
                hazeState = hazeState,
                onCreateTabClick = {
                    urlError = null
                    showUrlDialog = true
                },
                onRefreshClick = { tabsViewModel.refreshOpenThreads() },
            )
        },
    ) { innerPadding ->
        val listPadding = PaddingValues(
            top = 24.dp,
            bottom = innerPadding.calculateBottomPadding() + TabListBottomControlsDefaults.listBottomPadding,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            // --- Content ---
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator()
                }
            } else {
                TabsPagerContent(
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                    pagerState = pagerState,
                    tabsViewModel = tabsViewModel,
                    navController = navController,
                    closeDrawer = closeDrawer,
                    listContentPadding = listPadding,
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
}
