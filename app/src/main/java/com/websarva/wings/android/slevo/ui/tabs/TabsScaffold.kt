package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.tabs.screen.TabScreenContent
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore

@Composable
fun TabsScaffold(
    parentPadding: PaddingValues,
    tabSessionStore: TabSessionStore,
    navController: NavHostController
) {
    val lastPage by tabSessionStore.lastSelectedTabsPage.collectAsState(initial = 0)
    val tabListViewModel: TabListViewModel = hiltViewModel()
    TabScreenContent(
        modifier = Modifier.padding(parentPadding),
        tabSessionStore = tabSessionStore,
        tabListViewModel = tabListViewModel,
        navController = navController,
        closeDrawer = {}, // Scaffoldの場合は何もしない
        initialPage = lastPage,
        onPageChanged = { tabSessionStore.setLastSelectedTabsPage(it) },
        currentScreenRoute = null,
    )
}
