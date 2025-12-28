package com.websarva.wings.android.slevo.ui.thread.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.BottomSheetListItem
import com.websarva.wings.android.slevo.ui.common.BottomSheetTitle
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMenuSheet(
    text: String,
    onCopyClick: () -> Unit,
    onNgClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    SlevoBottomSheet(onDismissRequest = onDismiss) {
        TextMenuSheetContent(
            text = text,
            onCopyClick = onCopyClick,
            onNgClick = onNgClick,
        )
    }
}

@Composable
fun TextMenuSheetContent(
    text: String,
    onCopyClick: () -> Unit,
    onNgClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BottomSheetTitle(
            text = text,
        )
        HorizontalDivider()
        BottomSheetListItem(
            text = stringResource(R.string.copy),
            icon = Icons.Outlined.ContentCopy,
            onClick = onCopyClick,
        )
        Spacer(Modifier.height(8.dp))
        BottomSheetListItem(
            text = stringResource(R.string.ng_registration),
            icon = Icons.Default.Block,
            onClick = onNgClick,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TextMenuSheetPreview() {
    TextMenuSheetContent(
        text = "abcd",
        onCopyClick = {},
        onNgClick = {},
    )
}
