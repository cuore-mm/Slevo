package com.websarva.wings.android.bbsviewer.ui.bbslist.service

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R

@Composable
fun BBSListScreen(
    uiState: BbsServiceUiState,
    modifier: Modifier = Modifier,
    onClick: (ServiceInfo) -> Unit,
    onLongClick: (String) -> Unit,
) {

    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        items(uiState.services, key = { it.domain }) { service ->
            val isSelected = service.domain in uiState.selected
            ServiceCard(
                service = service,
                selected = isSelected,
                onClick = { onClick(service) },
                onLongClick = { onLongClick(service.domain) },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceCard(
    service: ServiceInfo,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = service.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "(${service.boardCount})",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
fun AddBbsDialog(
    enteredUrl: String,
    onUrlChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onAdd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.prompt_enter_bbs_menu_or_board_url),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                TextField(
                    value = enteredUrl,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.url)) },
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAdd,
                enabled = enteredUrl.isNotBlank()
            ) {
                Text(text = stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ServiceCardPreview() {
    ServiceCard(
        service = ServiceInfo(
            domain = "1",
            name = "5ch.net",
            boardCount = 100
        ),
        onClick = {},
        onLongClick = {},
        selected = false,
    )
}

@Preview(showBackground = true)
@Composable
fun AddBBSDialogPreview() {
    AddBbsDialog(
        onDismissRequest = {},
        enteredUrl = "",
        onUrlChange = {},
        onCancel = {},
        onAdd = {}
    )
}
