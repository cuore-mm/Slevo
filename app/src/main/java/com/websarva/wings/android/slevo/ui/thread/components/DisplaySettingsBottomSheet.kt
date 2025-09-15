package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsBottomSheet(
    show: Boolean,
    textScale: Float,
    isIndividual: Boolean,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeight: Float,
    onDismissRequest: () -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onIndividualChange: (Boolean) -> Unit,
    onHeaderTextScaleChange: (Float) -> Unit,
    onBodyTextScaleChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
) {
    if (show) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.text_size))
                Slider(
                    value = textScale,
                    onValueChange = {
                        val snapped = (it * 20).roundToInt() / 20f
                        onTextScaleChange(snapped)
                    },
                    valueRange = 0.5f..2f,
                    steps = 29,
                    enabled = !isIndividual
                )
                Text(text = "${(textScale * 100).roundToInt()}%")
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.individual_text_settings))
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = isIndividual, onCheckedChange = onIndividualChange)
                }
                if (isIndividual) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.header_text_size))
                    Slider(
                        value = headerTextScale,
                        onValueChange = {
                            val snapped = (it * 20).roundToInt() / 20f
                            onHeaderTextScaleChange(snapped)
                        },
                        valueRange = 0.5f..2f,
                        steps = 29
                    )
                    Text(text = "${(headerTextScale * 100).roundToInt()}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.body_text_size))
                    Slider(
                        value = bodyTextScale,
                        onValueChange = {
                            val snapped = (it * 20).roundToInt() / 20f
                            onBodyTextScaleChange(snapped)
                        },
                        valueRange = 0.5f..2f,
                        steps = 29
                    )
                    Text(text = "${(bodyTextScale * 100).roundToInt()}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.line_spacing))
                    Slider(
                        value = lineHeight,
                        onValueChange = {
                            val snapped = (it * 10).roundToInt() / 10f
                            onLineHeightChange(snapped)
                        },
                        valueRange = 1.2f..2f,
                        steps = 7
                    )
                    Text(text = String.format("%.1f", lineHeight) + "em")
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
