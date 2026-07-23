package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.ui.tabs.screen.TabScreenContent
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    tabSessionStore: TabSessionStore,
    navController: NavHostController,
    onDismissRequest: () -> Unit,
    initialPage: Int = TabPage.BOARD.index,
    currentScreenRoute: AppRoute? = null,
) {
    val tabListViewModel: TabListViewModel = hiltViewModel()
    SlevoBottomSheet(
        modifier = modifier,
        onDismissRequest = {
            tabListViewModel.resetSearchState()
            onDismissRequest()
        },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        TabScreenContent(
            modifier = Modifier.fillMaxHeight(0.95f),
            tabSessionStore = tabSessionStore,
            tabListViewModel = tabListViewModel,
            navController = navController,
            closeDrawer = {
                tabListViewModel.resetSearchState()
                onDismissRequest()
            },
            initialPage = initialPage,
            currentScreenRoute = currentScreenRoute,
        )
    }
}
