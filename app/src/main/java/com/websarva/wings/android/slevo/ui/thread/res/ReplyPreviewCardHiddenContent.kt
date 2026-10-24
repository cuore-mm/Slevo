package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange

/** NG非表示中の返信先状態と取得済み候補への操作だけを表示する。 */
@Composable
internal fun ReplyPreviewCardHiddenContent(
    postNumber: Int,
    overflowCount: Int,
    overflowNumbers: List<Int>,
    unavailableRanges: List<ReplyTargetUnavailableRange>,
    onReveal: (() -> Unit)?,
    bodyTextScale: Float,
    headerTextScale: Float,
    lineHeightEm: Float,
    onOpenOverflow: (List<Int>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reply-preview-target-$postNumber"),
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(8.dp),
        ) {
            // --- Hidden status and disclosure action ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("reply-preview-header-$postNumber"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$postNumber  ${stringResource(R.string.reply_preview_ng_hidden)}",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reply-preview-hidden-status-$postNumber"),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * headerTextScale * 0.9f,
                    lineHeight = lineHeightEm.em,
                    color = colors.onSurfaceVariant,
                )
                onReveal?.let { reveal ->
                    ReplyTargetDisclosureAction(
                        postNumber = postNumber,
                        isRevealed = false,
                        onClick = reveal,
                    )
                }
            }

            // --- Remaining acquired candidates ---
            if (overflowCount > 0 && overflowNumbers.isNotEmpty()) {
                val overflowDescription = stringResource(
                    R.string.reply_preview_open_target_list,
                    overflowNumbers.size,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = overflowDescription,
                            onClick = { onOpenOverflow(overflowNumbers) },
                        )
                        .semantics { contentDescription = overflowDescription },
                    color = colors.surfaceVariant,
                ) {
                    Text(
                        text = stringResource(R.string.reply_preview_other_count, overflowCount),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * bodyTextScale * 0.9f,
                        lineHeight = lineHeightEm.em,
                        color = colors.primary,
                    )
                }
            }

            // --- Unavailable target ranges ---
            ReplyTargetUnavailableRangesRow(
                ranges = unavailableRanges,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
