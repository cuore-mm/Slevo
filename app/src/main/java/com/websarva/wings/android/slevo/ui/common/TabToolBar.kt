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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.components.ThreadToolBar
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState

/**
 * タブ型ボトムバーに並べる単体アクションの表示情報を保持する。
 *
 * アイコンとアクセシビリティ文言、実行コールバックをひとまとまりで扱う。
 */
data class TabToolBarAction(
    val icon: ImageVector,
    @StringRes val contentDescriptionRes: Int,
    val onClick: () -> Unit,
    val tint: Color? = null,
)

/**
 * 板/スレッド画面のボトムバー表示を共通化する。
 *
 * 上段はタイトル・ブックマーク・更新、下段はアクション群を並べる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TabToolBar(
    modifier: Modifier = Modifier,
    title: String,
    bookmarkState: BookmarkStatusState,
    onBookmarkClick: () -> Unit,
    actions: List<TabToolBarAction>,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
    onTitleClick: (() -> Unit)? = null,
    onRefreshClick: (() -> Unit),
    isLoading: Boolean = false,
    loadProgress: Float = 0f,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    titleFontWeight: FontWeight = FontWeight.Bold,
    titleMaxLines: Int = 2,
    titleTextAlign: TextAlign = TextAlign.Start,
) {
    // --- Layout ---
    Box(modifier = modifier.fillMaxWidth()) {
        FlexibleBottomAppBar(
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

                // --- Title row ---
                val cardContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FeedbackTooltipIconButton(
                            tooltipText = stringResource(R.string.bookmark),
                            onClick = onBookmarkClick,
                        ) {
                            if (bookmarkState.isBookmarked) {
                                val tintColor =
                                    bookmarkState.selectedGroup?.colorName?.let { bookmarkColor(it) }
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
                            textAlign = titleTextAlign,
                            modifier = Modifier.weight(1f),
                        )
                        FeedbackTooltipIconButton(
                            tooltipText = stringResource(R.string.refresh),
                            onClick = onRefreshClick,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                            )
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

                // --- Actions row ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    actions.forEach { action ->
                        FeedbackTooltipIconButton(
                            tooltipText = stringResource(action.contentDescriptionRes),
                            onClick = action.onClick,
                        ) {
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
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = ProgressIndicatorDefaults.linearColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadToolBarPreview() {
    ThreadToolBar(
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(
                title = "スレッドのタイトル"
            ),
            bookmarkStatusState = BookmarkStatusState(
                isBookmarked = false,
                selectedGroup = null
            )
        ),
        isTreeSort = false,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onRefreshClick = {},
        onSearchClick = {},
        onBookmarkClick = {},
        onThreadInfoClick = {},
        onMoreClick = {},
        onAutoScrollClick = {}
    )
}
