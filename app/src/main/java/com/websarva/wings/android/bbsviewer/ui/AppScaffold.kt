package com.websarva.wings.android.bbsviewer.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.bbsviewer.ui.topbar.TopAppBarViewModel
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkViewModel
import com.websarva.wings.android.bbsviewer.ui.bottombar.RenderBottomBar
import com.websarva.wings.android.bbsviewer.ui.navigation.AppNavGraph
import com.websarva.wings.android.bbsviewer.ui.settings.SettingsViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.RenderTopBar

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    topAppBarViewModel: TopAppBarViewModel,
    bookmarkViewModel: BookmarkViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        topBar = {
            RenderTopBar(
                navController = navController,
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
            settingsViewModel = settingsViewModel
        )
    }
}
