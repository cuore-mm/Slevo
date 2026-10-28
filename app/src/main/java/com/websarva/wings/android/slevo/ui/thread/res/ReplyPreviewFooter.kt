package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults.verticalAlignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange

/** 省略候補と未取得範囲を同じ返信プレビュー末尾へ配置する。 */
@Composable
internal fun ReplyPreviewFooter(
    previewType: ImageReplyPreviewType,
    overflowCount: Int,
    overflowNumbers: List<Int>,
    unavailableRanges: List<ReplyTargetUnavailableRange>,
    onOpenOverflow: (List<Int>) -> Unit,
) {
    val hasOverflowAction = overflowCount > 0 && overflowNumbers.isNotEmpty()
    val hasUnavailableRanges =
        previewType == ImageReplyPreviewType.REPLY_TARGET && unavailableRanges.isNotEmpty()
    if (!hasOverflowAction && !hasUnavailableRanges) {
        // Guard: 末尾に表示する操作も状態もない場合はレイアウト要素を追加しない。
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // --- Remaining acquired candidates ---
        ReplyPreviewOverflowSection(previewType, overflowCount, overflowNumbers, onOpenOverflow)

        // --- Unavailable target ranges ---
        if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
            ReplyTargetUnavailableRangesRow(
                ranges = unavailableRanges,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

/** 省略候補がある場合に区切り線と一覧操作を表示する。 */
@Composable
internal fun ReplyPreviewOverflowSection(
    previewType: ImageReplyPreviewType,
    overflowCount: Int,
    overflowNumbers: List<Int>,
    onOpenOverflow: (List<Int>) -> Unit,
) {
    // Guard: 開く候補がない場合は区切り線も表示しない。
    if (overflowCount <= 0 || overflowNumbers.isEmpty()) return
    HorizontalDivider()
    ReplyPreviewOverflowAction(previewType, overflowCount, overflowNumbers, onOpenOverflow)
}

/** 省略分の取得済み返信先を一覧で開く操作を表示する。 */
@Composable
private fun ReplyPreviewOverflowAction(
    previewType: ImageReplyPreviewType,
    overflowCount: Int,
    overflowNumbers: List<Int>,
    onOpenOverflow: (List<Int>) -> Unit,
) {
    // --- Accessibility action ---
    val descriptionResource = if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
        R.string.reply_preview_open_target_list
    } else {
        R.string.reply_preview_open_source_list
    }
    val overflowDescription = stringResource(descriptionResource, overflowNumbers.size)

    // --- Existing overflow row ---
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = overflowDescription,
                onClick = { onOpenOverflow(overflowNumbers) },
            )
            .semantics { contentDescription = overflowDescription },
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reply_preview_other_count, overflowCount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 未取得番号を個別展開せず、統合範囲の状態文言として表示する。 */
@Composable
internal fun ReplyTargetUnavailableRangesRow(
    ranges: List<ReplyTargetUnavailableRange>,
    modifier: Modifier = Modifier,
) {
    // Guard: 空の範囲は状態行に変換しない。
    if (ranges.isEmpty()) return

    // --- Build bounded text from merged ranges ---
    val rangeText = ranges.joinToString("、") { range ->
        if (range.firstPostNumber == range.lastPostNumber) {
            range.firstPostNumber.toString()
        } else {
            "${range.firstPostNumber}〜${range.lastPostNumber}"
        }
    }
    val unavailableText = stringResource(R.string.reply_preview_unavailable_ranges, rangeText)

    // --- Render non-interactive unavailable state ---
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = unavailableText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Preview(showBackground = true, name = "Reply preview footer")
@Composable
private fun ReplyPreviewFooterPreview() {
    SlevoTheme {
        ReplyPreviewFooter(
            previewType = ImageReplyPreviewType.REPLY_TARGET,
            overflowCount = 2,
            overflowNumbers = listOf(1, 2, 3),
            unavailableRanges = listOf(
                ReplyTargetUnavailableRange(101, 103),
                ReplyTargetUnavailableRange(105, 200),
            ),
            onOpenOverflow = {},
        )
    }
}
