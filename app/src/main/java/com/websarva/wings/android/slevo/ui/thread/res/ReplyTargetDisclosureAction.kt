package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R

/** NG返信先の一時開示を切り替える控えめな操作ボタンを表示する。 */
@Composable
internal fun ReplyTargetDisclosureAction(
    postNumber: Int,
    isRevealed: Boolean,
    onClick: () -> Unit,
) {
    val actionDescription = stringResource(
        if (isRevealed) {
            R.string.reply_preview_hide_target
        } else {
            R.string.reply_preview_reveal_target
        },
        postNumber,
    )
    TextButton(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = actionDescription }
            .testTag("reply-target-disclosure-button-$postNumber"),
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(
            text = stringResource(
                if (isRevealed) R.string.reply_preview_hide else R.string.reply_preview_reveal,
            ),
        )
    }
}
