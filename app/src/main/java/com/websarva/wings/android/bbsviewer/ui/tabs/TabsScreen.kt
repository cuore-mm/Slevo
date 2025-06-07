package com.websarva.wings.android.bbsviewer.ui.tabs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.drawer.OpenThreadsList
import com.websarva.wings.android.bbsviewer.ui.drawer.TabsViewModel
import com.websarva.wings.android.bbsviewer.ui.topbar.HomeTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    modifier: Modifier = Modifier,
    tabsViewModel: TabsViewModel,
    navController: NavHostController
) {
    val openTabs by tabsViewModel.openTabs.collectAsState()

    Scaffold(
        topBar = {
            HomeTopAppBarScreen(
                title = stringResource(R.string.tabs)
            )
        }
    ) { innerPadding ->
        OpenThreadsList(
            openTabs = openTabs,
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = {},
        )
    }
}
