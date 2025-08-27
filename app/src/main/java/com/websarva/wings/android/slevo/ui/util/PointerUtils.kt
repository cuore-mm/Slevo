package com.websarva.wings.android.slevo.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.consumeAllPointerEvents(): Modifier = pointerInput(Unit) {
    detectTapGestures(onPress = { awaitRelease() })
}
