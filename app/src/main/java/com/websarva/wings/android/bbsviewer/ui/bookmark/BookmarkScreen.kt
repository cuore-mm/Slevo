package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
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
    onThreadClick: (BookmarkThreadEntity) -> Unit
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
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    )
                }
        }

        // ページ本体
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val screenModifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)

            when (page) {
                0 -> BookmarkBoardScreen(
                    modifier = screenModifier,
                    boardGroups = boardGroups,
                    onBoardClick = onBoardClick,

                    )

                1 -> BookmarkThreadListScreen(
                    modifier = screenModifier,
                    groupedThreadBookmarks = threadGroups,
                    onThreadClick = onThreadClick,
                )
            }
        }
    }
}

@Composable
fun BookmarkBoardScreen(
    modifier: Modifier = Modifier,
    boardGroups: List<GroupWithBoards>,
    onBoardClick: (BoardEntity) -> Unit
) {
    val groupedDataList = boardGroups.map { gwb ->
        GroupedData(group = gwb.group, items = gwb.boards)
    }

    GenericGroupedListScreen(
        modifier = modifier,
        groupedDataList = groupedDataList,
        emptyListMessageResId = R.string.no_registered_boards,
        emptyGroupMessageResId = R.string.no_registered_boards, // グループ内が空の場合も同じメッセージ
        itemKey = { board -> board.boardId }, // BoardEntityの一意なキー
        itemContent = { board ->
            // BoardEntity を表示するためのコンポーザブル
            Text(
                text = board.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBoardClick(board) } // クリック処理
                    .padding(16.dp)
            )
        }
    )
}

@Composable
fun BookmarkThreadListScreen(
    modifier: Modifier = Modifier,
    groupedThreadBookmarks: List<GroupWithThreadBookmarks>,
    onThreadClick: (BookmarkThreadEntity) -> Unit,
) {
    val groupedDataList = groupedThreadBookmarks.map { gwtb ->
        GroupedData(group = gwtb.group, items = gwtb.threads)
    }

    GenericGroupedListScreen(
        modifier = modifier,
        groupedDataList = groupedDataList,
        emptyListMessageResId = R.string.no_bookmarked_threads,
        emptyGroupMessageResId = R.string.no_bookmarked_threads, // strings.xml に要追加
        itemKey = { thread -> thread.threadKey + thread.boardUrl }, // BookmarkThreadEntityの一意なキー
        itemContent = { thread ->
            // BookmarkThreadEntity を表示するためのコンポーザブル
            BookmarkItem( // BookmarkItem は clickable を持つので、itemContent内で直接呼び出す
                thread = thread,
                onClick = { onThreadClick(thread) }
            )
        }
    )
}

@Composable
fun BookmarkItem(
    modifier: Modifier = Modifier,
    thread: BookmarkThreadEntity,
    onClick: (BookmarkThreadEntity) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(thread) }
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

@Preview(showBackground = true)
@Composable
fun BookmarkBoardScreenPreview() {
    BookmarkBoardScreen(
        boardGroups = listOf(
            GroupWithBoards(
                group = BoardGroupEntity(
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
        onBoardClick = {}
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
        onThreadClick = {}
    )
}
