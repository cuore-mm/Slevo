package com.websarva.wings.android.slevo.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor

data class BookmarkToolBarAction(
    val icon: ImageVector,
    @StringRes val contentDescriptionRes: Int,
    val onClick: () -> Unit,
    val tint: Color? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookmarkToolBar(
    modifier: Modifier = Modifier,
    title: String,
    bookmarkState: SingleBookmarkState,
    onBookmarkClick: () -> Unit,
    actions: List<BookmarkToolBarAction>,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
    onTitleClick: (() -> Unit)? = null,
    onRefreshClick: (() -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    titleFontWeight: FontWeight = FontWeight.Bold,
    titleMaxLines: Int = 2,
) {
    FlexibleBottomAppBar(
        modifier = modifier,
        expandedHeight = 96.dp,
        scrollBehavior = scrollBehavior,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            val cardModifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)

            val cardContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBookmarkClick) {
                        if (bookmarkState.isBookmarked) {
                            val tintColor = bookmarkState.selectedGroup?.colorName?.let { bookmarkColor(it) }
                                ?: LocalContentColor.current
                            Box {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = tintColor,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(R.string.bookmark),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.StarOutline,
                                contentDescription = stringResource(R.string.bookmark),
                            )
                        }
                    }
                    Text(
                        text = title,
                        fontWeight = titleFontWeight,
                        style = titleStyle,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onRefreshClick != null) {
                        IconButton(onClick = onRefreshClick) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                            )
                        }
                    }
                }
            }

            if (onTitleClick != null) {
                Card(
                    modifier = cardModifier,
                    onClick = onTitleClick,
                ) {
                    cardContent()
                }
            } else {
                Card(modifier = cardModifier) {
                    cardContent()
                }
            }

            Spacer(modifier = Modifier.padding(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                actions.forEach { action ->
                    IconButton(onClick = action.onClick) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.contentDescriptionRes),
                            tint = action.tint ?: LocalContentColor.current,
                        )
                    }
                }
            }
        }
    }
}
