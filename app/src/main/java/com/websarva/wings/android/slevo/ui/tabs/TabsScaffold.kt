package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.ui.tabs.screen.TabScreenContent
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore

/** タブ一覧画面へルート下部 chrome の占有領域を渡す Scaffold ラッパー。 */
@Composable
fun TabsScaffold(
    appChromePadding: PaddingValues,
    tabSessionStore: TabSessionStore,
    navController: NavHostController
) {
    val lastPage by tabSessionStore.lastSelectedTabsPage.collectAsState(initial = TabPage.BOARD.index)
    val tabListViewModel: TabListViewModel = hiltViewModel()
    TabScreenContent(
        modifier = Modifier,
        appChromePadding = appChromePadding,
        tabSessionStore = tabSessionStore,
        tabListViewModel = tabListViewModel,
        navController = navController,
        closeDrawer = {}, // Scaffoldの場合は何もしない
        initialPage = lastPage,
        onPageChanged = { tabSessionStore.setLastSelectedTabsPage(it) },
        currentScreenRoute = null,
    )
}
