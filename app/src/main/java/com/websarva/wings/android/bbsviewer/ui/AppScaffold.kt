package com.websarva.wings.android.bbsviewer.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.bottombar.RenderBottomBar
import com.websarva.wings.android.bbsviewer.ui.navigation.AppNavGraph
import com.websarva.wings.android.bbsviewer.ui.topbar.RenderTopBar

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    topAppBarViewModel: TopAppBarViewModel,
    bookmarkViewModel: BookmarkViewModel,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // 画面遷移が発生するたびに呼ばれ、スクロール位置をリセットする
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        // 表示をリセット
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RenderTopBar(
                navController = navController,
                scrollBehavior = scrollBehavior,
                topAppBarViewModel = topAppBarViewModel,
                navBackStackEntry = navBackStackEntry,
            )
        },
        bottomBar = {
            RenderBottomBar(
                navController = navController,
                navBackStackEntry = navBackStackEntry,
            )
        }
    ) { innerPadding ->
        AppNavGraph(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            topAppBarViewModel = topAppBarViewModel,
            bookmarkViewModel = bookmarkViewModel,
        )
    }
}
