package com.websarva.wings.android.slevo.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TabsScaffold(
    parentPadding: PaddingValues,
    tabsViewModel: TabsViewModel,
    navController: NavHostController
) {
    val lastPage by tabsViewModel.lastSelectedPage.collectAsState()
    TabScreenContent(
        modifier = Modifier.padding(parentPadding),
        tabsViewModel = tabsViewModel,
        navController = navController,
        closeDrawer = {}, // Scaffoldの場合は何もしない
        initialPage = lastPage,
        onPageChanged = { tabsViewModel.setLastSelectedPage(it) }
    )
}
