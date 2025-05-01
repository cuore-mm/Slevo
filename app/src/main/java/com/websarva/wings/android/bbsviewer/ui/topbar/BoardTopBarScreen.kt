package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardTopBarScreen(
    title: String,
    onNavigationClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onInfoClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(R.string.open_tablist)
                )
            }
        },
        actions = {
            IconButton(onClick = onBookmarkClick) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = stringResource(R.string.bookmark)
                )
            }
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.infomation)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun BoardTopBarScreenPreview() {
    MaterialTheme {
        BoardTopBarScreen(
            title = "なんでも実況J",
            onNavigationClick = {},
            onBookmarkClick = {},
            onInfoClick = {},
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        )
    }
}
