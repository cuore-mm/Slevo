package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.drawer.OpenThreadsList
import com.websarva.wings.android.bbsviewer.ui.drawer.TabsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    onDismissRequest: () -> Unit,
) {
    val openTabs by tabsViewModel.openTabs.collectAsState()
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        OpenThreadsList(
            openTabs = openTabs,
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = onDismissRequest,
        )
    }
}
