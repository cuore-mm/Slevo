package com.websarva.wings.android.slevo.ui.util

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.consume
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

private sealed interface GestureDetectionResult {
    data object None : GestureDetectionResult
    data object Invalid : GestureDetectionResult
    data class Direction(val value: GestureDirection) : GestureDetectionResult
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
            var isInvalid = false

            detectDragGestures(
                onDragStart = {
                    onGestureProgress(null)
                    path = mutableListOf(Offset.Zero)
                    totalOffset = Offset.Zero
                    lastDirection = null
                    isInvalid = false
                },
                onDrag = { change, dragAmount ->
                    if (isInvalid) {
                        change.consume()
                        return@detectDragGestures
                    }
                    totalOffset += dragAmount
                    path.add(totalOffset)
                    when (val result = detectGestureDirection(path, thresholdPx)) {
                        GestureDetectionResult.None -> Unit
                        GestureDetectionResult.Invalid -> {
                            if (!isInvalid) {
                                if (lastDirection != null) {
                                    onGestureProgress(null)
                                    lastDirection = null
                                }
                                isInvalid = true
                                onGestureInvalid()
                            }
                        }

                        is GestureDetectionResult.Direction -> {
                            val direction = result.value
                            if (direction != lastDirection) {
                                lastDirection = direction
                                onGestureProgress(direction)
                            }
                        }
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
                    if (!isInvalid) {
                        onGestureInvalid()
                    }
                    isInvalid = false
                },
                onDragEnd = {
                    val result = detectGestureDirection(path, thresholdPx)
                    when (result) {
                        is GestureDetectionResult.Direction -> {
                            onGesture(result.value)
                            if (lastDirection != null) {
                                onGestureProgress(null)
                                lastDirection = null
                            }
                        }

                        GestureDetectionResult.Invalid -> {
                            if (lastDirection != null) {
                                onGestureProgress(null)
                                lastDirection = null
                            } else {
                                onGestureProgress(null)
                            }
                            if (!isInvalid) {
                                onGestureInvalid()
                            }
                        }

                        GestureDetectionResult.None -> {
                            if (lastDirection != null) {
                                onGestureProgress(null)
                                lastDirection = null
                            } else {
                                onGestureProgress(null)
                            }
                            if (!isInvalid) {
                                onGestureInvalid()
                            }
                        }
                    }
                    isInvalid = false
                }
            )
        }
    }
}

private fun detectGestureDirection(
    path: List<Offset>,
    thresholdPx: Float,
): GestureDetectionResult {
    if (path.size < 2) return GestureDetectionResult.None
    val firstIndex = path.indexOfFirst { offset ->
        abs(offset.x) >= thresholdPx
    }
    if (firstIndex == -1) return GestureDetectionResult.None
    val firstPoint = path[firstIndex]
    if (abs(firstPoint.x) < abs(firstPoint.y)) return GestureDetectionResult.None

    val firstDirection = if (firstPoint.x > 0f) {
        HorizontalDirection.Right
    } else {
        HorizontalDirection.Left
    }

    if (firstIndex == path.lastIndex) {
        return when (firstDirection) {
            HorizontalDirection.Right -> GestureDetectionResult.Direction(GestureDirection.Right)
            HorizontalDirection.Left -> GestureDetectionResult.Direction(GestureDirection.Left)
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
        HorizontalDirection.Right -> {
            val movedUp = firstPoint.y - minY >= thresholdPx
            val movedDown = maxY - firstPoint.y >= thresholdPx
            val movedBack = firstPoint.x - minX >= thresholdPx
            val movementCount = listOf(movedUp, movedDown, movedBack).count { it }
            if (movementCount > 1) {
                GestureDetectionResult.Invalid
            } else when {
                movedUp -> GestureDetectionResult.Direction(GestureDirection.RightUp)
                movedDown -> GestureDetectionResult.Direction(GestureDirection.RightDown)
                movedBack -> GestureDetectionResult.Direction(GestureDirection.RightLeft)
                else -> GestureDetectionResult.Direction(GestureDirection.Right)
            }
        }

        HorizontalDirection.Left -> {
            val movedUp = firstPoint.y - minY >= thresholdPx
            val movedDown = maxY - firstPoint.y >= thresholdPx
            val movedBack = maxX - firstPoint.x >= thresholdPx
            val movementCount = listOf(movedUp, movedDown, movedBack).count { it }
            if (movementCount > 1) {
                GestureDetectionResult.Invalid
            } else when {
                movedUp -> GestureDetectionResult.Direction(GestureDirection.LeftUp)
                movedDown -> GestureDetectionResult.Direction(GestureDirection.LeftDown)
                movedBack -> GestureDetectionResult.Direction(GestureDirection.LeftRight)
                else -> GestureDetectionResult.Direction(GestureDirection.Left)
            }
        }
    }
}
