package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.util.isInRoute

@Composable
fun HomeBottomNavigationBar(
    currentDestination: NavDestination?,
    onClick: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevelRoutes = listOf(
        TopLevelRoute(
            route = AppRoute.Bookmark,
            name = stringResource(R.string.bookmark),
            icon = Icons.Default.Favorite,
            parentRoute = AppRoute.Bookmark
        ),
        TopLevelRoute(
            route = AppRoute.BBSList,
            name = stringResource(R.string.boardList),
            icon = Icons.AutoMirrored.Filled.List,
            parentRoute = AppRoute.RegisteredBBS
        ),
        TopLevelRoute(
            route = AppRoute.Settings,
            name = stringResource(R.string.settings),
            icon = Icons.Default.Settings,
            parentRoute = AppRoute.Settings
        ),
    )
    NavigationBar(modifier = modifier) {
        topLevelRoutes.forEach { item ->
            NavigationBarItem(
                icon = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(item.icon, contentDescription = item.name)
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                },
                label = null, // labelはnullにしてicon内で表示
                alwaysShowLabel = true,
                selected = currentDestination?.hierarchy?.any {
                    it.hasRoute(item.parentRoute::class)
                } ?: false,
                onClick = { onClick(item.route) }
            )
        }
    }
}

private data class TopLevelRoute(
    val route: AppRoute,
    val name: String,
    val icon: ImageVector,
    val parentRoute: AppRoute
)

@Composable
fun BbsSelectBottomBar(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    BottomAppBar(
        modifier = Modifier.height(56.dp),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                BottomBarItem(
                    icon = Icons.Default.Delete,
                    label = "削除",
                    onClick = onDelete
                )
                BottomBarItem(
                    icon = Icons.Default.OpenInBrowser,
                    label = "開く",
                    onClick = onOpen
                )
            }
        }
    )
}

@Composable
fun BottomBarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
fun BoardBottomBar(
    sortOptions: List<String>,
    onSortOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    BottomAppBar(
        modifier = Modifier.height(56.dp),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box {
                    // IconButtonをクリックするとメニューが展開される
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.sort)
                        )
                    }
                    // DropdownMenuで選択肢を表示
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    expanded = false
                                    onSortOptionSelected(option)
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = stringResource(R.string.home)
                    )
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
                    )
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.Default.Create,
                        contentDescription = stringResource(R.string.create_thread)
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = stringResource(R.string.more)
                    )
                }
            }

        }
    )
}

@Composable
fun ThreadBottomBar(
    modifier: Modifier = Modifier,
    onPostClick: () -> Unit
) {
    var dialogVisible by remember { mutableStateOf(false) }
    BottomAppBar(
        modifier = Modifier.height(56.dp),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = stringResource(R.string.home)
                    )
                }
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
                    )
                }
                IconButton(onClick = onPostClick) {
                    Icon(
                        Icons.Default.Create,
                        contentDescription = stringResource(R.string.post)
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = stringResource(R.string.more)
                    )
                }
            }

        }
    )
}

@Preview(showBackground = true)
@Composable
fun HomeBottomNavigationBarPreview() {
    HomeBottomNavigationBar(
        modifier = Modifier
            .height(56.dp),
        currentDestination = null,
        onClick = {}
    )
}

@Preview(showBackground = true)
@Composable
fun BbsSelectBottomBarPreview() {
    BbsSelectBottomBar(
        onDelete = {},
        onOpen = {}
    )
}

@Preview(showBackground = true)
@Composable
fun BoardBottomBarPreview() {
    BoardBottomBar(
        sortOptions = listOf("Option 1", "Option 2", "Option 3"),
        onSortOptionSelected = {}
    )
}

@Preview(showBackground = true)
@Composable
fun ThreadBottomBarPreview() {
    ThreadBottomBar(
        onPostClick = {}
    )
}
