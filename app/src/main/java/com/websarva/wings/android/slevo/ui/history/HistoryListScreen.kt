package com.websarva.wings.android.slevo.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryListScreen(
    histories: List<ThreadHistoryDao.HistoryWithLastAccess>,
    onThreadClick: (ThreadHistoryDao.HistoryWithLastAccess) -> Unit,
    modifier: Modifier = Modifier
) {
    if (histories.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.no_history))
        }
        return
    }

    val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    LazyColumn(modifier = modifier) {
        var currentDate: String? = null
        histories.forEach { history ->
            val date = formatter.format(Date(history.lastAccess ?: 0L))
            if (date != currentDate) {
                currentDate = date
                item(key = "header_$date") {
                    Text(
                        text = date,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            item(key = history.history.id) {
                ListItem(
                    modifier = Modifier
                        .clickable { onThreadClick(history) }
                        .padding(horizontal = 8.dp),
                    headlineContent = { Text(history.history.title) },
                    supportingContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = history.history.boardName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = history.history.resCount.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
