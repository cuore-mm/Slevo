package com.websarva.wings.android.slevo.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.ui.bottombar.RenderBottomBar
import com.websarva.wings.android.slevo.ui.navigation.AppNavGraph
import com.websarva.wings.android.slevo.ui.settings.SettingsViewModel
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    settingsViewModel: SettingsViewModel,
    tabsViewModel: TabsViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    /* ① 共有する TopAppBarState を用意 */
    val topBarState = rememberTopAppBarState()

    /* ② BottomBar の高さ(px) を取得しておく */
    val density = LocalDensity.current
    val bottomBarHeightDp = 56.dp
    val bottomBarHeightPx = with(density) { bottomBarHeightDp.toPx() }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    // 画面遷移ごとにheightOffsetをリセット
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        topBarState.heightOffset = 0f
    }

    Scaffold(
//            modifier = modifier
//                .fillMaxSize()
//                .nestedScroll(scrollBehavior.nestedScrollConnection),
        bottomBar = {
            val offsetY = (-topBarState.heightOffset)        // 符号反転
                .coerceIn(0f, bottomBarHeightPx)             // 0 … BottomBarH

            RenderBottomBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(bottomBarHeightDp),
//                    .offset { IntOffset(0, offsetY.roundToInt()) },
                navController = navController,
                navBackStackEntry = navBackStackEntry,
            )
        }
    ) { innerPadding ->

        AppNavGraph(
            parentPadding = innerPadding,
            navController = navController,
            topBarState = topBarState,
            settingsViewModel = settingsViewModel,
            openDrawer = openDrawer,
            tabsViewModel = tabsViewModel
        )
    }

}
