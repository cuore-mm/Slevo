package com.websarva.wings.android.bbsviewer.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TabsScaffold(
    parentPadding: PaddingValues,
    tabsViewModel: TabsViewModel,
    navController: NavHostController
) {
    TabScreenContent(
        modifier = Modifier.padding(parentPadding),
        tabsViewModel = tabsViewModel,
        navController = navController,
        closeDrawer = {} // Scaffoldの場合は何もしない
    )
}
