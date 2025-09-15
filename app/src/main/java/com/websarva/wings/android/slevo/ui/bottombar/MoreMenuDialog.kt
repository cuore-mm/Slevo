package com.websarva.wings.android.slevo.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.BottomAlignedDialog
import com.websarva.wings.android.slevo.ui.common.LabeledIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuDialog(
    onDismissRequest: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    BottomAlignedDialog(
        onDismiss = onDismissRequest
    ) {
        MoreMenuContent(
            onHistoryClick = onHistoryClick,
            onSettingsClick = onSettingsClick,
            onAboutClick = onAboutClick
        )
    }
}

@Composable
fun MoreMenuContent(
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
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
        LabeledIconButton(
            icon = Icons.Filled.Info,
            label = stringResource(R.string.about_this_app),
            onClick = onAboutClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MoreMenuContentPreview() {
    MoreMenuContent(
        onHistoryClick = {},
        onSettingsClick = {},
        onAboutClick = {}
    )
}
