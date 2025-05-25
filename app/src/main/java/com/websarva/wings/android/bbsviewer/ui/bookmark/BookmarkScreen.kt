package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    boardGroups: List<GroupWithBoards>,
    onBoardClick: (BoardEntity) -> Unit
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
            when (page) {
                0 -> BookmarkBoardScreen(
                    boardGroups = boardGroups,
                    onBoardClick = onBoardClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                )

                1 -> ThreadList(
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

    if (boardGroups.isEmpty()) {
        // お気に入りがない場合の表示
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.no_registered_boards))
        }
    } else {
        // お気に入り一覧の表示
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
        ) {
            boardGroups.forEach { gwb ->
                // ────────────── グループ名 ──────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween // グループ名と板数を両端に配置
                    ) {
                        Text(
                            text = gwb.group.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            // modifier = Modifier.weight(1f) // SpaceBetween を使うので weight は不要な場合あり
                        )
                        Text(
                            text = "(${gwb.boards.size})", // 板数を表示
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Normal // 板数は通常の太さにするなど調整可能
                        )
                    }
                }

                // ────────────── カードで囲む ──────────────
                item {
                    // カラーコード文字列を Color に変換
                    val groupColor = Color(gwb.group.colorHex.toColorInt())

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min)
                        ) {
                            // ← 左端のカラーバー
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight()
                                    .background(groupColor)
                            )
                            // ← ボード一覧
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (gwb.boards.isEmpty()) { // 板がない場合の表示
                                    Text(
                                        text = stringResource(R.string.no_registered_boards),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    gwb.boards.forEachIndexed { index, board ->
                                        Text(
                                            text = board.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onBoardClick(board) }
                                                .padding(16.dp)
                                        )
                                        if (index < gwb.boards.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.12f
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkItem(
    modifier: Modifier = Modifier,
    thread: BookmarkThreadEntity,
    onClick: (BookmarkThreadEntity) -> Unit
) {
    Card(
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

@Composable
fun ThreadList() {
    Text(text = "スレッド一覧")
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
fun BookmarkItemPreview() {
    BookmarkItem(
        thread = BookmarkThreadEntity(
            threadUrl = "https://example.com/test/read.cgi/example/1234567890",
            title = "スレッドタイトル",
            boardName = "example板",
            resCount = 100
        ),
        onClick = {}
    )
}
