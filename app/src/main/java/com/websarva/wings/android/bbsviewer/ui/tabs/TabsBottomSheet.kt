package com.websarva.wings.android.bbsviewer.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.websarva.wings.android.bbsviewer.ui.tabs.TabsPagerContent

@RequiresApi(Build.VERSION_CODES.O)
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
        Scaffold (
            floatingActionButton = {}
        ){ innerPadding ->
            TabsPagerContent(
                tabsViewModel = tabsViewModel,
                navController = navController,
                closeDrawer = onDismissRequest
            )
        }
    }
}
