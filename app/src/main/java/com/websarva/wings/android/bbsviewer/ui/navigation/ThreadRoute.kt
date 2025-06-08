package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadBottomBar
import com.websarva.wings.android.bbsviewer.ui.thread.TabsBottomSheet
import com.websarva.wings.android.bbsviewer.ui.common.AddGroupDialog
import com.websarva.wings.android.bbsviewer.ui.common.BookmarkBottomSheet
import com.websarva.wings.android.bbsviewer.ui.drawer.TabInfo
import com.websarva.wings.android.bbsviewer.ui.drawer.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ConfirmationWebView
import com.websarva.wings.android.bbsviewer.ui.thread.PostDialog
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadScreen
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadTopBar
import com.websarva.wings.android.bbsviewer.ui.util.parseBoardUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
fun NavGraphBuilder.addThreadRoute(
    navController: NavHostController,
    tabsViewModel: TabsViewModel,
    openDrawer: () -> Unit,
) {
    composable<AppRoute.Thread> { backStackEntry -> // backStackEntry を引数に追加
        // ページごとに個別のThreadViewModelを保持したいので、ここでは取得しない
        // 開いているスレッドのタブ一覧を監視
        val openTabs by tabsViewModel.openTabs.collectAsState()
        // 画面遷移で受け取った引数を取得
        val threadRoute: AppRoute.Thread = backStackEntry.toRoute()

        // 既に開かれているタブならそのページから表示する
        val initialPage = openTabs.indexOfFirst { it.key == threadRoute.threadKey && it.boardUrl == threadRoute.boardUrl }.let { if (it == -1) 0 else it }
        // HorizontalPager の状態を保持
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { openTabs.size })

        // タブ毎のスクロール状態を保持するマップ
        val listStates = remember(openTabs) { mutableStateMapOf<String, LazyListState>() }
        // スレッドのキー (threadKey と boardUrl) が変更されたらタブ情報を更新する
        LaunchedEffect(threadRoute.threadKey, threadRoute.boardUrl) {
            // タブの処理とスクロール位置の復元
            val existingTab = tabsViewModel.getTabInfo(threadRoute.threadKey, threadRoute.boardUrl)
            val tabToOpen = TabInfo(
                key = threadRoute.threadKey,
                title = threadRoute.threadTitle,
                boardName = threadRoute.boardName,
                boardUrl = threadRoute.boardUrl,
                boardId = threadRoute.boardId,
                firstVisibleItemIndex = existingTab?.firstVisibleItemIndex ?: 0,
                firstVisibleItemScrollOffset = existingTab?.firstVisibleItemScrollOffset ?: 0
            )
            tabsViewModel.openThread(tabToOpen)

            // タブを一意に識別するキーを生成
            val mapKey = tabToOpen.key + tabToOpen.boardUrl
            // 未登録なら初期値で LazyListState を作成
            val state = listStates.getOrPut(mapKey) {
                LazyListState(
                    existingTab?.firstVisibleItemIndex ?: 0,
                    existingTab?.firstVisibleItemScrollOffset ?: 0
                )
            }
            // 以前開いていたタブなら保存済みの位置までスクロール
            if (existingTab != null) {
                state.scrollToItem(
                    existingTab.firstVisibleItemIndex,
                    existingTab.firstVisibleItemScrollOffset
                )
            } else {
                // 新規タブは先頭から表示
                state.scrollToItem(0, 0)
            }
        }


        val threadGroupSheetState = rememberModalBottomSheetState()
        val tabListSheetState = rememberModalBottomSheetState()

        val topBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults
            .exitUntilCollapsedScrollBehavior(topBarState)

        // 各タブを横に並べ、スワイプで切り替えられる Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { page ->
                // 表示対象のタブ情報を取得
                val tab = openTabs[page]
                val mapKey = tab.key + tab.boardUrl

                // 各タブ専用の ViewModel を取得
                val viewModel: ThreadViewModel = hiltViewModel(key = mapKey)
                val uiState by viewModel.uiState.collectAsState()

                // タブごとの LazyListState を取得・作成
                val listState = listStates.getOrPut(mapKey) {
                    LazyListState(
                        tab.firstVisibleItemIndex,
                        tab.firstVisibleItemScrollOffset
                    )
                }

                val isActive = pagerState.currentPage == page
                // ページがアクティブになったときだけスレッドを初期化
                LaunchedEffect(isActive, tab) {
                    if (isActive) {
                        viewModel.initializeThread(
                            threadKey = tab.key,
                            boardInfo = BoardInfo(
                                name = tab.boardName,
                                url = tab.boardUrl,
                                boardId = tab.boardId
                            ),
                            threadTitle = tab.title
                        )
                    }
                }

                // スクロール位置を定期的に保存
                LaunchedEffect(listState) {
                    snapshotFlow {
                        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }
                        .debounce(200L)
                        .collectLatest { (index, offset) ->
                            tabsViewModel.updateScrollPosition(
                                tab.key,
                                tab.boardUrl,
                                index,
                                offset
                            )
                        }
                }

                // 非表示のページでは描画コスト削減のため空リスト
                val posts = if (isActive) uiState.posts ?: emptyList() else emptyList()

                Scaffold(
                    topBar = {
                        ThreadTopBar(
                            onFavoriteClick = { viewModel.handleFavoriteClick() },
                            uiState = uiState,
                            onNavigationClick = openDrawer,
                            scrollBehavior = scrollBehavior
                        )
                    },
                    bottomBar = {
                        ThreadBottomBar(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .height(56.dp),
                            onPostClick = { viewModel.showPostDialog() },
                            onTabListClick = { viewModel.openTabListSheet() },
                            onRefreshClick = { viewModel.reloadThread() }
                        )
                    }
                ) { innerPadding ->
                    ThreadScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        posts = posts,
                        listState = listState
                    )

                }

            // ★ スレッドお気に入りグループ選択ボトムシート
            if (uiState.showThreadGroupSelector) {
                BookmarkBottomSheet(
                    sheetState = threadGroupSheetState,
                    onDismissRequest = { viewModel.dismissThreadGroupSelector() },
                    groups = uiState.availableThreadGroups,
                    selectedGroupId = uiState.currentThreadGroup?.groupId,
                    onGroupSelected = { groupId -> viewModel.selectGroupAndBookmark(groupId) },
                    onUnbookmarkRequested = { viewModel.unbookmarkCurrentThread() },
                    onAddGroup = { viewModel.openAddGroupDialog() }
                )
            }

            // ★ スレッドお気に入りグループ追加ダイアログ
            if (uiState.showAddGroupDialog) {
                AddGroupDialog(
                    onDismissRequest = { viewModel.closeAddGroupDialog() },
                    onAdd = { viewModel.addNewGroup() },
                    onValueChange = { name -> viewModel.setEnteredGroupName(name) },
                    enteredValue = uiState.enteredNewGroupName,
                    onColorSelected = { color -> viewModel.setSelectedColorCode(color) },
                    selectedColor = uiState.selectedColorForNewGroup ?: "#FF0000" // デフォルト色
                )
            }

            if (uiState.showTabListSheet) {
                TabsBottomSheet(
                    sheetState = tabListSheetState,
                    tabsViewModel = tabsViewModel,
                    navController = navController,
                    onDismissRequest = { viewModel.closeTabListSheet() },
                )
            }

            if (uiState.postDialog) {
                PostDialog(
                    onDismissRequest = { viewModel.hidePostDialog() },
                    postFormState = uiState.postFormState,
                    onNameChange = { name ->
                        viewModel.updatePostName(name)
                    },
                    onMailChange = { mail ->
                        viewModel.updatePostMail(mail)
                    },
                    onMessageChange = { message ->
                        viewModel.updatePostMessage(message)
                    },
                    onPostClick = {
                        parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                            viewModel.loadConfirmation(
                                host = host,
                                board = boardKey,
                                threadKey = uiState.threadInfo.key,
                                name = uiState.postFormState.name,
                                mail = uiState.postFormState.mail,
                                message = uiState.postFormState.message
                            )
                        }
                    },
                )
            }

            if (uiState.isConfirmationScreen) {
                uiState.postConfirmation?.let { it1 ->
                    ConfirmationWebView(
                        htmlContent = it1.html,
                        onDismissRequest = { viewModel.hideConfirmationScreen() },
                        onPostClick = {
                            parseBoardUrl(uiState.boardInfo.url)?.let { (host, boardKey) ->
                                uiState.postConfirmation?.let { confirmationData ->
                                    viewModel.postTo5chSecondPhase(
                                        host = host,
                                        board = boardKey,
                                        threadKey = uiState.threadInfo.key,
                                        confirmationData = confirmationData
                                    )
                                }
                            }
                            viewModel.hideConfirmationScreen()
                        }
                    )
                }
            }
        }
    }

}
