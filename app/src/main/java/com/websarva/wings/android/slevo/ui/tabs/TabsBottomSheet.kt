package com.websarva.wings.android.slevo.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    tabListViewModel: TabListViewModel,
    navController: NavHostController,
    onDismissRequest: () -> Unit,
    initialPage: Int = 0,
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        TabScreenContent(
            modifier = Modifier.fillMaxHeight(0.8f),
            tabListViewModel = tabListViewModel,
            navController = navController,
            closeDrawer = onDismissRequest, // ボトムシートを閉じる
            initialPage = initialPage
        )
    }
}
