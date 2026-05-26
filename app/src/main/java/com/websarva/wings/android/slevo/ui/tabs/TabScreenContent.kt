package com.websarva.wings.android.slevo.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.board.screen.BoardInfoBottomSheet
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.sheet.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    val uiState by tabsViewModel.uiState.collectAsState()
    val invalidUrlMessage = stringResource(R.string.invalid_url)
    val coroutineScope = rememberCoroutineScope()

    // --- Haze state ---
    val hazeState = rememberHazeState()

    // --- Pager state ---
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tabsViewModel.onPageChanged()
            onPageChanged(page)
        }
    }

    // --- Scaffold ---
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // TabListBottomControls の高さ分の bottom padding。
        // hazeTopOverlap(32) + controlHeight(48) + spacing(8) + progressHeight(8) + bottomPadding(16) = 112.dp
        val bottomControlsHeight = 112.dp
        val listPadding = PaddingValues(
            top = 24.dp,
            bottom = bottomControlsHeight,
        )

        val boxWindowOffset = remember { mutableStateOf(IntOffset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInWindow()
                    boxWindowOffset.value = IntOffset(pos.x.toInt(), pos.y.toInt())
                }
        ) {
            // --- Content with haze source ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
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
            }

            // --- Bottom controls ---
            TabListBottomControls(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                pagerState = pagerState,
                hazeState = hazeState,
                isRefreshing = uiState.isRefreshing,
                refreshProgress = uiState.refreshProgress,
                onCreateTabClick = {
                    tabsViewModel.setUrlErrorMessage(null)
                    tabsViewModel.setUrlDialogVisible(true)
                },
                onRefreshClick = { tabsViewModel.refreshOpenThreads() },
                onCancelRefreshClick = { tabsViewModel.cancelRefreshOpenThreads() },
            )

            // --- Long-press dim overlay ---
            val dimAlpha by animateFloatAsState(
                targetValue = if (uiState.isInLongPressSelectionMode) 0.30f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "dimOverlayAlpha",
            )
            if (dimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAlpha))
                        .clickable { tabsViewModel.cancelTabSelection() }
                )
            }

            // --- Selected tab floating card (overlay layer) ---
            val floatingScale by animateFloatAsState(
                targetValue = if (uiState.isInLongPressSelectionMode) 1.04f else 1f,
                animationSpec = tween(durationMillis = 220),
                label = "floatingCardScale",
            )
            val density = LocalDensity.current
            uiState.selectedBoardTab?.let { tab ->
                uiState.selectedTabBounds?.let { bounds ->
                    val localLeft = bounds.left - boxWindowOffset.value.x
                    val localTop = bounds.top - boxWindowOffset.value.y
                    val cardWidthPx = bounds.right - bounds.left
                    Box(
                        modifier = Modifier
                            .width(with(density) { cardWidthPx.toDp() })
                            .offset { IntOffset(localLeft, localTop) }
                            .graphicsLayer {
                                scaleX = floatingScale
                                scaleY = floatingScale
                                transformOrigin = TransformOrigin.Center
                            }
                            .clickable { /* 選択タブのタップは選択解除しない */ }
                    ) {
                        BoardTabFloatingCard(tab = tab)
                    }
                }
            }
            uiState.selectedThreadTab?.let { tab ->
                uiState.selectedTabBounds?.let { bounds ->
                    val localLeft = bounds.left - boxWindowOffset.value.x
                    val localTop = bounds.top - boxWindowOffset.value.y
                    val cardWidthPx = bounds.right - bounds.left
                    Box(
                        modifier = Modifier
                            .width(with(density) { cardWidthPx.toDp() })
                            .offset { IntOffset(localLeft, localTop) }
                            .graphicsLayer {
                                scaleX = floatingScale
                                scaleY = floatingScale
                                transformOrigin = TransformOrigin.Center
                            }
                            .clickable { /* 選択タブのタップは選択解除しない */ }
                    ) {
                        ThreadTabFloatingCard(
                            tab = tab,
                            newResCount = uiState.newResCounts[tab.id.value] ?: tab.newResCount,
                        )
                    }
                }
            }

            // --- Anchored tab action menu ---
            AnchoredTabActionMenu(
                expanded = uiState.isInLongPressSelectionMode,
                anchorBoundsInWindow = uiState.selectedTabBounds,
                hazeState = hazeState,
                isPinned = uiState.selectedBoardTab?.isPinned
                    ?: uiState.selectedThreadTab?.isPinned
                    ?: false,
                onDismissRequest = { tabsViewModel.cancelTabSelection() },
                onDetailClick = { tabsViewModel.openSelectedTabDetail() },
                onPinClick = { tabsViewModel.toggleSelectedTabPin() },
                onCloseClick = { tabsViewModel.closeSelectedTab() },
            )

            // --- Bottom sheets ---
            if (uiState.showBoardInfoBottomSheet) {
                val boardTab = uiState.selectedBoardTab
                if (boardTab != null) {
                    BoardInfoBottomSheet(
                        showBoardInfoSheet = true,
                        onDismissRequest = { tabsViewModel.dismissBoardInfoBottomSheet() },
                        boardName = boardTab.boardName,
                        serviceName = boardTab.serviceName,
                        boardUrl = boardTab.boardUrl,
                    )
                }
            }
            if (uiState.showThreadInfoBottomSheet) {
                val threadTab = uiState.selectedThreadTab
                if (threadTab != null) {
                    ThreadInfoBottomSheet(
                        showThreadInfoSheet = true,
                        onDismissRequest = { tabsViewModel.dismissThreadInfoBottomSheet() },
                        threadInfo = ThreadInfo(
                            title = threadTab.title,
                            key = threadTab.threadKey,
                            url = "${threadTab.boardUrl}test/read.cgi/${
                                threadTab.boardUrl.substringAfterLast(
                                    "/"
                                ).removeSuffix("/")
                            }/${threadTab.threadKey}/",
                            datUrl = "",
                            resCount = threadTab.resCount,
                        ),
                        boardInfo = BoardInfo(
                            boardId = threadTab.boardId,
                            name = threadTab.boardName,
                            url = threadTab.boardUrl,
                        ),
                        navController = navController,
                        tabsViewModel = tabsViewModel,
                        showBoardAction = true,
                    )
                }
            }

            // --- Back handler for selection mode ---
            if (uiState.isInLongPressSelectionMode) {
                BackHandler { tabsViewModel.cancelTabSelection() }
            }

            // --- URL dialog ---
            if (uiState.showUrlDialog) {
                UrlOpenDialog(
                    onDismissRequest = {
                        tabsViewModel.setUrlDialogVisible(false)
                    },
                    isError = uiState.urlErrorMessage != null,
                    errorMessage = uiState.urlErrorMessage,
                    isValidating = uiState.isUrlValidating,
                    onValueChange = {
                        if (uiState.urlErrorMessage != null) {
                            tabsViewModel.setUrlErrorMessage(null)
                        }
                    },
                    onOpen = { url ->
                        tabsViewModel.startUrlValidation()
                        val resolved = resolveUrl(url)
                        // --- itest board handling ---
                        if (resolved is ResolvedUrl.ItestBoard) {
                            // itest URLはホスト解決が必要なため非同期で処理する。
                            tabsViewModel.setUrlErrorMessage(null)
                            coroutineScope.launch {
                                try {
                                    val host = tabsViewModel.resolveBoardHost(
                                        boardKey = resolved.boardKey,
                                        sourceUrl = resolved.rawUrl,
                                    )
                                    if (host != null) {
                                        val boardUrl = "https://$host/${resolved.boardKey}/"
                                        val route = tabsViewModel.normalizeBoardRouteForNavigation(
                                            AppRoute.Board(
                                                boardName = boardUrl,
                                                boardUrl = boardUrl
                                            )
                                        )
                                        navController.navigateToBoard(
                                            route = route,
                                            tabsViewModel = tabsViewModel,
                                        )
                                        tabsViewModel.setUrlErrorMessage(null)
                                        tabsViewModel.setUrlDialogVisible(false)
                                        closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                                    } else {
                                        // URL解析に失敗したため、エラーを表示して閉じない。
                                        tabsViewModel.setUrlErrorMessage(invalidUrlMessage)
                                    }
                                } finally {
                                    tabsViewModel.finishUrlValidation()
                                }
                            }
                            return@UrlOpenDialog
                        }
                        // --- Thread URL handling ---
                        if (resolved is ResolvedUrl.Thread) {
                            coroutineScope.launch {
                                val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                                val route = tabsViewModel.normalizeThreadRouteForNavigation(
                                    AppRoute.Thread(
                                        threadKey = resolved.threadKey,
                                        boardUrl = boardUrl,
                                        boardName = resolved.boardKey,
                                        threadTitle = null
                                    )
                                )
                                navController.navigateToThread(
                                    route = route,
                                    tabsViewModel = tabsViewModel,
                                )
                                tabsViewModel.setUrlErrorMessage(null)
                                tabsViewModel.setUrlDialogVisible(false)
                                closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                                tabsViewModel.finishUrlValidation()
                            }
                            return@UrlOpenDialog
                        }
                        // --- Board URL handling ---
                        if (resolved is ResolvedUrl.Board) {
                            coroutineScope.launch {
                                val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                                val route = tabsViewModel.normalizeBoardRouteForNavigation(
                                    AppRoute.Board(
                                        boardName = boardUrl,
                                        boardUrl = boardUrl
                                    )
                                )
                                navController.navigateToBoard(
                                    route = route,
                                    tabsViewModel = tabsViewModel,
                                )
                                tabsViewModel.setUrlErrorMessage(null)
                                tabsViewModel.setUrlDialogVisible(false)
                                closeDrawer() // ダイアログを閉じた後、ドロワーも閉じる
                                tabsViewModel.finishUrlValidation()
                            }
                            return@UrlOpenDialog
                        }
                        // --- Invalid URL ---
                        // URL解析に失敗したため、エラーを表示して閉じない。
                        tabsViewModel.setUrlErrorMessage(invalidUrlMessage)
                        tabsViewModel.finishUrlValidation()
                    }
                )
            }
        }
    }
}

