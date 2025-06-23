package com.websarva.wings.android.bbsviewer.ui.tabs

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsPagerContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        TabsPagerContent(
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = onDismissRequest
        )
    }
}
