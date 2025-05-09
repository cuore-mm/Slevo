package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import kotlinx.coroutines.launch

@Composable
fun BookmarkScreen(
    modifier: Modifier = Modifier,
    bookmarks: List<BookmarkThreadEntity>,
    onItemClick: (BookmarkThreadEntity) -> Unit
) {
    val scope = rememberCoroutineScope()

    // 0: 板一覧, 1: スレッド一覧
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }      // ← 必須で渡す
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
                    bookmarks = bookmarks,
                    onItemClick = onItemClick,
                    modifier = Modifier.fillMaxSize()
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
    bookmarks: List<BookmarkThreadEntity>,
    onItemClick: (BookmarkThreadEntity) -> Unit
) {

    if (bookmarks.isEmpty()) {
        // お気に入りがない場合の表示
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "お気に入りがありません")
        }
    } else {
        // お気に入り一覧の表示
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
        ) {
            items(bookmarks) { bookmark ->
                BookmarkItem(
                    bookmark = bookmark,
                    onClick = { onItemClick(bookmark) }
                )
            }
        }
    }
}

@Composable
fun BookmarkItem(
    modifier: Modifier = Modifier,
    bookmark: BookmarkThreadEntity,
    onClick: (BookmarkThreadEntity) -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(bookmark) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                Text(
                    text = "${bookmark.boardName} ${bookmark.resCount}",
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
fun BookmarkItemPreview() {
    BookmarkItem(
        bookmark = BookmarkThreadEntity(
            threadUrl = "https://example.com/test/read.cgi/example/1234567890",
            title = "スレッドタイトル",
            boardName = "example板",
            resCount = 100
        ),
        onClick = {}
    )
}
