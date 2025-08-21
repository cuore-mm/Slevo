package com.websarva.wings.android.bbsviewer.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    isBookmarked: Boolean,
    bookmarkIconColor: Color,
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
        actions = {
            IconButton(onClick = onBookmarkClick) {
                if (isBookmarked) {
                    // ブックマークされている場合：アイコンを重ねて表示
                    Box {
                        // ① 背景（内側の色）となる塗りつぶしアイコン
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null, // contentDescriptionは前景のアイコンに設定
                            tint = bookmarkIconColor
                        )
                        // ② 前景（縁取り）となるアイコン
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.bookmark),
                        )
                    }
                } else {
                    // ブックマークされていない場合：縁取りアイコンのみを表示
                    Icon(
                        imageVector = Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.bookmark),
                        tint = LocalContentColor.current
                    )
                }
            }
//            IconButton(onClick = onInfoClick) {
//                Icon(
//                    imageVector = Icons.Outlined.Info,
//                    contentDescription = stringResource(R.string.infomation)
//                )
//            }
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
            isBookmarked = false,
            bookmarkIconColor = Color.Yellow,
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        )
    }
}
