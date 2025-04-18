package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.navigation.AppRoute
import com.websarva.wings.android.bbsviewer.ui.util.checkCurrentRoute

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
            parentRoute = AppRoute.RouteName.BOOKMARK
        ),
        TopLevelRoute(
            route = AppRoute.BBSList,
            name = stringResource(R.string.boardList),
            icon = Icons.AutoMirrored.Filled.List,
            parentRoute = AppRoute.RouteName.REGISTERED_BBS
        )
    )
    NavigationBar(modifier = modifier) {
        topLevelRoutes.forEach { topLevelRoute ->
            NavigationBarItem(
                icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                label = { Text(topLevelRoute.name) },
                selected = checkCurrentRoute(
                    currentDestination = currentDestination,
                    routeNames = listOf(topLevelRoute.parentRoute)
                ),
                onClick = { onClick(topLevelRoute.route) }
            )
        }
    }
}

private data class TopLevelRoute(
    val route: AppRoute,
    val name: String,
    val icon: ImageVector,
    val parentRoute: String
)

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
    modifier: Modifier= Modifier,
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
fun BoardAppBarPreview() {
    BoardBottomBar(
        sortOptions = listOf("Option 1", "Option 2", "Option 3"),
        onSortOptionSelected = {}
    )
}
