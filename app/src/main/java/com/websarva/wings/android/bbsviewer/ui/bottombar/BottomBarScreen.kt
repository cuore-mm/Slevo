package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
            route = AppRoute.ServiceList,
            name = stringResource(R.string.boardList),
            icon = Icons.AutoMirrored.Filled.List,
            parentRoute = AppRoute.BbsServiceGroup
        ),
        TopLevelRoute(
            route = AppRoute.Tabs,
            name = stringResource(R.string.tabs),
            icon = Icons.Default.Menu,
            parentRoute = AppRoute.Tabs
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
fun BookmarkSelectBottomBar(
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
fun BookmarkSelectBottomBarPreview() {
    BookmarkSelectBottomBar(
        onDelete = {},
        onOpen = {}
    )
}

