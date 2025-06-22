package com.websarva.wings.android.bbsviewer.ui.tabs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@Composable
fun TabsScaffold(
    modifier: Modifier = Modifier,
    tabsViewModel: TabsViewModel,
    navController: NavHostController
) {
    val openTabs by tabsViewModel.openTabs.collectAsState()

    Scaffold { innerPadding ->
        OpenThreadsList(
            modifier = modifier.padding(innerPadding),
            openTabs = openTabs,
            onCloseClick = { tab ->
                tabsViewModel.closeThread(tab)
            },
            navController = navController,
            closeDrawer = {},
        )
    }
}
