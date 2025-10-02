package com.websarva.wings.android.slevo.ui.util

import androidx.compose.runtime.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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

fun Modifier.detectDirectionalGesture(
    enabled: Boolean,
    threshold: Dp = 64.dp,
    onGesture: (GestureDirection) -> Unit,
): Modifier = composed {
    if (!enabled) {
        this
    } else {
        val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
        pointerInput(enabled, thresholdPx) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                val path = mutableListOf(Offset.Zero)
                var totalOffset = Offset.Zero

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                    val delta = change.positionChange()
                    if (delta != Offset.Zero) {
                        totalOffset += delta
                        path.add(totalOffset)
                    }
                    if (change.changedToUpIgnoreConsumed()) {
                        detectGestureDirection(path, thresholdPx)?.let(onGesture)
                        break
                    }
                }
            }
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
