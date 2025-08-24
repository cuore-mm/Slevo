package com.websarva.wings.android.slevo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.bottombar.BbsSelectBottomBar
import com.websarva.wings.android.slevo.ui.common.SelectedTopBarScreen
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.topbar.SmallTopAppBarScreen

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
            Box {
                AnimatedVisibility(
                    visible = !uiState.selectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SmallTopAppBarScreen(
                        title = "NG",
                        onNavigateUp = onNavigateUp,
                    )
                }
                AnimatedVisibility(
                    visible = uiState.selectMode,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    SelectedTopBarScreen(
                        onBack = { viewModel.toggleSelectMode(false) },
                        selectedCount = uiState.selected.size
                    )
                }
            }
        },
        bottomBar = {
            if (uiState.selectMode) {
                BbsSelectBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    onDelete = { viewModel.removeSelected() },
                    onOpen = {}
                )
            }
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
                    val isSelected = ng.id in uiState.selected
                    ListItem(
                        headlineContent = { Text(ng.pattern) },
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    if (uiState.selectMode) {
                                        viewModel.toggleSelect(ng.id)
                                    } else {
                                        viewModel.startEdit(ng)
                                    }
                                },
                                onLongClick = {
                                    if (!uiState.selectMode) {
                                        viewModel.toggleSelectMode(true)
                                        viewModel.toggleSelect(ng.id)
                                    }
                                }
                            ),
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    )
                    HorizontalDivider()
                }
            }
            uiState.editingNg?.let { ng ->
                NgDialogRoute(
                    id = ng.id,
                    text = ng.pattern,
                    type = ng.type,
                    boardName = ng.boardId?.let { uiState.boardNames[it] } ?: "",
                    boardId = ng.boardId,
                    isRegex = ng.isRegex,
                    onDismiss = { viewModel.endEdit() },
                )
            }
            BackHandler(enabled = uiState.selectMode) {
                viewModel.toggleSelectMode(false)
            }
        }
    }
}

