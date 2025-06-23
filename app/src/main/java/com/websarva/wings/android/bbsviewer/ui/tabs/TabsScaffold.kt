package com.websarva.wings.android.bbsviewer.ui.tabs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsPagerContent

@Composable
fun TabsScaffold(
    modifier: Modifier = Modifier,
    tabsViewModel: TabsViewModel,
    navController: NavHostController
) {
    Scaffold { innerPadding ->
        TabsPagerContent(
            modifier = modifier.padding(innerPadding),
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = {}
        )
    }
}
