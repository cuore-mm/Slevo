package com.websarva.wings.android.slevo.ui.bottombar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tab
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
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.navigation.AppRoute

@Composable
fun NavigationBottomBar(
    currentDestination: NavDestination?,
    onClick: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevelRoutes = listOf(
        TopLevelRoute(
            route = AppRoute.Tabs,
            name = stringResource(R.string.tabs),
            icon = Icons.Default.Tab,
            parentRoute = AppRoute.Tabs
        ),
        TopLevelRoute(
            route = AppRoute.BookmarkList,
            name = stringResource(R.string.bookmark),
            icon = Icons.Default.Star,
            parentRoute = AppRoute.BookmarkList
        ),
        TopLevelRoute(
            route = AppRoute.ServiceList,
            name = stringResource(R.string.boardList),
            icon = Icons.AutoMirrored.Filled.List,
            parentRoute = AppRoute.BbsServiceGroup
        ),
        TopLevelRoute(
            route = AppRoute.More,
            name = stringResource(R.string.more),
            icon = Icons.Default.MoreHoriz,
            parentRoute = AppRoute.More
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

@Preview(showBackground = true)
@Composable
fun HomeBottomNavigationBarPreview() {
    NavigationBottomBar(
        modifier = Modifier
            .height(56.dp),
        currentDestination = null,
        onClick = {}
    )
}
