package com.websarva.wings.android.slevo.ui.thread.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDisplaySettingsBottomSheet(
    sheetState: SheetState,
    currentScale: Float,
    onValueChange: (Float) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.post_body_text_size),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = currentScale,
                onValueChange = {
                    val rounded = (it * 20).roundToInt() / 20f
                    onValueChange(rounded)
                },
                valueRange = 0.8f..1.6f,
                steps = 29,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${(currentScale * 100).roundToInt()}%",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismissRequest,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.close))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PreviewThreadDisplaySettingsBottomSheet() {
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    var previewScale by remember { mutableFloatStateOf(1.0f) }
    ThreadDisplaySettingsBottomSheet(
        sheetState = sheetState,
        currentScale = previewScale,
        onValueChange = { previewScale = it },
        onDismissRequest = {
            coroutineScope.launch { sheetState.hide() }
        }
    )
}
