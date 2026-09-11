package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.ui.tabs.screen.TabScreenContent
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore

/** タブ一覧画面へルート下部 chrome の占有領域を渡す Scaffold ラッパー。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TabsScaffold(
    appChromePadding: PaddingValues,
    tabSessionStore: TabSessionStore,
    navController: NavHostController,
    sourceRoute: AppRoute? = null,
    tabsEntryId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val lastPage by tabSessionStore.lastSelectedTabsPage.collectAsState(initial = TabPage.BOARD.index)
    val tabListViewModel: TabListViewModel = hiltViewModel()
    TabScreenContent(
        modifier = Modifier,
        appChromePadding = appChromePadding,
        tabSessionStore = tabSessionStore,
        tabListViewModel = tabListViewModel,
        navController = navController,
        initialPage = deriveTabsInitialPage(sourceRoute, lastPage),
        onPageChanged = { tabSessionStore.setLastSelectedTabsPage(it) },
        sourceRoute = sourceRoute,
        tabsEntryId = tabsEntryId,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

/**
 * Tabsを開いた画面種別に応じて初期表示ページを決める。
 *
 * Board / Thread起点のTabsでは遷移元種別を優先し、それ以外では最後に選択したページを復元する。
 */
internal fun deriveTabsInitialPage(sourceRoute: AppRoute?, lastPage: Int): Int = when (sourceRoute) {
    is AppRoute.Board -> TabPage.BOARD.index
    is AppRoute.Thread -> TabPage.THREAD.index
    else -> lastPage
}
