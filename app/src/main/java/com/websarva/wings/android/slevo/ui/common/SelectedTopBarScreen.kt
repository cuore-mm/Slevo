package com.websarva.wings.android.slevo.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedTopBarScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    selectedCount: Int
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        title = {
            Text(
                text = "$selectedCount" + stringResource(R.string.selected_count_label),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        modifier = modifier
    )
}

// EditableBBSListTopBarScreenのプレビュー
@Preview(showBackground = true)
@Composable
fun SelectedTopBarScreenPreview() {
    SelectedTopBarScreen(
        onBack = { /* doSomething() */ },
        selectedCount = 3
    )
}
