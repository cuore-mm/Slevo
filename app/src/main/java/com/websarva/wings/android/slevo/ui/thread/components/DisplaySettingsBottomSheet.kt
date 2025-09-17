package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_TEXT_SCALE = 1f
private const val DEFAULT_HEADER_TEXT_SCALE = 0.85f
private const val DEFAULT_BODY_TEXT_SCALE = 1f

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
            // 抽出したコンテンツ関数を呼び出す
            DisplaySettingsContent(
                textScale = textScale,
                isIndividual = isIndividual,
                headerTextScale = headerTextScale,
                bodyTextScale = bodyTextScale,
                lineHeight = lineHeight,
                onTextScaleChange = onTextScaleChange,
                onIndividualChange = onIndividualChange,
                onHeaderTextScaleChange = onHeaderTextScaleChange,
                onBodyTextScaleChange = onBodyTextScaleChange,
                onLineHeightChange = onLineHeightChange,
            )
        }
    }
}

@Composable
fun DisplaySettingsContent(
    textScale: Float,
    isIndividual: Boolean,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeight: Float,
    onTextScaleChange: (Float) -> Unit,
    onIndividualChange: (Boolean) -> Unit,
    onHeaderTextScaleChange: (Float) -> Unit,
    onBodyTextScaleChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        LabeledSlider(
            labelRes = R.string.text_size,
            value = textScale,
            valueRange = 0.7f..1.6f,
            snapFactor = 20,
            steps = 0,
            enabled = !isIndividual,
            valueFormatter = { v ->
                String.format(
                    Locale.getDefault(),
                    "%d%%",
                    (v * 100).roundToInt()
                )
            },
            onValueChange = onTextScaleChange
        )

        Spacer(modifier = Modifier.height(8.dp))
        Card (
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation()
        ){
            Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.individual_text_settings),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(checked = isIndividual, onCheckedChange = onIndividualChange)
                }
                AnimatedVisibility(
                    visible = isIndividual,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        LabeledSlider(
                            labelRes = R.string.header_text_size,
                            value = headerTextScale,
                            valueRange = 0.7f..1.6f,
                            snapFactor = 20,
                            steps = 0,
                            valueFormatter = { v ->
                                String.format(
                                    Locale.getDefault(),
                                    "%d%%",
                                    (v * 100).roundToInt()
                                )
                            },
                            onValueChange = onHeaderTextScaleChange,
                            sliderColors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LabeledSlider(
                            labelRes = R.string.body_text_size,
                            value = bodyTextScale,
                            valueRange = 0.7f..1.6f,
                            snapFactor = 20,
                            steps = 0,
                            valueFormatter = { v ->
                                String.format(
                                    Locale.getDefault(),
                                    "%d%%",
                                    (v * 100).roundToInt()
                                )
                            },
                            onValueChange = onBodyTextScaleChange,
                            sliderColors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        LabeledSlider(
                            labelRes = R.string.line_spacing,
                            value = lineHeight,
                            valueRange = 1.2f..1.8f,
                            snapFactor = 10,
                            steps = 5,
                            valueFormatter = { v ->
                                String.format(Locale.getDefault(), "%d%%", (v * 100).roundToInt())
                            },
                            onValueChange = onLineHeightChange,
                            sliderColors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.reset),
            modifier = Modifier
                .align(Alignment.End)
                .clickable {
                    onTextScaleChange(DEFAULT_TEXT_SCALE)
                    onHeaderTextScaleChange(DEFAULT_HEADER_TEXT_SCALE)
                    onBodyTextScaleChange(DEFAULT_BODY_TEXT_SCALE)
                    onLineHeightChange(DEFAULT_THREAD_LINE_HEIGHT)
                },
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}


@Composable
fun LabeledSlider(
    modifier: Modifier = Modifier,
    labelRes: Int,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    snapFactor: Int = 0,
    steps: Int = 0,
    enabled: Boolean = true,
    valueFormatter: (Float) -> String = { v -> String.format(Locale.getDefault(), "%.2f", v) },
    sliderColors: SliderColors = SliderDefaults.colors(),
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(labelRes),
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = valueFormatter(value),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally),
        )
        Slider(
            value = value,
            onValueChange = {
                val newValue =
                    if (snapFactor > 0) (it * snapFactor).roundToInt() / snapFactor.toFloat() else it
                onValueChange(newValue)
            },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = sliderColors
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DisplaySettingsContentPreview() {
    val textScaleState = remember { mutableFloatStateOf(DEFAULT_TEXT_SCALE) }
    val individualState = remember { mutableStateOf(true) }
    val headerScaleState = remember { mutableFloatStateOf(DEFAULT_HEADER_TEXT_SCALE) }
    val bodyScaleState = remember { mutableFloatStateOf(DEFAULT_BODY_TEXT_SCALE) }
    val lineHeightState = remember { mutableFloatStateOf(DEFAULT_THREAD_LINE_HEIGHT) }

    DisplaySettingsContent(
        textScale = textScaleState.floatValue,
        isIndividual = individualState.value,
        headerTextScale = headerScaleState.floatValue,
        bodyTextScale = bodyScaleState.floatValue,
        lineHeight = lineHeightState.floatValue,
        onTextScaleChange = { textScaleState.floatValue = it },
        onIndividualChange = { individualState.value = it },
        onHeaderTextScaleChange = { headerScaleState.floatValue = it },
        onBodyTextScaleChange = { bodyScaleState.floatValue = it },
        onLineHeightChange = { lineHeightState.floatValue = it },
    )
}
