package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: SheetState,
    tabsViewModel: TabsViewModel,
    navController: NavHostController,
    onDismissRequest: () -> Unit,
    initialPage: Int = 0,
) {
    SlevoBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        TabScreenContent(
            modifier = Modifier.fillMaxHeight(0.8f),
            tabsViewModel = tabsViewModel,
            navController = navController,
            closeDrawer = onDismissRequest, // ボトムシートを閉じる
            initialPage = initialPage
        )
    }
}
