package com.websarva.wings.android.slevo.ui.util

import androidx.compose.ui.composed
import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.data.model.GestureDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class HorizontalDirection {
    Right,
    Left,
}

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.detectDirectionalGesture(
    enabled: Boolean,
    threshold: Dp = 64.dp,
    onGestureProgress: (GestureDirection?) -> Unit = {},
    onGestureInvalid: () -> Unit = {},
    onGesture: (GestureDirection) -> Unit,
): Modifier = composed {
    if (!enabled) {
        this
    } else {
        val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
        pointerInput(true, thresholdPx) {
            if (!enabled) return@pointerInput
            var path = mutableListOf(Offset.Zero)
            var totalOffset = Offset.Zero
            var lastDirection: GestureDirection? = null

            detectDragGestures(
                onDragStart = {
                    onGestureProgress(null)
                    path = mutableListOf(Offset.Zero)
                    totalOffset = Offset.Zero
                    lastDirection = null
                },
                onDrag = { change, dragAmount ->
                    totalOffset += dragAmount
                    path.add(totalOffset)
                    val direction = detectGestureDirection(path, thresholdPx)
                    if (direction != lastDirection) {
                        lastDirection = direction
                        onGestureProgress(direction)
                    }
                    change.consume()
                },
                onDragCancel = {
                    if (lastDirection != null) {
                        onGestureProgress(null)
                        lastDirection = null
                    } else {
                        onGestureProgress(null)
                    }
                    onGestureInvalid()
                },
                onDragEnd = {
                    val direction = detectGestureDirection(path, thresholdPx)
                    if (direction != null) {
                        onGesture(direction)
                        if (lastDirection != null) {
                            onGestureProgress(null)
                            lastDirection = null
                        }
                    } else {
                        if (lastDirection != null) {
                            onGestureProgress(null)
                            lastDirection = null
                        } else {
                            onGestureProgress(null)
                        }
                        onGestureInvalid()
                    }
                }
            )
        }
    }
}

private fun detectGestureDirection(
    path: List<Offset>,
    thresholdPx: Float,
): GestureDirection? {
    if (path.size < 2) return null
    val firstIndex = path.indexOfFirst { offset ->
        abs(offset.x) >= thresholdPx
    }
    if (firstIndex == -1) return null
    val firstPoint = path[firstIndex]
    if (abs(firstPoint.x) < abs(firstPoint.y)) return null

    val firstDirection = if (firstPoint.x > 0f) {
        HorizontalDirection.Right
    } else {
        HorizontalDirection.Left
    }

    if (firstIndex == path.lastIndex) {
        return when (firstDirection) {
            HorizontalDirection.Right -> GestureDirection.Right
            HorizontalDirection.Left -> GestureDirection.Left
        }
    }

    var maxX = firstPoint.x
    var minX = firstPoint.x
    var maxY = firstPoint.y
    var minY = firstPoint.y

    for (i in firstIndex + 1 until path.size) {
        val point = path[i]
        maxX = max(maxX, point.x)
        minX = min(minX, point.x)
        maxY = max(maxY, point.y)
        minY = min(minY, point.y)
    }

    return when (firstDirection) {
        HorizontalDirection.Right -> when {
            firstPoint.y - minY >= thresholdPx -> GestureDirection.RightUp
            maxY - firstPoint.y >= thresholdPx -> GestureDirection.RightDown
            firstPoint.x - minX >= thresholdPx -> GestureDirection.RightLeft
            else -> GestureDirection.Right
        }

        HorizontalDirection.Left -> when {
            firstPoint.y - minY >= thresholdPx -> GestureDirection.LeftUp
            maxY - firstPoint.y >= thresholdPx -> GestureDirection.LeftDown
            maxX - firstPoint.x >= thresholdPx -> GestureDirection.LeftRight
            else -> GestureDirection.Left
        }
    }
}
