package com.websarva.wings.android.bbsviewer.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TabsScaffold(
    parentPadding: PaddingValues,
    tabsViewModel: TabsViewModel,
    navController: NavHostController
) {
    Scaffold (
        floatingActionButton = {}
    ){ innerPadding ->
        TabsPagerContent(
            modifier = Modifier.padding(
                // 左右と下は親のpadding、上は子のpaddingを使用
                start = parentPadding.calculateStartPadding(LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = parentPadding.calculateEndPadding(LayoutDirection.Ltr),
                bottom = parentPadding.calculateBottomPadding()
            ),
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = {}
        )
    }
}