/**
 * 選択中の板タブを overlay 上に再描画するための Composable。
 * floating card 側で選択中の視覚状態（拡大・影）を表現する。
 */
@Composable
private fun BoardTabFloatingCard(tab: BoardTabInfo) {
    val color =
        tab.bookmarkColorName?.let { bookmarkColor(it) }
    val serviceName = tab.serviceName.ifBlank { extractServiceName(tab.boardUrl) }

    TabListCard(
        bookmarkColor = color,
        onClick = {},
        isPinned = tab.isPinned,
        headerTitle = serviceName,
        bodyTitle = tab.boardName,
        bodyMaxLines = 1,
        onCloseClick = {},
    )
}

/**
 * 選択中のスレッドタブを overlay 上に再描画するための Composable。
 * floating card 側で選択中の視覚状態（拡大・影）を表現する。
 */
@Composable
private fun ThreadTabFloatingCard(tab: ThreadTabInfo, newResCount: Int) {
    val color =
        tab.bookmarkColorName?.let { bookmarkColor(it) }

    TabListCard(
        bookmarkColor = color,
        onClick = {},
        isPinned = tab.isPinned,
        headerTitle = tab.boardName,
        headerTrailingContent = TabHeaderTrailingContent.ThreadResCount(
            resCount = tab.resCount,
            newResCount = newResCount,
        ),
        bodyTitle = tab.title,
        bodyMaxLines = 2,
        onCloseClick = {},
    )
}
