package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.AppRoute

@Composable
fun HomeBottomNavigationBar(
    navController: NavHostController,
    onClick: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
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
        )
    )
    NavigationBar(modifier = modifier) {
        topLevelRoutes.forEach { topLevelRoute ->
            NavigationBarItem(
                icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                label = { Text(topLevelRoute.name) },
                selected = currentDestination
                    ?.hierarchy
                    ?.any { it.route == topLevelRoute.parentRoute::class.qualifiedName } == true,
                onClick = { onClick(topLevelRoute.route) }
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
fun BoardAppBar(
) {
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
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.sort)
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
    BoardAppBar()
}
