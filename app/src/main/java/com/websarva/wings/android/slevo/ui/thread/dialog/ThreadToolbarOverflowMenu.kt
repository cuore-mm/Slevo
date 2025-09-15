package com.websarva.wings.android.slevo.ui.thread.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DisplaySettings
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
fun ThreadToolbarOverflowMenu(
    onDismissRequest: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
) {
    BottomAlignedDialog(
        onDismiss = onDismissRequest
    ) {
        ThreadToolbarMenuContent(
            onBookmarkClick = onBookmarkClick,
            onBoardListClick = onBoardListClick,
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick,
            onDisplaySettingsClick = onDisplaySettingsClick,
        )
    }
}

@Composable
fun ThreadToolbarMenuContent(
    onBookmarkClick: () -> Unit,
    onBoardListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisplaySettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
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
        Spacer(modifier = Modifier.height(24.dp))
        LabeledIconButton(
            icon = Icons.Filled.DisplaySettings,
            label = stringResource(R.string.display_settings),
            onClick = onDisplaySettingsClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ThreadToolbarMenuContentPreview() {
    ThreadToolbarMenuContent(
        onBookmarkClick = {},
        onBoardListClick = {},
        onHistoryClick = {},
        onSettingsClick = {},
        onDisplaySettingsClick = {},
    )
}
