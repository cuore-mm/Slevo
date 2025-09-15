package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
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
    onDismissRequest: () -> Unit,
    onTextScaleChange: (Float) -> Unit,
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
                    steps = 29
                )
                Text(text = "${(textScale * 100).roundToInt()}%")
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
