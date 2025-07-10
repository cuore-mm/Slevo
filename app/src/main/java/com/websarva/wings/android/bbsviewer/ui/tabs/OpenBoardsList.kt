package com.websarva.wings.android.bbsviewer.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.theme.bookmarkColor
import com.websarva.wings.android.bbsviewer.ui.theme.BookmarkColor

@Composable
fun OpenBoardsList(
    modifier: Modifier = Modifier,
    openTabs: List<BoardTabInfo>,
    onCloseClick: (BoardTabInfo) -> Unit = {},
    navController: NavHostController,
    closeDrawer: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(openTabs, key = { it.boardUrl }) { tab ->
                val color = tab.bookmarkColorName?.let { bookmarkColor(it) }
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    if (color != null) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .width(8.dp)
                                .fillMaxHeight()
                                .background(color)
                        )
                    }
                    ListItem(
                        headlineContent = { Text(tab.boardName, maxLines = 1) },
                        supportingContent = { Text(tab.serviceName) },
                        trailingContent = {
                            IconButton(onClick = { onCloseClick(tab) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close)
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                closeDrawer()
                                navController.navigate(
                                    AppRoute.Board(
                                        boardId = tab.boardId,
                                        boardName = tab.boardName,
                                        boardUrl = tab.boardUrl
                                    )
                                ) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OpenBoardsListPreview() {
    val sampleBoards = listOf(
        BoardTabInfo(1, "板1", "https://example.com/board1", "example.com", bookmarkColorName = BookmarkColor.RED.value),
        BoardTabInfo(2, "板2", "https://example.com/board2", "example.com", bookmarkColorName = BookmarkColor.GREEN.value),
        BoardTabInfo(3, "板3", "https://example.com/board3", "example.com")
    )
    OpenBoardsList(
        openTabs = sampleBoards,
        onCloseClick = {},
        navController = rememberNavController(),
        closeDrawer = {}
    )
}
