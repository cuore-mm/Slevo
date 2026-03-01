package com.websarva.wings.android.slevo.ui.thread.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.BottomSheetListItem
import com.websarva.wings.android.slevo.ui.common.BottomSheetTitle
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyActionMenuSheet(
    postNum: Int,
    onReplyClick: () -> Unit,
    onCopyClick: () -> Unit,
    onNgClick: () -> Unit,
    onDismiss: () -> Unit
) {
    SlevoBottomSheet(onDismissRequest = onDismiss) {
        ReplyActionMenuSheetContent(
            postNum = postNum,
            onReplyClick = onReplyClick,
            onCopyClick = onCopyClick,
            onNgClick = onNgClick,
        )
    }
}

@Composable
fun ReplyActionMenuSheetContent(
    postNum: Int,
    onReplyClick: () -> Unit,
    onCopyClick: () -> Unit,
    onNgClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        BottomSheetTitle(text = "No. $postNum")
        HorizontalDivider()
        BottomSheetListItem(
            text = stringResource(R.string.reply),
            icon = Icons.AutoMirrored.Outlined.Reply,
            onClick = onReplyClick
        )
        BottomSheetListItem(
            text = stringResource(R.string.copy),
            icon = Icons.Outlined.ContentCopy,
            onClick = onCopyClick
        )
        BottomSheetListItem(
            text = stringResource(R.string.ng_registration),
            icon = Icons.Outlined.Block,
            onClick = onNgClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ReplyActionMenuSheetPreview() {
    ReplyActionMenuSheetContent(
        postNum = 123,
        onReplyClick = {},
        onCopyClick = {},
        onNgClick = {},
    )
}
