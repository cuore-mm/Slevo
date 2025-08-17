package com.websarva.wings.android.bbsviewer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.NgType
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.bbsviewer.ui.topbar.SmallTopAppBarScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNgScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsNgViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf(
        NgType.USER_ID,
        NgType.USER_NAME,
        NgType.WORD,
        NgType.THREAD_TITLE,
    )
    val selectedIndex = tabs.indexOf(uiState.selectedTab)

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
            TabRow(selectedTabIndex = selectedIndex) {
                tabs.forEachIndexed { index, type ->
                    val labelRes = when (type) {
                        NgType.USER_ID -> R.string.id_label
                        NgType.USER_NAME -> R.string.name_label
                        NgType.WORD -> R.string.word_label
                        NgType.THREAD_TITLE -> R.string.thread_title_label
                    }
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { viewModel.selectTab(type) },
                        text = { Text(stringResource(labelRes)) },
                    )
                }
            }
            val filtered = uiState.ngs.filter { it.type == uiState.selectedTab }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { ng ->
                    ListItem(
                        headlineContent = { Text(ng.pattern) },
                        modifier = Modifier.clickable { viewModel.startEdit(ng) },
                    )
                    HorizontalDivider()
                }
            }
            uiState.editingNg?.let { ng ->
                NgDialogRoute(
                    id = ng.id,
                    text = ng.pattern,
                    type = ng.type,
                    boardId = ng.boardId,
                    isRegex = ng.isRegex,
                    onDismiss = { viewModel.endEdit() },
                )
            }
        }
    }
}

