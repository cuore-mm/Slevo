package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.thread.dialog.ThreadCopyDialog
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadInfoBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    threadInfo: ThreadInfo,
    threadUrl: String,
) {
    var showCopyDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        ThreadInfoBottomSheetContent(
            threadInfo = threadInfo,
            onCopyClick = { showCopyDialog = true },
        )
    }
    if (showCopyDialog) {
        ThreadCopyDialog(
            threadTitle = threadInfo.title,
            threadUrl = threadUrl,
            onDismissRequest = { showCopyDialog = false },
        )
    }
}

@Composable
private fun ThreadInfoBottomSheetContent(
    threadInfo: ThreadInfo,
    onCopyClick: () -> Unit,
) {
    val momentumFormatter = remember { DecimalFormat("0.0") }
    val date = threadInfo.date
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = threadInfo.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.res_count_prefix) + " ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(threadInfo.resCount.toString())
                }
            },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Row {
            Text(
                text = stringResource(
                    R.string.thread_date,
                    date.year,
                    date.month,
                    date.day,
                    date.dayOfWeek,
                    date.hour,
                    date.minute
                ),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Text(
                text = stringResource(R.string.momentum) + ": "
                        + momentumFormatter.format(threadInfo.momentum),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable(onClick = onCopyClick)
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.copy)
            )
            Text(
                text = stringResource(R.string.copy),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadInfoBottomSheetContentPreview() {
    ThreadInfoBottomSheetContent(
        threadInfo = ThreadInfo(
            title = "スレッドタイトル",
            resCount = 100,
            momentum = 1234.5,
            date = ThreadDate(2024, 5, 1, 12, 34, "水")
        ),
        onCopyClick = {}
    )
}
