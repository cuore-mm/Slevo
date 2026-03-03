package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                pagerState = pagerState,
                onCreateTabClick = {
                    urlError = null
                    showUrlDialog = true
                },
                onRefreshClick = { tabsViewModel.refreshOpenThreads() },
            )
        },
    ) { innerPadding ->
        val listPadding = PaddingValues(
            bottom = innerPadding.calculateBottomPadding() + TabListBottomControlsDefaults.listBottomPadding,
        )

        // --- Content ---
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
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

/**
 * タブ一覧の下部操作群で利用するデフォルト寸法を保持する。
 */
private object TabListBottomControlsDefaults {
    val listBottomPadding: Dp = 120.dp
}

/**
 * 下部の2段操作群を表示し、板/スレ切替とタブ操作を提供する。
 */
@Composable
private fun TabListBottomControls(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    onCreateTabClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    val isBoardPage = pagerState.currentPage == 0
    val coroutineScope = rememberCoroutineScope()

    // --- Floating controls layout ---
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                TabListSegmentRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedIndex = pagerState.currentPage,
                    onSelect = { index ->
                        if (pagerState.currentPage != index) {
                            // --- Page switching ---
                            // 切り替え時のみアニメーションで遷移する。
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onCreateTabClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.open_url),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!isBoardPage) {
                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 板/スレ切替のセグメントボタンを描画する。
 */
@Composable
private fun TabListSegmentRow(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    // --- Segmented options ---
    val options = listOf(
        stringResource(R.string.board),
        stringResource(R.string.thread),
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = selectedIndex == index
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                onClick = { onSelect(index) },
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
