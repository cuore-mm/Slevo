package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.model.ThreadDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadInfoBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    threadInfo: ThreadInfo,
    onCopyClick: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        ThreadInfoBottomSheetContent(
            threadInfo = threadInfo,
            onCopyClick = onCopyClick,
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
            text = stringResource(R.string.res_count_label, threadInfo.resCount),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(
                R.string.thread_date_and_momentum,
                date.year,
                date.month,
                date.day,
                date.dayOfWeek,
                date.hour,
                date.minute,
                momentumFormatter.format(threadInfo.momentum)
            ),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onCopyClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = stringResource(R.string.copy))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadInfoBottomSheetPreview() {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ThreadInfoBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {},
        threadInfo = ThreadInfo(
            title = "スレッドタイトル",
            resCount = 100,
            momentum = 1234.5,
            date = ThreadDate(2024, 5, 1, 12, 34, "水")
        ),
        onCopyClick = {}
    )
}
