package com.websarva.wings.android.bbsviewer.ui.common.bookmark

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_amber
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_blue
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_blue_gray
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_brown
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_cyan
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_deep_purple
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_green
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_indigo
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_lime
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_orange
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_pink
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_purple
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_red
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_teal
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_dark_yellow
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_amber
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_blue
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_blue_gray
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_brown
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_cyan
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_deep_purple
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_green
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_indigo
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_lime
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_orange
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_pink
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_purple
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_red
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_teal
import com.websarva.wings.android.bbsviewer.ui.theme.md_theme_light_yellow

@Composable
fun AddGroupDialog(
    modifier: Modifier = Modifier,
    isEdit: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit = {},
    onValueChange: (String) -> Unit,
    enteredValue: String,
    onColorSelected: (String) -> Unit,
    selectedColor: String,
) {
    val colors = if (isSystemInDarkTheme()) {
        listOf(
            md_theme_dark_red, md_theme_dark_pink, md_theme_dark_purple, md_theme_dark_indigo,
            md_theme_dark_blue, md_theme_dark_teal, md_theme_dark_green, md_theme_dark_yellow,
            md_theme_dark_amber, md_theme_dark_brown
        )
    } else {
        listOf(
            md_theme_light_red, md_theme_light_pink, md_theme_light_purple, md_theme_light_indigo,
            md_theme_light_blue, md_theme_light_teal, md_theme_light_green, md_theme_light_yellow,
            md_theme_light_amber, md_theme_light_brown
        )
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        text = {
            Column {
                if (isEdit) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.delete))
                    }
                }
                Spacer(Modifier.height(8.dp))
                /* ---------- ① 色選択エリア ---------- */
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 5,                             // 例：1 行あたり最大 4 個
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { color  ->
                        val hex = String.format("#%06X", 0xFFFFFF and color.toArgb())
                        val isSelected = hex.equals(selectedColor, ignoreCase = true)
                        val border = if (isSelected)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else null

                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { onColorSelected(hex) },
                            shape = CircleShape,
                            color = color,
                            border = border
                        ) { /* 円形チップ */ }
                    }
                }

                Spacer(Modifier.height(8.dp))

                /* ---------- ② グループ名入力 ---------- */
                TextField(
                    value = enteredValue,
                    onValueChange = onValueChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.group_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                val textRes = if (isEdit) R.string.save else R.string.add
                Text(text = stringResource(textRes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun AddGroupDialogPreview() {
    var name by remember { mutableStateOf("") }
    var selColor by remember { mutableStateOf("#FF4081") }
    val palette = listOf("#FF4081", "#3F51B5", "#4CAF50", "#FF9800")

    AddGroupDialog(
        isEdit = true,
        onDismissRequest = {},
        onConfirm = {},
        onDelete = {},
        onValueChange = { name = it },
        onColorSelected = { selColor = it },
        enteredValue = name,
        selectedColor = selColor
    )
}
