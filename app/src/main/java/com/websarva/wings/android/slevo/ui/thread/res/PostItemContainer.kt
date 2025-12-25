package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun PostItemContainer(
    modifier: Modifier,
    indentLevel: Int,
    dimmed: Boolean,
    isPressed: Boolean,
    scope: CoroutineScope,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    showMyPostIndicator: Boolean,
    content: @Composable () -> Unit,
) {
    // --- フィードバック ---
    val haptic = LocalHapticFeedback.current

    // --- 外枠 ---
    val boundaryColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp * indentLevel)
            .alpha(if (dimmed) 0.6f else 1f)
            .drawBehind {
                if (indentLevel > 0) {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = boundaryColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // --- 本文領域 ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isPressed) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                handlePressFeedback(
                                    scope = scope,
                                    onFeedbackStart = { onContentPressedChange(true) },
                                    onFeedbackEnd = { onContentPressedChange(false) },
                                    awaitRelease = { awaitRelease() }
                                )
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRequestMenu()
                            }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                content()
            }

            // --- 自分の投稿マーカー ---
            if (showMyPostIndicator) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostItemContainerPreview() {
    val scope = rememberCoroutineScope()
    PostItemContainer(
        modifier = Modifier,
        indentLevel = 1,
        dimmed = false,
        isPressed = false,
        scope = scope,
        onContentPressedChange = {},
        onRequestMenu = {},
        showMyPostIndicator = true,
    ) {
        Text(text = "コンテナプレビュー")
    }
}
