package com.websarva.wings.android.bbsviewer.ui.bbslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun BBSListScreen(
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    val bbses = listOf("5ch", "エッヂ", "ポケモンBBS")
    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        items(bbses) { bbs ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable(onClick = { onClick(bbs) }),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = bbs,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BoardCategoryList(
    modifier: Modifier = Modifier,
    categories: List<Category>,
    onCategoryClick: (Category) -> Unit
) {
    LazyVerticalGrid(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        columns = GridCells.Fixed(2)
    ) {
        items(categories) { category ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(category) },
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null
                    )
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun CategorisedBoardListScreen(
    modifier: Modifier = Modifier,
    boards: List<Board>,
    onBoardClick: (Board) -> Unit
) {
    LazyVerticalGrid(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        columns = GridCells.Fixed(2)
    ) {
        items(boards) { board ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBoardClick(board) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = board.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBoardCategoryList() {
    val sampleBoards = listOf(
        Board(name = "なんJ", url = "https://example.com/thread_list")
    )
    val sampleCategories = listOf(
        Category(name = "カテゴリー1", boards = sampleBoards),
        Category(name = "カテゴリー2", boards = sampleBoards),
        Category(name = "カテゴリー3", boards = sampleBoards),
        Category(name = "カテゴリー4", boards = sampleBoards),
        Category(name = "カテゴリー5", boards = sampleBoards),
    )
    BoardCategoryList(
        categories = sampleCategories,
        onCategoryClick = {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewCategorisedBoardListScreen() {
    val sampleBoards = listOf(
        Board(name = "board1", url = "https://example.com/thread_list"),
        Board(name = "board2", url = "https://example.com/thread_list"),
        Board(name = "board3", url = "https://example.com/thread_list"),
        Board(name = "board4", url = "https://example.com/thread_list"),
        Board(name = "board5", url = "https://example.com/thread_list"),
        Board(name = "board6", url = "https://example.com/thread_list"),
    )
    CategorisedBoardListScreen(
        boards = sampleBoards,
        onBoardClick = {}
    )
}
