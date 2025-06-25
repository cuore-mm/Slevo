package com.websarva.wings.android.bbsviewer.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.common.AddGroupDialog
import com.websarva.wings.android.bbsviewer.ui.common.BookmarkBottomSheet
import com.websarva.wings.android.bbsviewer.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsBottomSheet
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.thread.ConfirmationWebView
import com.websarva.wings.android.bbsviewer.ui.thread.PostDialog
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadBottomBar
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadScreen
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadTopBar
import com.websarva.wings.android.bbsviewer.ui.thread.ThreadViewModel
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
    composable<AppRoute.Thread> { backStackEntry ->
        // 開いているスレッドのタブ一覧を監視
        val openTabs by tabsViewModel.openThreadTabs.collectAsState()
        // 画面遷移で受け取った引数を取得
        val threadRoute: AppRoute.Thread = backStackEntry.toRoute()


        // 1. 現在のルートに対応するタブがリストに存在することを保証する
        LaunchedEffect(threadRoute) {
            tabsViewModel.openThreadTab(
                ThreadTabInfo(
                    key = threadRoute.threadKey,
                    title = threadRoute.threadTitle,
                    boardName = threadRoute.boardName,
                    boardUrl = threadRoute.boardUrl,
                    boardId = threadRoute.boardId,
                    resCount = threadRoute.resCount
                )
            )
        }

        // 2. 現在のルートに対応するタブがリストに存在するかチェック
        val currentTabInfo =
            openTabs.find { it.key == threadRoute.threadKey && it.boardUrl == threadRoute.boardUrl }

        if (currentTabInfo != null) {
            // ★ 3. タブが存在する場合：HorizontalPagerを表示
            // ナビゲーション引数(`threadRoute`)が変更された時だけ、初期ページを再計算する
            val initialPage = remember(threadRoute, openTabs.size) {
                openTabs.indexOfFirst { it.key == threadRoute.threadKey && it.boardUrl == threadRoute.boardUrl }
                    .coerceAtLeast(0)
            }

            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { openTabs.size }
            )

            // 計算された初期ページと実際のページが異なればスクロールさせる
            // (例: 新規タブが追加されてリストが更新された場合などに対応)
            LaunchedEffect(initialPage) {
                if (pagerState.currentPage != initialPage) {
                    pagerState.scrollToPage(initialPage)
                }
            }

            val bookmarkSheetState = rememberModalBottomSheetState()
            val tabListSheetState = rememberModalBottomSheetState()

            // 各タブを横に並べ、スワイプで切り替えられる Pager
            HorizontalPager(
                state = pagerState,
            ) { page ->
                // 表示対象のタブ情報を取得
                val tab = openTabs[page]
                val viewModelKey = tab.key + tab.boardUrl

                // 各タブ専用の ViewModel を取得。未登録なら Factory から生成
                val viewModel: ThreadViewModel = tabsViewModel.getOrCreateThreadViewModel(viewModelKey)
                val uiState by viewModel.uiState.collectAsState()

                // rememberのキーにスクロール位置を渡す。
                // これにより、ViewModelに保存されているスクロール位置(`tab`のプロパティ)が
                // 変更されたときに、LazyListStateが新しい初期値で再生成される。
                val listState =
                    remember(tab.firstVisibleItemIndex, tab.firstVisibleItemScrollOffset) {
                        LazyListState(
                            firstVisibleItemIndex = tab.firstVisibleItemIndex,
                            firstVisibleItemScrollOffset = tab.firstVisibleItemScrollOffset
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
                LaunchedEffect(listState, isActive) { // キーにisActiveを追加
                    // このページがアクティブな場合のみ、スクロール位置の保存処理を起動する
                    if (isActive) {
                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }
                            .debounce(200L)
                            .collectLatest { (index, offset) ->
                                tabsViewModel.updateThreadScrollPosition(
                                    tab.key,
                                    tab.boardUrl,
                                    firstVisibleIndex = index,
                                    scrollOffset = offset
                                )
                            }
                    }
                }

                val posts = uiState.posts ?: emptyList()

                val topBarState = rememberTopAppBarState()
                val scrollBehavior = TopAppBarDefaults
                    .exitUntilCollapsedScrollBehavior(topBarState)

                Scaffold(
                    modifier = Modifier
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        Column {
                            ThreadTopBar(
                                onFavoriteClick = { viewModel.handleFavoriteClick() },
                                uiState = uiState,
                                onNavigationClick = openDrawer,
                                scrollBehavior = scrollBehavior
                            )
                            if (uiState.isLoading) {
                                LinearProgressIndicator(
                                    progress = {
                                        uiState.loadProgress
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
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
                        sheetState = bookmarkSheetState,
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
                        enteredValue = uiState.enteredGroupName,
                        onColorSelected = { color -> viewModel.setSelectedColorCode(color) },
                        selectedColor = uiState.selectedColor ?: "#FF0000" // デフォルト色
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
        } else {
            // ★ 4. タブが存在しない場合：読み込み中画面を表示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

}
