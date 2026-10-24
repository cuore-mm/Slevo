package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange

/** 未取得番号を個別展開せず、統合範囲の状態文言として表示する。 */
@Composable
internal fun ReplyTargetUnavailableRangesRow(
    ranges: List<ReplyTargetUnavailableRange>,
    modifier: Modifier = Modifier,
) {
    if (ranges.isEmpty()) return

    // --- 統合区間から有限長の表示文言を作る ---
    val rangeText = ranges.joinToString("、") { range ->
        if (range.firstPostNumber == range.lastPostNumber) {
            range.firstPostNumber.toString()
        } else {
            "${range.firstPostNumber}〜${range.lastPostNumber}"
        }
    }
    val unavailableText = stringResource(R.string.reply_preview_unavailable_ranges, rangeText)

    // --- 非操作の未取得状態を描画 ---
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = unavailableText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(showBackground = true, name = "Unavailable ranges only")
@Composable
private fun ReplyTargetUnavailableRangesRowPreview() {
    SlevoTheme {
        ReplyTargetUnavailableRangesRow(
            ranges = listOf(
                ReplyTargetUnavailableRange(101, 103),
                ReplyTargetUnavailableRange(105, 200),
            ),
        )
    }
}
