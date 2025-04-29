package com.websarva.wings.android.bbsviewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.ui.topbar.HomeTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    Scaffold(
        topBar = {
            HomeTopAppBarScreen(
                title = "設定",
                modifier = Modifier,
                scrollBehavior = null
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 「ダークテーマ」スイッチ
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ダークテーマ",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isDark,
                    onCheckedChange = { onToggleTheme() }
                )
            }
            HorizontalDivider()
            // → 他の設定項目も同様に Row＋Switch/TextField 等で追加
        }
    }

}
