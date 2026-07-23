package com.websarva.wings.android.slevo.ui.thread.screen

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteBottomBar
import com.websarva.wings.android.slevo.ui.bbsroute.BbsRouteScaffold
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunner
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunnerParams
import com.websarva.wings.android.slevo.ui.common.PostDialog
import com.websarva.wings.android.slevo.ui.common.PostDialogMode
import com.websarva.wings.android.slevo.ui.common.PostingDialog
import com.websarva.wings.android.slevo.ui.common.SearchBottomBar
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.common.interaction.CommonGestureActionHandlers
import com.websarva.wings.android.slevo.ui.common.interaction.dispatchCommonGestureAction
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogAction
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.buildImageViewerRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThreadScreen
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.components.ThreadToolBar
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.thread.dialog.ResponseWebViewDialog
import com.websarva.wings.android.slevo.ui.thread.dialog.ThreadToolbarOverflowMenu
import com.websarva.wings.android.slevo.ui.thread.res.PostDialogTarget
import com.websarva.wings.android.slevo.ui.thread.res.ReplyPopup
import com.websarva.wings.android.slevo.ui.thread.res.rememberPostItemDialogState
import com.websarva.wings.android.slevo.ui.thread.sheet.DisplaySettingsBottomSheet
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuSheet
import com.websarva.wings.android.slevo.ui.thread.sheet.ThreadInfoBottomSheet
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadRouteViewModel
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import kotlinx.coroutines.launch

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
    tabSessionStore: TabSessionStore,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val routeViewModel: ThreadRouteViewModel = hiltViewModel()
    val threadLoaded by tabSessionStore.threadLoaded.collectAsState()
    val openThreadTabs by tabSessionStore.openThreadTabs.collectAsState()
    val selectedThreadTabKey by tabSessionStore.selectedThreadTabKey.collectAsState()
    val context = LocalContext.current
    var isPopupVisible by remember { mutableStateOf(false) }
    val popupDialogState = rememberPostItemDialogState()
    var popupMenuTarget by remember { mutableStateOf<PostDialogTarget?>(null) }
    var popupDialogTarget by remember { mutableStateOf<PostDialogTarget?>(null) }

    val routeThreadId = parseBoardUrl(threadRoute.boardUrl)?.let { (host, board) ->
        ThreadId.of(host, board, threadRoute.threadKey)
    }

    LaunchedEffect(threadRoute, threadLoaded) {
        if (!threadLoaded) {
            return@LaunchedEffect
        }
        // route 引数は初期化入力・placeholder として扱い、既に有効な選択中タブがある場合は上書きしない。
        if (selectedThreadTabKey != null && openThreadTabs.any { it.id.value == selectedThreadTabKey }) {
            return@LaunchedEffect
        }
        val info = tabSessionStore.resolveBoardInfo(
            boardId = threadRoute.boardId,
            boardUrl = threadRoute.boardUrl,
            boardName = threadRoute.boardName
        )
        if (info == null || routeThreadId == null) {
            Toast.makeText(context, R.string.invalid_url, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
            return@LaunchedEffect
        }
        val index = tabSessionStore.ensureThreadTab(
            threadRoute.copy(
                boardId = info.boardId,
                boardName = info.name
            )
        )
        if (index >= 0) {
            tabSessionStore.selectThreadTab(routeThreadId)
        }
    }

    BbsRouteScaffold(
        route = threadRoute,
        tabSessionStore = tabSessionStore,
        navController = navController,
        isTabsLoaded = threadLoaded,
        onEmptyTabs = { navController.navigateUp() },
        openTabs = openThreadTabs,
        selectedTabKey = selectedThreadTabKey,
        getUiState = { tab -> routeViewModel.uiStateFor(tab.id.value) },
        getBookmarkSheetHolder = { tab -> routeViewModel.bookmarkSheetHolderFor(tab.id.value) },
        getKey = { it.id.value },
        getScrollIndex = { it.firstVisibleItemIndex },
        getScrollOffset = { it.firstVisibleItemScrollOffset },
        updateScrollPosition = { tab, index, offset ->
            routeViewModel.updateThreadScrollPosition(tab.id, index, offset)
        },
        onTabSelected = { tabSessionStore.selectThreadTab(it.id) },
        animateToPageFlow = tabSessionStore.threadPageAnimation,
        bottomBarActionVisibilityEnabled = !isPopupVisible,
        bottomBar = { tab, uiState, actionProgress, openTabListSheet ->
            BbsRouteBottomBar(
                isSearchMode = uiState.isSearchMode,
                onCloseSearch = { routeViewModel.closeSearch(tab.id.value) },
                animationLabel = "BottomBarAnimation",
                searchContent = { modifier, closeSearch ->
                    SearchBottomBar(
                        modifier = modifier,
                        searchInputValue = uiState.searchInputValue,
                        onSearchInputChange = { routeViewModel.updateSearchInput(tab.id.value, it) },
                        onCloseSearch = closeSearch,
                        placeholderResId = R.string.search_in_thread,
                    )
                },
                defaultContent = { modifier ->
                    ThreadToolBar(
                        modifier = modifier,
                        uiState = uiState,
                        isTreeSort = uiState.sortType == ThreadSortType.TREE,
                        onSortClick = { routeViewModel.toggleSortType(tab.id.value) },
                        onPostClick = { routeViewModel.postDialogActionsFor(tab.id.value).showDialog() },
                        onTabListClick = openTabListSheet,
                        onRefreshClick = { routeViewModel.reloadThread(tab.id.value) },
                        onSearchClick = { routeViewModel.startSearch(tab.id.value) },
                        onBookmarkClick = { routeViewModel.openBookmarkSheet(tab.id.value) },
                        onThreadInfoClick = { routeViewModel.openThreadInfoSheet(tab.id.value) },
                        onMoreClick = { routeViewModel.openMoreSheet(tab.id.value) },
                        onAutoScrollClick = { routeViewModel.toggleAutoScroll(tab.id.value) },
                        actionsProgress = if (uiState.isSearchMode) 0f else actionProgress,
                    )
                }
            )
        },
        content = { tab, uiState, listState, modifier, navController, openTabListSheet, openUrlDialog ->
            LaunchedEffect(uiState.threadInfo.key, uiState.isLoading) {
                // スレッドタイトルが空でなく、投稿リストが取得済みの場合にタブ情報を更新
                if (
                    !uiState.isLoading &&
                    uiState.threadInfo.title.isNotEmpty() &&
                    uiState.posts != null &&
                    uiState.threadInfo.key.isNotEmpty()
                ) {
                    parseBoardUrl(uiState.boardInfo.url)?.let { (host, board) ->
                        routeViewModel.updateThreadTabInfo(
                            threadId = ThreadId.of(host, board, uiState.threadInfo.key),
                            title = uiState.threadInfo.title,
                            resCount = uiState.posts.size
                        )
                    }
                }
            }

            val tabInfo = openThreadTabs.find {
                it.threadKey == uiState.threadInfo.key && it.boardUrl == uiState.boardInfo.url
            }
            LaunchedEffect(tabInfo?.firstNewResNo, tabInfo?.prevResCount) {
                tabInfo?.let {
                    routeViewModel.setNewArrivalInfo(tab.id.value, it.firstNewResNo, it.prevResCount)
                }
            }
            ThreadScreen(
                modifier = modifier,
                uiState = uiState,
                listState = listState,
                navController = navController,
                tabSessionStore = tabSessionStore,
                onAutoScrollBottom = { routeViewModel.onAutoScrollReachedBottom(tab.id.value) },
                onBottomRefresh = { routeViewModel.reloadThreadFromBottomPull(tab.id.value) },
                onLastRead = { resNum ->
                    routeThreadId?.let { routeViewModel.updateThreadLastRead(it, resNum) }
                },
                gestureSettings = uiState.gestureSettings,
                onPopupVisibilityChange = { isPopupVisible = it },
                onRequestPostMenu = { target -> popupMenuTarget = target },
                onRequestTextMenu = { text, type -> popupDialogState.showTextMenu(text, type) },
                onImageLongPress = { url, urls -> routeViewModel.openImageMenu(tab.id.value, url, urls) },
                onImageLoadStart = { url -> routeViewModel.onThreadImageLoadStart(tab.id.value, url) },
                onImageLoadError = { url, failureType ->
                    routeViewModel.onThreadImageLoadError(tab.id.value, url, failureType)
                },
                onImageLoadSuccess = { url -> routeViewModel.onThreadImageLoadSuccess(tab.id.value, url) },
                onImageRetry = { url -> routeViewModel.onThreadImageRetry(tab.id.value, url) },
                onRequestTreePopup = { postNum, baseOffset ->
                    routeViewModel.addPopupForTree(tab.id.value, baseOffset, postNum)
                },
                onAddPopupForReplyFrom = { replyNumbers, baseOffset ->
                    routeViewModel.addPopupForReplyFrom(tab.id.value, baseOffset, replyNumbers)
                },
                onAddPopupForReplyNumber = { postNumber, baseOffset ->
                    routeViewModel.addPopupForReplyNumber(tab.id.value, baseOffset, postNumber)
                },
                onAddPopupForId = { id, baseOffset ->
                    routeViewModel.addPopupForId(tab.id.value, baseOffset, id)
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onGestureAction = { action ->
                    dispatchCommonGestureAction(
                        action = action,
                        handlers = CommonGestureActionHandlers(
                            onRefresh = { routeViewModel.reloadThread(tab.id.value) },
                            onPostOrCreateThread = { routeViewModel.postDialogActionsFor(tab.id.value).showDialog() },
                            onSearch = { routeViewModel.startSearch(tab.id.value) },
                            onOpenTabList = openTabListSheet,
                            onOpenBookmarkList = { navController.navigate(AppRoute.BookmarkList) },
                            onOpenBoardList = { navController.navigate(AppRoute.ServiceList) },
                            onOpenHistory = { navController.navigate(AppRoute.HistoryList) },
                            onOpenNewTab = openUrlDialog,
                            onSwitchToNextTab = { tabSessionStore.animateThreadPage(1) },
                            onSwitchToPreviousTab = { tabSessionStore.animateThreadPage(-1) },
                            onCloseTab = {
                                if (uiState.threadInfo.key.isNotBlank() && uiState.boardInfo.url.isNotBlank()) {
                                    tabSessionStore.closeThreadTab(
                                        uiState.threadInfo.key,
                                        uiState.boardInfo.url,
                                    )
                                }
                            },
                        ),
                    )
                }
            )
        },
        optionalSheetContent = { tab, uiState ->
            val clipboard = LocalClipboard.current
            val coroutineScope = rememberCoroutineScope()
            val uriHandler = LocalUriHandler.current
            val imageSavePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                routeViewModel.onImageSavePermissionResult(tab.id.value, context, granted)
            }

            // --- Image save event ---
            val imageSaveEvents = remember(tab.id.value) { routeViewModel.imageSaveEventsFor(tab.id.value) }
            LaunchedEffect(imageSaveEvents) {
                imageSaveEvents.collect { event ->
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

            // --- Load error event ---
            LaunchedEffect(uiState.pendingToastResId) {
                uiState.pendingToastResId?.let { resId ->
                    Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
                    routeViewModel.consumeToast(tab.id.value)
                }
            }

            ReplyPopup(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                popupStack = uiState.popupStack,
                posts = uiState.posts ?: emptyList(),
                replySourceMap = uiState.replySourceMap,
                idCountMap = uiState.idCountMap,
                idIndexList = uiState.idIndexList,
                ngPostNumbers = uiState.ngPostNumbers,
                myPostNumbers = uiState.myPostNumbers,
                headerTextScale = if (uiState.isIndividualTextScale) uiState.headerTextScale else uiState.textScale * 0.85f,
                bodyTextScale = if (uiState.isIndividualTextScale) uiState.bodyTextScale else uiState.textScale,
                lineHeight = if (uiState.isIndividualTextScale) {
                    uiState.lineHeight
                } else {
                    TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT
                },
                searchQuery = uiState.searchQuery,
                onUrlClick = { url -> uriHandler.openUri(url) },
                onThreadUrlClick = { route ->
                    coroutineScope.launch {
                        val normalizedRoute = tabSessionStore.normalizeThreadRouteForNavigation(route)
                        tabSessionStore.registerAndSelectThreadRoute(normalizedRoute)
                        navController.navigateToThreadScreen(normalizedRoute)
                    }
                },
                onImageClick = { _, imageUrls, tappedIndex, transitionNamespace ->
                    val route = buildImageViewerRoute(
                        imageUrls = imageUrls,
                        tappedIndex = tappedIndex,
                        transitionNamespace = transitionNamespace,
                    )
                    route?.let(navController::navigate)
                },
                onImageLongPress = { url, urls -> routeViewModel.openImageMenu(tab.id.value, url, urls) },
                imageLoadFailureByUrl = uiState.imageLoadFailureByUrl,
                onImageLoadStart = { url -> routeViewModel.onThreadImageLoadStart(tab.id.value, url) },
                onImageLoadError = { url, failureType ->
                    routeViewModel.onThreadImageLoadError(tab.id.value, url, failureType)
                },
                onImageLoadSuccess = { url -> routeViewModel.onThreadImageLoadSuccess(tab.id.value, url) },
                onImageRetry = { url -> routeViewModel.onThreadImageRetry(tab.id.value, url) },
                onRequestMenu = { target -> popupMenuTarget = target },
                onShowTextMenu = { text, type -> popupDialogState.showTextMenu(text, type) },
                onRequestTreePopup = { postNum, baseOffset ->
                    routeViewModel.addPopupForTree(tab.id.value, baseOffset, postNum)
                },
                onAddPopupForReplyFrom = { replyNumbers, baseOffset ->
                    routeViewModel.addPopupForReplyFrom(tab.id.value, baseOffset, replyNumbers)
                },
                onAddPopupForReplyNumber = { postNumber, baseOffset ->
                    routeViewModel.addPopupForReplyNumber(tab.id.value, baseOffset, postNumber)
                },
                onAddPopupForId = { id, baseOffset ->
                    routeViewModel.addPopupForId(tab.id.value, baseOffset, id)
                },
                onPopupSizeChange = { index, size ->
                    routeViewModel.updatePopupSize(tab.id.value, index, size)
                },
                onClose = { routeViewModel.removeTopPopup(tab.id.value) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )

            ReplyActionOverlayHost(
                menuTarget = popupMenuTarget,
                dialogTarget = popupDialogTarget,
                boardName = uiState.boardInfo.name,
                boardId = uiState.boardInfo.boardId,
                scope = coroutineScope,
                dialogState = popupDialogState,
                onClearMenuTarget = { popupMenuTarget = null },
                onReply = { target -> routeViewModel.postDialogActionsFor(tab.id.value).showReplyDialog(target.postNum) },
                onCopy = { target ->
                    popupDialogTarget = target
                    popupDialogState.showCopyDialog()
                },
                onNg = { target ->
                    popupDialogTarget = target
                    popupDialogState.showNgSelectDialog()
                },
            )

            ThreadInfoBottomSheet(
                showThreadInfoSheet = uiState.showThreadInfoSheet,
                onDismissRequest = { routeViewModel.closeThreadInfoSheet(tab.id.value) },
                threadInfo = uiState.threadInfo,
                boardInfo = uiState.boardInfo,
                navController = navController,
                tabSessionStore = tabSessionStore,
            )

            // --- Image menu state ---
            val loadingImageUrls = uiState.imageLoadingUrls
            ImageMenuSheet(
                show = uiState.showImageMenuSheet,
                imageUrl = uiState.imageMenuTargetUrl,
                imageUrls = uiState.imageMenuTargetUrls,
                imageLoadFailureByUrl = uiState.imageLoadFailureByUrl,
                loadingImageUrls = loadingImageUrls,
                onActionSelected = { action ->
                    val targetUrl = uiState.imageMenuTargetUrl.orEmpty()
                    ImageMenuActionRunner.run(
                        action = action,
                        params = ImageMenuActionRunnerParams(
                            context = context,
                            coroutineScope = coroutineScope,
                            currentImageUrl = targetUrl,
                            imageUrls = uiState.imageMenuTargetUrls,
                            onOpenNgDialog = { url -> routeViewModel.openImageNgDialog(tab.id.value, url) },
                onRequestSaveSingle = { url ->
                    routeViewModel.requestImageSave(tab.id.value, context, listOf(url))
                },
                onRequestSaveAll = { urls ->
                    // レス内画像が2件以上ある場合のみ処理する。
                    if (urls.size >= 2) {
                        routeViewModel.requestImageSave(tab.id.value, context, urls)
                    }
                },
                onActionHandled = { routeViewModel.closeImageMenu(tab.id.value) },

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
                onDismissRequest = { routeViewModel.closeImageMenu(tab.id.value) },
            )

            if (uiState.showImageNgDialog) {
                uiState.imageNgTargetUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    NgDialogRoute(
                        text = url,
                        type = NgType.WORD,
                        boardName = uiState.boardInfo.name,
                        boardId = uiState.boardInfo.boardId.takeIf { it != 0L },
                        onDismiss = { routeViewModel.closeImageNgDialog(tab.id.value) }
                    )
                }
            }

            if (uiState.showMoreSheet) {
                ThreadToolbarOverflowMenu(
                    onDismissRequest = { routeViewModel.closeMoreSheet(tab.id.value) },
                    onBookmarkClick = {
                        routeViewModel.closeMoreSheet(tab.id.value)
                        navController.navigate(AppRoute.BookmarkList)
                    },
                    onBoardListClick = {
                        routeViewModel.closeMoreSheet(tab.id.value)
                        navController.navigate(AppRoute.ServiceList)
                    },
                    onHistoryClick = {
                        routeViewModel.closeMoreSheet(tab.id.value)
                        navController.navigate(AppRoute.HistoryList)
                    },
                    onSettingsClick = {
                        routeViewModel.closeMoreSheet(tab.id.value)
                        navController.navigate(AppRoute.SettingsHome)
                    },
                    onDisplaySettingsClick = {
                        routeViewModel.closeMoreSheet(tab.id.value)
                        routeViewModel.openDisplaySettingsSheet(tab.id.value)
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
                onDismissRequest = { routeViewModel.closeDisplaySettingsSheet(tab.id.value) },
                onTextScaleChange = { routeViewModel.updateTextScale(it) },
                onIndividualChange = { routeViewModel.updateIndividualTextScale(it) },
                onHeaderTextScaleChange = { routeViewModel.updateHeaderTextScale(it) },
                onBodyTextScaleChange = { routeViewModel.updateBodyTextScale(it) },
                onLineHeightChange = { routeViewModel.updateLineHeight(it) }
            )

            val postDialogState = uiState.postDialogState
            if (postDialogState.isDialogVisible) {
                val context = LocalContext.current
                PostDialog(
                    uiState = postDialogState,
                    onDismissRequest = { routeViewModel.postDialogActionsFor(tab.id.value).hideDialog() },
                    onAction = { action ->
                        when (action) {
                            is PostDialogAction.ChangeName ->
                                routeViewModel.postDialogActionsFor(tab.id.value).updateName(action.value)

                            is PostDialogAction.ChangeMail ->
                                routeViewModel.postDialogActionsFor(tab.id.value).updateMail(action.value)

                            is PostDialogAction.ChangeMessage ->
                                routeViewModel.postDialogActionsFor(tab.id.value).updateMessage(action.value)

                            is PostDialogAction.SelectNameHistory ->
                                routeViewModel.postDialogActionsFor(tab.id.value).selectNameHistory(action.value)

                            is PostDialogAction.SelectMailHistory ->
                                routeViewModel.postDialogActionsFor(tab.id.value).selectMailHistory(action.value)

                            is PostDialogAction.DeleteNameHistory ->
                                routeViewModel.postDialogActionsFor(tab.id.value).deleteNameHistory(action.value)

                            is PostDialogAction.DeleteMailHistory ->
                                routeViewModel.postDialogActionsFor(tab.id.value).deleteMailHistory(action.value)

                            PostDialogAction.Post -> {
                                parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                    routeViewModel.postDialogActionsFor(tab.id.value).postFirstPhase(
                                        host,
                                        boardKey,
                                        threadKey = uiState.threadInfo.key,
                                    )
                                }
                            }

                            is PostDialogAction.ChangeTitle -> Unit
                        }
                    },
                    onImageUpload = { uri -> routeViewModel.uploadPostDialogImage(tab.id.value, context, uri) },
                    onImageUrlClick = { urls, tappedIndex, transitionNamespace ->
                        val route = buildImageViewerRoute(
                            imageUrls = urls,
                            tappedIndex = tappedIndex,
                            transitionNamespace = transitionNamespace,
                        )
                        route?.let(navController::navigate)
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
                    onDismissRequest = { routeViewModel.postDialogActionsFor(tab.id.value).hideConfirmationScreen() },
                    onConfirm = {
                        parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                            routeViewModel.postDialogActionsFor(tab.id.value).postSecondPhase(
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
                    onDismissRequest = { routeViewModel.postDialogActionsFor(tab.id.value).hideErrorWebView() },
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
