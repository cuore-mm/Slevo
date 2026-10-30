package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewFooterUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange

/** 省略件数と未取得範囲を操作を持たないフッター内容として表示する。 */
@Composable
internal fun ReplyPreviewFooterContent(
    state: ReplyPreviewFooterUiState,
    modifier: Modifier = Modifier,
) {
    val count = when (state) {
        is ReplyPreviewFooterUiState.More -> state.count
        is ReplyPreviewFooterUiState.MoreAndUnavailable -> state.count
        is ReplyPreviewFooterUiState.Unavailable -> null
    }
    val ranges = when (state) {
        is ReplyPreviewFooterUiState.More -> emptyList()
        is ReplyPreviewFooterUiState.MoreAndUnavailable -> state.ranges
        is ReplyPreviewFooterUiState.Unavailable -> state.ranges
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (count != null) {
                Text(
                    text = stringResource(R.string.reply_preview_other_count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (ranges.isNotEmpty()) {
                val rangeText = ranges.joinToString("、") { range ->
                    if (range.firstPostNumber == range.lastPostNumber) {
                        range.firstPostNumber.toString()
                    } else {
                        "${range.firstPostNumber}〜${range.lastPostNumber}"
                    }
                }
                val unavailableColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                val style = MaterialTheme.typography.labelSmall
                val firstLineHeight = with(LocalDensity.current) {
                    style.lineHeight.toDp()
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier.height(firstLineHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = unavailableColor,
                        )
                    }
                    Text(
                        text = stringResource(R.string.reply_preview_unavailable_ranges, rangeText),
                        style = MaterialTheme.typography.labelSmall,
                        color = unavailableColor,
                    )
                }
            }
        }
        if (count != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true, name = "Reply preview footer")
@Composable
private fun ReplyPreviewFooterPreview() {
    SlevoTheme {
        ReplyPreviewFooterContent(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
            state = ReplyPreviewFooterUiState.MoreAndUnavailable(
                count = 2,
                ranges = listOf(ReplyTargetUnavailableRange(101, 103)),
            ),
        )
    }
}
