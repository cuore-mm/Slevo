package com.websarva.wings.android.bbsviewer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.ui.topbar.SmallTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNgScreen(
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBarScreen(
                title = "NG",
                onNavigateUp = onNavigateUp,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Text(
                text = "NG設定",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

