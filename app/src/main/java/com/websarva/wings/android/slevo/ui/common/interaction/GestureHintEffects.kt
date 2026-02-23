package com.websarva.wings.android.slevo.ui.common.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * ジェスチャーヒントの invalid 表示を一定時間後に解除する共通 effect。
 */
@Composable
fun ObserveGestureHintInvalidResetEffect(
    isInvalid: Boolean,
    onReset: () -> Unit,
    delayMillis: Long = 1_200L,
) {
    LaunchedEffect(isInvalid, delayMillis) {
        if (isInvalid) {
            delay(delayMillis)
            onReset()
        }
    }
}
