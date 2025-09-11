package com.websarva.wings.android.slevo.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.util.BottomAlignedDialog
import com.websarva.wings.android.slevo.ui.util.LabeledIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarOverflowMenu(
    onDismissRequest: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    BottomAlignedDialog(
        onDismiss = onDismissRequest
    ) {
        ToolbarMenuContent(
            onBookmarkClick = onBookmarkClick,
            onBoardListClick = onBoardListClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@Composable
fun ToolbarMenuContent(
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        LabeledIconButton(
            icon = Icons.Filled.Star,
            label = stringResource(R.string.bookmark),
            onClick = onBookmarkClick
        )
        LabeledIconButton(
            icon = Icons.AutoMirrored.Filled.List,
            label = stringResource(R.string.boardList),
            onClick = onBoardListClick
        )
        LabeledIconButton(
            icon = Icons.Filled.History,
            label = stringResource(R.string.history),
            onClick = onHistoryClick
        )
        LabeledIconButton(
            icon = Icons.Filled.Settings,
            label = stringResource(R.string.settings),
            onClick = onSettingsClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ToolbarMenuContentPreview() {
    ToolbarMenuContent(
        onBookmarkClick = {},
        onBoardListClick = {},
        onHistoryClick = {},
        onSettingsClick = {},
    )
}
