package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme

/** NG返信先を非表示または一時開示状態で囲む共通カードを表示する。 */
@Composable
internal fun ReplyTargetDisclosureCard(
    postNumber: Int,
    isRevealed: Boolean,
    onToggleDisclosure: () -> Unit,
    modifier: Modifier = Modifier,
    statusLabel: String? = null,
    statusDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    // --- Accessible action label ---
    val actionDescription = stringResource(
        if (isRevealed) {
            R.string.reply_preview_hide_target
        } else {
            R.string.reply_preview_reveal_target
        },
        postNumber,
    )
    // --- Shared frame and stable header ---
    ReplyTargetDisclosureSurface(postNumber = postNumber, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReplyTargetDisclosureHeader(
                postNumber = postNumber,
                isRevealed = isRevealed,
                actionDescription = actionDescription,
                onToggleDisclosure = onToggleDisclosure,
            )
            // --- Hidden state label and state-specific content ---
            if (statusLabel != null) {
                Text(
                    text = statusLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .then(
                            if (statusDescription != null) {
                                Modifier.semantics {
                                    contentDescription = statusDescription
                                }
                            } else {
                                Modifier
                            },
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            content()
        }
    }
}

/** 非表示/開示状態で共用する返信先カードの枠を描画する。 */
@Composable
internal fun ReplyTargetDisclosureSurface(
    postNumber: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reply-target-disclosure-card-$postNumber"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        content = content,
    )
}

/** レス番号と開閉操作を位置固定のヘッダー行へ配置する。 */
@Composable
internal fun ReplyTargetDisclosureHeader(
    postNumber: Int,
    isRevealed: Boolean,
    actionDescription: String,
    onToggleDisclosure: () -> Unit,
) {
    // --- Stable number and right-aligned action ---
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp)
            .testTag("reply-target-disclosure-header-$postNumber"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$postNumber",
            modifier = Modifier.testTag("reply-target-disclosure-number-$postNumber"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        // --- Subdued disclosure action ---
        TextButton(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = actionDescription }
                .testTag("reply-target-disclosure-button-$postNumber"),
            onClick = onToggleDisclosure,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(
                    if (isRevealed) {
                        R.string.reply_preview_hide
                    } else {
                        R.string.reply_preview_reveal
                    },
                ),
            )
        }
    }
}

@Preview(showBackground = true, name = "Hidden NG reply target")
@Composable
private fun HiddenReplyTargetDisclosureCardPreview() {
    SlevoTheme {
        ReplyTargetDisclosureCard(
            postNumber = 12,
            isRevealed = false,
            statusLabel = "NGで非表示",
            onToggleDisclosure = {},
        )
    }
}

@Preview(showBackground = true, name = "Revealed NG reply target")
@Composable
private fun RevealedReplyTargetDisclosureCardPreview() {
    SlevoTheme {
        ReplyTargetDisclosureCard(
            postNumber = 12,
            isRevealed = true,
            onToggleDisclosure = {},
        ) {
            Text(
                text = "開示された返信本文です。\n本文の分だけカードの高さが伸びます。",
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
