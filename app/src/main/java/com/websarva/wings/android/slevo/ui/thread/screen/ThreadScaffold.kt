package com.websarva.wings.android.slevo.ui.thread.screen

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteBottomBar
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteScaffold
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostDialogMode
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.common.SearchBottomBar
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunner
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunnerParams
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogAction
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.components.ThreadToolBar
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.dialog.ThreadToolbarOverflowMenu
import com.websarva.wings.android.slevo.ui.thread.sheet.DisplaySettingsBottomSheet
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuSheet
import com.websarva.wings.android.slevo.ui.thread.sheet.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.rememberBottomBarShowOnBottomBehavior
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * スレッド画面の主要UIを構築する。
 *
 * タブ状態とボトムシートを統合して表示し、操作イベントを各 ViewModel へ委譲する。
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ThreadScaffold(
    threadRoute: AppRoute.Thread,
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val tabsUiState by tabsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentPage by tabsViewModel.threadCurrentPage.collectAsState()
    var isPopupVisible by remember { mutableStateOf(false) }

    val routeThreadId = parseBoardUrl(threadRoute.boardUrl)?.let { (host, board) ->
        ThreadId.of(host, board, threadRoute.threadKey)
    }

    LaunchedEffect(threadRoute, tabsUiState.threadLoaded) {
        if (!tabsUiState.threadLoaded) {
            return@LaunchedEffect
        }
        val info = tabsViewModel.resolveBoardInfo(
            boardId = threadRoute.boardId,
            boardUrl = threadRoute.boardUrl,
            boardName = threadRoute.boardName
        )
        if (info == null || routeThreadId == null) {
            Toast.makeText(context, R.string.invalid_url, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
            return@LaunchedEffect
        }
        val index = tabsViewModel.ensureThreadTab(
            threadRoute.copy(
                boardId = info.boardId,
                boardName = info.name
            )
        )
        if (index >= 0) {
            tabsViewModel.setThreadCurrentPage(index)
        }
    }

    BbsRouteScaffold(
        route = threadRoute,
        tabsViewModel = tabsViewModel,
        navController = navController,
        isTabsLoaded = tabsUiState.threadLoaded,
        onEmptyTabs = { navController.navigateUp() },
        openTabs = tabsUiState.openThreadTabs,
        currentRoutePredicate = { routeThreadId != null && it.id == routeThreadId },
        getViewModel = { tab -> tabsViewModel.getOrCreateThreadViewModel(tab.id.value) },
        getKey = { it.id.value },
        getScrollIndex = { it.firstVisibleItemIndex },
        getScrollOffset = { it.firstVisibleItemScrollOffset },
        initializeViewModel = { viewModel, tab ->
            viewModel.initializeThread(
                threadKey = tab.threadKey,
                boardInfo = BoardInfo(
                    name = tab.boardName,
                    url = tab.boardUrl,
                    boardId = tab.boardId
                ),
                threadTitle = tab.title
            )
        },
        updateScrollPosition = { viewModel, tab, index, offset ->
            viewModel.updateThreadScrollPosition(tab.id, index, offset)
        },
        currentPage = currentPage,
        onPageChange = { tabsViewModel.setThreadCurrentPage(it) },
        animateToPageFlow = tabsViewModel.threadPageAnimation,
        bottomBarScrollBehavior = { listState ->
            rememberBottomBarShowOnBottomBehavior(
                listState = listState,
                scrollEnabled = !isPopupVisible
            )
        },
        bottomBar = { viewModel, uiState, barScrollBehavior, openTabListSheet ->
            BbsRouteBottomBar(
                isSearchMode = uiState.isSearchMode,
                onCloseSearch = { viewModel.closeSearch() },
                animationLabel = "BottomBarAnimation",
                searchContent = { modifier, closeSearch ->
                    SearchBottomBar(
                        modifier = modifier,
                        searchQuery = uiState.searchQuery,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onCloseSearch = closeSearch,
                        placeholderResId = R.string.search_in_thread,
                    )
                },
                defaultContent = { modifier ->
                    ThreadToolBar(
                        modifier = modifier,
                        uiState = uiState,
                        isTreeSort = uiState.sortType == ThreadSortType.TREE,
                        onSortClick = { viewModel.toggleSortType() },
                        onPostClick = { viewModel.postDialogActions.showDialog() },
                        onTabListClick = openTabListSheet,
                        onRefreshClick = { viewModel.reloadThread() },
                        onSearchClick = { viewModel.startSearch() },
                        onBookmarkClick = { viewModel.openBookmarkSheet() },
                        onThreadInfoClick = { viewModel.openThreadInfoSheet() },
                        onMoreClick = { viewModel.openMoreSheet() },
                        onAutoScrollClick = { viewModel.toggleAutoScroll() },
                        scrollBehavior = barScrollBehavior,
                    )
                }
            )
        },
        content = { viewModel, uiState, listState, modifier, navController, showBottomBar, openTabListSheet, openUrlDialog ->
            LaunchedEffect(uiState.threadInfo.key, uiState.isLoading) {
                // スレッドタイトルが空でなく、投稿リストが取得済みの場合にタブ情報を更新
                if (
                    !uiState.isLoading &&
                    uiState.threadInfo.title.isNotEmpty() &&
                    uiState.posts != null &&
                    uiState.threadInfo.key.isNotEmpty()
                ) {
                    parseBoardUrl(uiState.boardInfo.url)?.let { (host, board) ->
                        viewModel.updateThreadTabInfo(
                            threadId = ThreadId.of(host, board, uiState.threadInfo.key),
                            title = uiState.threadInfo.title,
                            resCount = uiState.posts.size
                        )
                    }
                }
            }

            val tabInfo = tabsUiState.openThreadTabs.find {
                it.threadKey == uiState.threadInfo.key && it.boardUrl == uiState.boardInfo.url
            }
            LaunchedEffect(tabInfo?.firstNewResNo, tabInfo?.prevResCount) {
                tabInfo?.let {
                    viewModel.setNewArrivalInfo(it.firstNewResNo, it.prevResCount)
                }
            }
            ThreadScreen(
                modifier = modifier,
                uiState = uiState,
                listState = listState,
                navController = navController,
                tabsViewModel = tabsViewModel,
                showBottomBar = showBottomBar,
                onAutoScrollBottom = { viewModel.onAutoScrollReachedBottom() },
                onBottomRefresh = { viewModel.reloadThread() },
                onLastRead = { resNum ->
                    routeThreadId?.let { viewModel.updateThreadLastRead(it, resNum) }
                },
                onReplyToPost = { viewModel.postDialogActions.showReplyDialog(it) },
                gestureSettings = uiState.gestureSettings,
                onImageLongPress = { url, urls -> viewModel.openImageMenu(url, urls) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onPopupVisibilityChange = { isPopupVisible = it },
                onGestureAction = { action ->
                    when (action) {
                        GestureAction.Refresh -> viewModel.reloadThread()
                        GestureAction.PostOrCreateThread -> viewModel.postDialogActions.showDialog()
                        GestureAction.Search -> viewModel.startSearch()
                        GestureAction.OpenTabList -> openTabListSheet()
                        GestureAction.OpenBookmarkList -> navController.navigate(AppRoute.BookmarkList)
                        GestureAction.OpenBoardList -> navController.navigate(AppRoute.ServiceList)
                        GestureAction.OpenHistory -> navController.navigate(AppRoute.HistoryList)
                        GestureAction.OpenNewTab -> openUrlDialog()
                        GestureAction.SwitchToNextTab -> tabsViewModel.animateThreadPage(1)
                        GestureAction.SwitchToPreviousTab -> tabsViewModel.animateThreadPage(-1)
                        GestureAction.CloseTab ->
                            if (uiState.threadInfo.key.isNotBlank() && uiState.boardInfo.url.isNotBlank()) {
                                tabsViewModel.closeThreadTab(
                                    uiState.threadInfo.key,
                                    uiState.boardInfo.url
                                )
                            }

                        GestureAction.ToTop, GestureAction.ToBottom -> Unit
                    }
                }
            )
        },
        optionalSheetContent = { viewModel, uiState ->
            val clipboard = LocalClipboard.current
            val coroutineScope = rememberCoroutineScope()
            val imageSavePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.onImageSavePermissionResult(context, granted)
            }

            // --- Image save event ---
            LaunchedEffect(viewModel) {
                viewModel.imageSaveEvents.collect { event ->
                    when (event) {
                        is ImageSaveUiEvent.RequestPermission -> {
                            imageSavePermissionLauncher.launch(event.permission)
                        }

                        is ImageSaveUiEvent.ShowToast -> {
                            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            ThreadInfoBottomSheet(
                showThreadInfoSheet = uiState.showThreadInfoSheet,
                onDismissRequest = { viewModel.closeThreadInfoSheet() },
                threadInfo = uiState.threadInfo,
                boardInfo = uiState.boardInfo,
                navController = navController,
                tabsViewModel = tabsViewModel,
            )

            ImageMenuSheet(
                show = uiState.showImageMenuSheet,
                imageUrl = uiState.imageMenuTargetUrl,
                imageUrls = uiState.imageMenuTargetUrls,
                onActionSelected = { action ->
                    val targetUrl = uiState.imageMenuTargetUrl.orEmpty()
                    ImageMenuActionRunner.run(
                        action = action,
                        params = ImageMenuActionRunnerParams(
                            context = context,
                            coroutineScope = coroutineScope,
                            currentImageUrl = targetUrl,
                            imageUrls = uiState.imageMenuTargetUrls,
                            onOpenNgDialog = { url -> viewModel.openImageNgDialog(url) },
                            onRequestSaveSingle = { url ->
                                viewModel.requestImageSave(context, listOf(url))
                            },
                            onRequestSaveAll = { urls ->
                                // レス内画像が2件以上ある場合のみ処理する。
                                if (urls.size >= 2) {
                                    viewModel.requestImageSave(context, urls)
                                }
                            },
                            onActionHandled = { viewModel.closeImageMenu() },
                            onSetClipboardText = { text ->
                                val clip = ClipData.newPlainText("", text).toClipEntry()
                                clipboard.setClipEntry(clip)
                            },
                            onSetClipboardImageUri = { uri ->
                                val clip = ClipData.newUri(
                                    context.contentResolver,
                                    "",
                                    uri
                                ).toClipEntry()
                                clipboard.setClipEntry(clip)
                            },
                        ),
                    )
                },
                onDismissRequest = { viewModel.closeImageMenu() },
            )

            if (uiState.showImageNgDialog) {
                uiState.imageNgTargetUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    NgDialogRoute(
                        text = url,
                        type = NgType.WORD,
                        boardName = uiState.boardInfo.name,
                        boardId = uiState.boardInfo.boardId.takeIf { it != 0L },
                        onDismiss = { viewModel.closeImageNgDialog() }
                    )
                }
            }

            if (uiState.showMoreSheet) {
                ThreadToolbarOverflowMenu(
                    onDismissRequest = { viewModel.closeMoreSheet() },
                    onBookmarkClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.BookmarkList)
                    },
                    onBoardListClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.ServiceList)
                    },
                    onHistoryClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.HistoryList)
                    },
                    onSettingsClick = {
                        viewModel.closeMoreSheet()
                        navController.navigate(AppRoute.SettingsHome)
                    },
                    onDisplaySettingsClick = {
                        viewModel.closeMoreSheet()
                        viewModel.openDisplaySettingsSheet()
                    }
                )
            }

            DisplaySettingsBottomSheet(
                show = uiState.showDisplaySettingsSheet,
                textScale = uiState.textScale,
                isIndividual = uiState.isIndividualTextScale,
                headerTextScale = uiState.headerTextScale,
                bodyTextScale = uiState.bodyTextScale,
                lineHeight = uiState.lineHeight,
                onDismissRequest = { viewModel.closeDisplaySettingsSheet() },
                onTextScaleChange = { viewModel.updateTextScale(it) },
                onIndividualChange = { viewModel.updateIndividualTextScale(it) },
                onHeaderTextScaleChange = { viewModel.updateHeaderTextScale(it) },
                onBodyTextScaleChange = { viewModel.updateBodyTextScale(it) },
                onLineHeightChange = { viewModel.updateLineHeight(it) }
            )

            val postDialogState = uiState.postDialogState
            if (postDialogState.isDialogVisible) {
                val context = LocalContext.current
                PostDialog(
                    uiState = postDialogState,
                    onDismissRequest = { viewModel.postDialogActions.hideDialog() },
                    onAction = { action ->
                        when (action) {
                            is PostDialogAction.ChangeName ->
                                viewModel.postDialogActions.updateName(action.value)

                            is PostDialogAction.ChangeMail ->
                                viewModel.postDialogActions.updateMail(action.value)

                            is PostDialogAction.ChangeMessage ->
                                viewModel.postDialogActions.updateMessage(action.value)

                            is PostDialogAction.SelectNameHistory ->
                                viewModel.postDialogActions.selectNameHistory(action.value)

                            is PostDialogAction.SelectMailHistory ->
                                viewModel.postDialogActions.selectMailHistory(action.value)

                            is PostDialogAction.DeleteNameHistory ->
                                viewModel.postDialogActions.deleteNameHistory(action.value)

                            is PostDialogAction.DeleteMailHistory ->
                                viewModel.postDialogActions.deleteMailHistory(action.value)

                            PostDialogAction.Post -> {
                                parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                    viewModel.postDialogActions.postFirstPhase(
                                        host,
                                        boardKey,
                                        threadKey = uiState.threadInfo.key,
                                    )
                                }
                            }

                            is PostDialogAction.ChangeTitle -> Unit
                        }
                    },
                    onImageUpload = { uri -> viewModel.uploadImage(context, uri) },
                    onImageUrlClick = { urls, tappedIndex ->
                        if (urls.isEmpty()) {
                            // Guard: 画像が存在しない場合は遷移しない。
                        } else {
                            val encodedUrls = urls.map { imageUrl ->
                                URLEncoder.encode(imageUrl, StandardCharsets.UTF_8.toString())
                            }
                            navController.navigate(
                                AppRoute.ImageViewer(
                                    imageUrls = encodedUrls,
                                    initialIndex = tappedIndex.coerceIn(encodedUrls.indices),
                                )
                            )
                        }
                    },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    mode = PostDialogMode.Reply
                )
            }

            if (postDialogState.isConfirmationScreen) {
                postDialogState.postConfirmation?.let { confirmationData ->
                    ResponseWebViewDialog(
                        htmlContent = confirmationData.html,
                        onDismissRequest = { viewModel.postDialogActions.hideConfirmationScreen() },
                        onConfirm = {
                            parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                viewModel.postDialogActions.postSecondPhase(
                                    host,
                                    boardKey,
                                    threadKey = uiState.threadInfo.key,
                                    confirmationData = confirmationData,
                                )
                            }
                        },
                        title = "書き込み確認",
                        confirmButtonText = "書き込む"
                    )
                }
            }

            if (postDialogState.showErrorWebView) {
                ResponseWebViewDialog(
                    htmlContent = postDialogState.errorHtmlContent,
                    onDismissRequest = { viewModel.postDialogActions.hideErrorWebView() },
                    title = "応答結果",
                    onConfirm = null // 確認ボタンは不要なのでnull
                )
            }

            if (postDialogState.isPosting) {
                PostingDialog()
            }
        }
    )
}
