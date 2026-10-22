package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus

/** 返信先がNGまたは未取得のときに番号・状態・利用可能な操作を表示する。 */
@Composable
internal fun ReplyTargetStatusRow(
    postNumber: Int,
    status: ReplyTargetCandidateStatus,
    overflowCount: Int,
    overflowNumbers: List<Int>,
    hasMoreCandidates: Boolean,
    onReveal: (() -> Unit)?,
    onOpenOverflow: (() -> Unit)?,
) {
    // --- Candidate state label ---
    val stateText = stringResource(
        when (status) {
            ReplyTargetCandidateStatus.NG -> R.string.reply_preview_ng_hidden
            ReplyTargetCandidateStatus.UNAVAILABLE -> R.string.reply_preview_unavailable
            // 正常候補は投稿プレビューカードで描画する。
            ReplyTargetCandidateStatus.AVAILABLE -> return
        },
    )
    val stateDescription = stringResource(
        R.string.reply_preview_status_description,
        postNumber,
        stateText,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$postNumber  $stateText",
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = stateDescription },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (status == ReplyTargetCandidateStatus.NG && onReveal != null) {
                val revealDescription = stringResource(
                    R.string.reply_preview_reveal_target,
                    postNumber,
                )
                Spacer(Modifier.width(4.dp))
                Button(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = revealDescription },
                    onClick = onReveal,
                ) {
                    Text(stringResource(R.string.reply_preview_reveal))
                }
            }
        }
        // --- Remaining candidates and truncation notice ---
        if (overflowCount > 0 && overflowNumbers.isNotEmpty() && onOpenOverflow != null) {
            val overflowDescription = stringResource(
                R.string.reply_preview_open_target_list,
                overflowNumbers.size,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = overflowDescription
                    },
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onOpenOverflow,
            ) {
                Text(
                    text = stringResource(R.string.reply_preview_other_count, overflowCount),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (hasMoreCandidates) {
                Text(
                    text = stringResource(R.string.reply_preview_candidates_truncated),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "NG reply target", widthDp = 220, fontScale = 1.4f)
@Composable
private fun ReplyTargetNgStatusRowPreview() {
    SlevoTheme {
        ReplyTargetStatusRow(
            postNumber = 12,
            status = ReplyTargetCandidateStatus.NG,
            overflowCount = 2,
            overflowNumbers = listOf(12, 14, 16),
            hasMoreCandidates = true,
            onReveal = {},
            onOpenOverflow = {},
        )
    }
}

@Preview(showBackground = true, name = "Narrow unavailable reply target", widthDp = 220, fontScale = 1.4f)
@Composable
private fun ReplyTargetUnavailableNarrowStatusRowPreview() {
    SlevoTheme {
        ReplyTargetStatusRow(
            postNumber = 99,
            status = ReplyTargetCandidateStatus.UNAVAILABLE,
            overflowCount = 0,
            overflowNumbers = emptyList(),
            hasMoreCandidates = false,
            onReveal = null,
            onOpenOverflow = null,
        )
    }
}

@Preview(showBackground = true, name = "Unavailable reply target")
@Composable
private fun ReplyTargetUnavailableStatusRowPreview() {
    SlevoTheme {
        ReplyTargetStatusRow(
            postNumber = 99,
            status = ReplyTargetCandidateStatus.UNAVAILABLE,
            overflowCount = 0,
            overflowNumbers = emptyList(),
            hasMoreCandidates = false,
            onReveal = null,
            onOpenOverflow = null,
        )
    }
}
