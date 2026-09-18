package com.websarva.wings.android.slevo.ui.bookmarklist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.common.SelectedTopBarScreen
import com.websarva.wings.android.slevo.ui.common.mergeScaffoldPaddingValues
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetHost
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.BbsEntryTransition
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoardScreen
import com.websarva.wings.android.slevo.ui.navigation.navigateToThreadScreen
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import kotlinx.coroutines.launch

/**
 * ブックマーク画面の AppBar、一覧、選択用 BottomBar を構成する。
 *
 * ルート chrome と画面 Scaffold の余白は一覧コンテナへ一度だけ渡す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkListScaffold(
    appChromePadding: PaddingValues,
    navController: NavHostController,
    topBarState: TopAppBarState,
    openDrawer: () -> Unit,
    tabSessionStore: TabSessionStore,
    onOpenBoard: (AppRoute.Board) -> Unit = { route -> navController.navigateToBoardScreen(route) },
    onOpenThread: (AppRoute.Thread) -> Unit = { route -> navController.navigateToThreadScreen(route) },
) {
    val bookmarkViewModel: BookmarkViewModel = hiltViewModel()
    val uiState by bookmarkViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState)

    val editSheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            Box {
                AnimatedVisibility(
                    visible = !uiState.selectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BookmarkTopBar(
                        scrollBehavior = scrollBehavior,
                        onNavigationClick = openDrawer,
                        onAddClick = { },
                        onSearchClick = { }
                    )
                }
                AnimatedVisibility(
                    visible = uiState.selectMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    SelectedTopBarScreen(
                        onBack = { bookmarkViewModel.toggleSelectMode(false) },
                        selectedCount = uiState.selectedBoards.size + uiState.selectedThreads.size
                    )
                }
            }
        },
    ) { innerPadding ->
        val contentPadding = mergeScaffoldPaddingValues(innerPadding, appChromePadding)
        BookmarkScreen(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
            boardGroups = uiState.boardList,
            onBoardClick = { board ->
                coroutineScope.launch {
                    val route = tabSessionStore.normalizeBoardRouteForNavigation(
                        AppRoute.Board(
                            boardId = board.boardId,
                            boardName = board.name,
                            boardUrl = board.url
                        )
                    )
                    tabSessionStore.registerAndSelectBoardRoute(route)
                    onOpenBoard(route.copy(entryTransition = BbsEntryTransition.MainShellSlide))
                }
            },
            threadGroups = uiState.groupedThreadBookmarks,
            onThreadClick = { thread ->
                coroutineScope.launch {
                    val route = tabSessionStore.normalizeThreadRouteForNavigation(
                        AppRoute.Thread(
                            threadKey = thread.threadKey,
                            boardName = thread.boardName,
                            boardUrl = thread.boardUrl,
                            threadTitle = thread.title,
                            boardId = thread.boardId,
                            resCount = thread.resCount
                        )
                    )
                    val index = tabSessionStore.registerAndSelectThreadRoute(route)
                    if (index >= 0) {
                        onOpenThread(route.copy(entryTransition = BbsEntryTransition.MainShellSlide))
                    }
                }
            },
            selectMode = uiState.selectMode,
            selectedBoardIds = uiState.selectedBoards,
            selectedThreadIds = uiState.selectedThreads,
            onBoardLongClick = { id ->
                bookmarkViewModel.toggleSelectMode(true)
                bookmarkViewModel.toggleBoardSelect(id)
            },
            onThreadLongClick = { id ->
                bookmarkViewModel.toggleSelectMode(true)
                bookmarkViewModel.toggleThreadSelect(id)
            },
        )

        BackHandler(enabled = uiState.selectMode) {
            bookmarkViewModel.toggleSelectMode(false)
        }

        val bookmarkSheetHolder = bookmarkViewModel.bookmarkSheetHolder

        BookmarkSheetHost(
            sheetState = editSheetState,
            holder = bookmarkSheetHolder,
            uiState = uiState.bookmarkSheetState,
            onAfterApply = { bookmarkViewModel.toggleSelectMode(false) },
            onAfterUnbookmark = { bookmarkViewModel.toggleSelectMode(false) },
        )
    }
}
