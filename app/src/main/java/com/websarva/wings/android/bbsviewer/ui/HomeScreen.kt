package com.websarva.wings.android.bbsviewer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.theme.BBSViewerTheme

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            HomeBottomNavigationBar(
                navController = navController,
                onClick = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeList.Bookmark.name,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = HomeList.Bookmark.name) {
                ThreadFetcherScreen()
            }
            composable(route = HomeList.BoardList.name) {
                BoardListScreen()
            }
        }

    }
}

@Composable
fun ThreadFetcherScreen(
    threadViewModel: ThreadViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val threadUiState by threadViewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        ThreadUrlInput(
            url = threadViewModel.enteredUrl,
            onValueChange = { threadViewModel.updateTextField(it) },
            onUrlEntered = { threadViewModel.parseUrl() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // DATデータの表示
        threadUiState.posts?.let {
            if (it.isEmpty()) {
                Text("スレッドが見つかりません")
            } else {
                ThreadScreen(posts = it)
            }
        }
    }
}

@Composable
fun ThreadUrlInput(
    url: String,
    onValueChange: (String) -> Unit,
    onUrlEntered: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        TextField(
            value = url,
            onValueChange = onValueChange,
            label = { Text("5chスレのURLを入力") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onUrlEntered,
            enabled = url.isNotBlank()
        ) {
            Text("取得")
        }
    }
}

@Composable
fun ThreadScreen(posts: List<ThreadPost>) {
    LazyColumn {
        items(posts) { post ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = "名前: ${post.name}", fontWeight = FontWeight.Bold)
                    Text(text = "日時: ${post.date}", fontSize = 12.sp, color = Color.Gray)
                    Text(text = post.content, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeBottomNavigationBar(
    navController: NavHostController,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val topLevelRoutes = listOf(
        TopLevelRoute(
            route = HomeList.Bookmark.name,
            name = stringResource(R.string.bookmark),
            icon = Icons.Default.Favorite
        ),
        TopLevelRoute(
            route = HomeList.BoardList.name,
            name = stringResource(R.string.boardList),
            icon = Icons.AutoMirrored.Filled.List
        )
    )
    NavigationBar {
        topLevelRoutes.forEach { topLevelRoute ->
            NavigationBarItem(
                icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                label = { Text(topLevelRoute.name) },
                selected = currentDestination?.hierarchy?.any { it.route == topLevelRoute.route } == true,
                onClick = { onClick(topLevelRoute.route) }
            )
        }
    }
}

@Composable
private fun BoardListScreen() {
    Text(text = "5ch")
}

enum class HomeList() {
    Bookmark,
    BoardList
}

private data class TopLevelRoute(
    val route: String,
    val name: String,
    val icon: ImageVector
)

@Preview(showBackground = true, backgroundColor = 0xFFF5F0EE)
@Composable
fun SearchBarPreview() {
    BBSViewerTheme { ThreadFetcherScreen() }
}

@Preview(showBackground = true)
@Composable
fun ThreadFetcherScreenPreview() {
    ThreadFetcherScreen()
}