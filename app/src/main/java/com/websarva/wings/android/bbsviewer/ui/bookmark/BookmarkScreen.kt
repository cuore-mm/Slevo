package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithThreadBookmarks
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.model.GroupedData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    boardGroups: List<GroupWithBoards>,
    onBoardClick: (BoardEntity) -> Unit,
    threadGroups: List<GroupWithThreadBookmarks>,
    onThreadClick: (BookmarkThreadEntity) -> Unit,
    selectMode: Boolean,
    selectedBoardIds: Set<Long>,
    selectedThreadIds: Set<String>,
    onBoardLongClick: (Long) -> Unit,
    onThreadLongClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()


    // 0: 板一覧, 1: スレッド一覧
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )

    Column(modifier = modifier.fillMaxSize()) {
        // タブ行
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(stringResource(R.string.board), stringResource(R.string.thread))
                .forEachIndexed { index, text ->
                    Tab(
                        text = { Text(text) },
                        selected = pagerState.currentPage == index,
                        enabled = !selectMode,
                        onClick = {
                            if (!selectMode) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        }
                    )
                }
        }

        // ページ本体
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = !selectMode
        ) { page ->
            val screenModifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)

            when (page) {
                0 -> BookmarkBoardScreen(
                    modifier = screenModifier,
                    boardGroups = boardGroups,
                    onBoardClick = onBoardClick,
                    selectMode = selectMode,
                    selectedBoardIds = selectedBoardIds,
                    onLongClick = onBoardLongClick,
                )

                1 -> BookmarkThreadListScreen(
                    modifier = screenModifier,
                    groupedThreadBookmarks = threadGroups,
                    onThreadClick = onThreadClick,
                    selectMode = selectMode,
                    selectedThreadIds = selectedThreadIds,
                    onLongClick = onThreadLongClick,
                )
            }
        }
    }
}

@Composable
fun BookmarkBoardScreen(
    modifier: Modifier = Modifier,
    boardGroups: List<GroupWithBoards>,
    onBoardClick: (BoardEntity) -> Unit,
    selectMode: Boolean,
    selectedBoardIds: Set<Long>,
    onLongClick: (Long) -> Unit
) {
    val groupedDataList = boardGroups.map { gwb ->
        GroupedData(group = gwb.group, items = gwb.boards)
    }

    GenericGroupedListScreen(
        modifier = modifier,
        groupedDataList = groupedDataList,
        emptyListMessageResId = R.string.no_registered_boards,
        emptyGroupMessageResId = R.string.no_registered_boards,
        itemKey = { board -> board.boardId },
        itemContent = { board ->
            val selected = board.boardId in selectedBoardIds
            BookmarkBoardItem(
                board = board,
                selected = selected,
                selectMode = selectMode,
                onClick = { onBoardClick(board) },
                onLongClick = { onLongClick(board.boardId) }
            )
        }
    )
}

@Composable
fun BookmarkThreadListScreen(
    modifier: Modifier = Modifier,
    groupedThreadBookmarks: List<GroupWithThreadBookmarks>,
    onThreadClick: (BookmarkThreadEntity) -> Unit,
    selectMode: Boolean,
    selectedThreadIds: Set<String>,
    onLongClick: (String) -> Unit,
) {
    val groupedDataList = groupedThreadBookmarks.map { gwtb ->
        GroupedData(group = gwtb.group, items = gwtb.threads)
    }

    GenericGroupedListScreen(
        modifier = modifier,
        groupedDataList = groupedDataList,
        emptyListMessageResId = R.string.no_bookmarked_threads,
        emptyGroupMessageResId = R.string.no_bookmarked_threads,
        itemKey = { thread -> thread.threadKey + thread.boardUrl },
        itemContent = { thread ->
            val id = thread.threadKey + thread.boardUrl
            val selected = id in selectedThreadIds
            BookmarkThreadItem(
                thread = thread,
                selected = selected,
                selectMode = selectMode,
                onClick = { onThreadClick(thread) },
                onLongClick = { onLongClick(id) }
            )
        }
    )
}

@Composable
fun BookmarkThreadItem(
    modifier: Modifier = Modifier,
    thread: BookmarkThreadEntity,
    selected: Boolean,
    selectMode: Boolean,
    onClick: (BookmarkThreadEntity) -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!selectMode) {
                        onClick(thread)
                    } else {
                        onLongClick()
                    }
                },
                onLongClick = { if (!selectMode) onLongClick() }
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainer
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                Text(
                    text = "${thread.boardName} ${thread.resCount}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun BookmarkBoardItem(
    board: BoardEntity,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    if (!selectMode) {
                        onClick()
                    } else {
                        onLongClick()
                    }
                },
                onLongClick = { if (!selectMode) onLongClick() }
            ),
        headlineContent = {
            Text(
                text = board.name,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    )
}

@Preview(showBackground = true)
@Composable
fun BookmarkBoardScreenPreview() {
    BookmarkBoardScreen(
        boardGroups = listOf(
            GroupWithBoards(
                group = BoardBookmarkGroupEntity(
                    name = "グループ1",
                    colorHex = "#FF0E00",
                    sortOrder = 1
                ),
                boards = listOf(
                    BoardEntity(
                        serviceId = 2,
                        name = "なんでも実況J",
                        url = "https://example.com/board1"
                    ),
                    BoardEntity(
                        serviceId = 3,
                        name = "エッヂ",
                        url = "https://example.com/board2"
                    )
                )
            )
        ),
        onBoardClick = {},
        selectMode = false,
        selectedBoardIds = emptySet(),
        onLongClick = {}
    )
}

@Preview(showBackground = true)
@Composable
fun BookmarkThreadListScreenPreview() {
    // サンプルのグループとスレッド
    val groups = listOf(
        GroupWithThreadBookmarks(
            group = ThreadBookmarkGroupEntity(
                groupId = 0,
                name = "お気に入りグループA",
                colorHex = "#FF9800",
                sortOrder = 0
            ),
            threads = listOf(
                BookmarkThreadEntity(
                    threadKey = "key1",
                    boardUrl = "https://example.com/board1",
                    title = "スレッド1",
                    boardName = "板1",
                    resCount = 123,
                    boardId = 1,
                    groupId = 1 // グループIDを設定
                ),
                BookmarkThreadEntity(
                    threadKey = "key2",
                    boardUrl = "https://example.com/board1",
                    title = "スレッド2",
                    boardName = "板1",
                    resCount = 45,
                    boardId = 1,
                    groupId = 1 // グループIDを設定
                )
            )
        ),
        GroupWithThreadBookmarks(
            group = ThreadBookmarkGroupEntity(
                groupId = 1,
                name = "お気に入りグループB",
                colorHex = "#4CAF50",
                sortOrder = 1
            ),
            threads = emptyList()
        )
    )

    BookmarkThreadListScreen(
        groupedThreadBookmarks = groups,
        onThreadClick = {},
        selectMode = false,
        selectedThreadIds = emptySet(),
        onLongClick = {}
    )
}
