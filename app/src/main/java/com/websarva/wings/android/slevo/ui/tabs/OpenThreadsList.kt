package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 開いているスレッドタブの一覧をカード表示し、選択されたタブへ遷移する。
 */
@Composable
fun OpenThreadsList(
    modifier: Modifier = Modifier,
    openTabs: List<ThreadTabInfo>,
    onCloseClick: (ThreadTabInfo) -> Unit = {},
    navController: NavHostController,
    closeDrawer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    newResCounts: Map<String, Int> = emptyMap(),
    onItemClick: (ThreadTabInfo) -> Unit = {},
    tabsViewModel: TabsViewModel? = null,
) {
    val removingTabs = remember { mutableStateMapOf<String, Boolean>() }
    val coroutineScope = rememberCoroutineScope()

    // --- List ---
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(openTabs, key = { it.id.value }) { tab ->
            val isRemoving = removingTabs[tab.id.value] == true
            AnimatedVisibility(
                visible = !isRemoving,
                exit = shrinkVertically(animationSpec = tween(180)) +
                    fadeOut(animationSpec = tween(180)),
            ) {
                OpenThreadCard(
                    tab = tab,
                    newResCount = newResCounts[tab.id.value] ?: 0,
                    onClick = {
                        if (isRemoving) return@OpenThreadCard
                        closeDrawer()
                        onItemClick(tab)
                        val route = AppRoute.Thread(
                            threadKey = tab.threadKey,
                            boardUrl = tab.boardUrl,
                            boardName = tab.boardName,
                            boardId = tab.boardId,
                            threadTitle = tab.title,
                            resCount = tab.resCount
                        )
                        navController.navigateToThread(
                            route = route,
                            tabsViewModel = tabsViewModel,
                        ) {
                            restoreState = true
                        }
                    },
                    onCloseClick = {
                        if (isRemoving) return@OpenThreadCard
                        removingTabs[tab.id.value] = true
                        coroutineScope.launch {
                            delay(180)
                            onCloseClick(tab)
                            removingTabs.remove(tab.id.value)
                        }
                    },
                )
            }
        }
    }
}

/**
 * スレッドタブをカード表示する。
 */
@Composable
private fun OpenThreadCard(
    tab: ThreadTabInfo,
    newResCount: Int,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    // --- Card highlight ---
    val color = tab.bookmarkColorName?.let { bookmarkColor(it) }

    TabListCard(
        modifier = Modifier.padding(horizontal = 12.dp),
        accentColor = color,
        onClick = onClick,
        headerTitle = tab.boardName,
        headerTrailingContent = {
            Text(
                text = tab.resCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (newResCount > 0) {
                // 新着バッジは板画面の強調スタイルに合わせる。
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+$newResCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        },
        bodyTitle = tab.title,
        onCloseClick = {
            // タブクローズ操作は一覧遷移より優先して処理する。
            onCloseClick()
        },
    )
}

@Preview(showBackground = true)
@Composable
fun OpenThreadsListPreview() {
    val sampleTabs = listOf(
        ThreadTabInfo(
            ThreadId.of("example.com", "board1", "1"),
            "スレッド1",
            "板1",
            "https://example.com/board1",
            1,
            100,
            bookmarkColorName = BookmarkColor.RED.value
        ),
        ThreadTabInfo(
            ThreadId.of("example.com", "board2", "2"),
            "スレッド2",
            "板2",
            "https://example.com/board2",
            2,
            200,
            bookmarkColorName = BookmarkColor.GREEN.value
        ),
        ThreadTabInfo(
            ThreadId.of("example.com", "board3", "3"),
            "スレッド3",
            "板3",
            "https://example.com/board3",
            3,
            300
        )
    )
    OpenThreadsList(
        openTabs = sampleTabs,
        onCloseClick = {},
        navController = rememberNavController(),
        closeDrawer = {},
        contentPadding = PaddingValues(0.dp),
        newResCounts = emptyMap(),
        onItemClick = {}
    )
}
