package com.websarva.wings.android.slevo.ui.util

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.data.model.GestureDirection
import kotlin.math.abs
import kotlin.math.max

// 水平方向の向きを簡潔に表すための列挙型
private enum class HorizontalDirection {
    Right,
    Left,
}

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.detectDirectionalGesture(
    enabled: Boolean,
    threshold: Dp = 24.dp,
    bendThreshold: Dp = 20.dp,
    backThreshold: Dp = 28.dp,
    observationDurationMillis: Long = 120L,
    verticalSlop: Dp = 10.dp,
    horizontalSlop: Dp = 18.dp,
    onGestureProgress: (GestureDirection?) -> Unit = {},
    onGestureInvalid: () -> Unit = {},
    onGesture: (GestureDirection) -> Unit,
): Modifier = composed {
    if (!enabled) {
        this
    } else {
        val density = LocalDensity.current
        val thresholds = GestureThresholds(
            straightDx = with(density) { threshold.toPx() },
            bendDy = with(density) { bendThreshold.toPx() },
            backBaseDx = with(density) { backThreshold.toPx() },
            verticalSlop = with(density) { verticalSlop.toPx() },
            horizontalSlop = with(density) { horizontalSlop.toPx() },
            ratioThreshold = PRIMARY_RATIO_THRESHOLD,
            backRatio = BACKWARD_RATIO,
            observationDurationMillis = observationDurationMillis,
        )

        pointerInput(enabled, thresholds) {
            if (!enabled) return@pointerInput

            while (true) {
                awaitPointerEventScope {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lockState: GestureLockState = GestureLockState.Observing
                    var lastDirection: GestureDirection? = null
                    var hasNotifiedInvalid = false

                    val pointerId = down.id
                    val downPosition = down.position
                    val downTime = down.uptimeMillis

                    onGestureProgress(null)

                    fun updateProgress(direction: GestureDirection?) {
                        if (direction != lastDirection) {
                            lastDirection = direction
                            onGestureProgress(direction)
                        }
                    }

                    fun notifyInvalid() {
                        if (!hasNotifiedInvalid) {
                            onGestureInvalid()
                            hasNotifiedInvalid = true
                        }
                    }

                    var pointerUp = false
                    while (!pointerUp) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue

                        val position = change.position
                        val totalOffset = position - downPosition

                        if (change.changedToUp()) {
                            when (val state = lockState) {
                                is GestureLockState.Horizontal -> {
                                    val finalDirection = state.context.finalDirection
                                    if (finalDirection != null) {
                                        onGesture(finalDirection)
                                    } else {
                                        notifyInvalid()
                                    }
                                }

                                GestureLockState.Vertical, GestureLockState.Observing -> notifyInvalid()
                            }

                            updateProgress(null)
                            pointerUp = true
                            continue
                        }

                        lockState = when (val state = lockState) {
                            GestureLockState.Observing -> {
                                val elapsed = change.uptimeMillis - downTime
                                if (elapsed < thresholds.observationDurationMillis) {
                                    GestureLockState.Observing
                                } else {
                                    val absDx = abs(totalOffset.x)
                                    val absDy = abs(totalOffset.y)
                                    val magnitude = absDx + absDy
                                    val horizontalRatio = if (magnitude == 0f) 0f else absDx / magnitude

                                    when {
                                        absDy >= thresholds.verticalSlop &&
                                            horizontalRatio < thresholds.ratioThreshold -> {
                                            notifyInvalid()
                                            GestureLockState.Vertical
                                        }

                                        absDx >= thresholds.horizontalSlop &&
                                            horizontalRatio >= thresholds.ratioThreshold -> {
                                            val direction = if (totalOffset.x >= 0f) {
                                                HorizontalDirection.Right
                                            } else {
                                                HorizontalDirection.Left
                                            }
                                            change.consume()
                                            GestureLockState.Horizontal(
                                                HorizontalGestureContext(
                                                    lockPoint = position,
                                                    primaryDirection = direction,
                                                ),
                                            )
                                        }

                                        else -> GestureLockState.Observing
                                    }
                                }
                            }

                            is GestureLockState.Horizontal -> {
                                val dx = position.x - state.context.lockPoint.x
                                val dy = position.y - state.context.lockPoint.y
                                val direction = evaluateHorizontalGesture(
                                    dx = dx,
                                    dy = dy,
                                    context = state.context,
                                    thresholds = thresholds,
                                )

                                updateProgress(direction)

                                if (direction != null) {
                                    state.context.finalDirection = direction
                                }

                                change.consume()
                                state
                            }

                            GestureLockState.Vertical -> {
                                notifyInvalid()
                                GestureLockState.Vertical
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PRIMARY_RATIO_THRESHOLD = 0.75f
private const val BACKWARD_RATIO = 0.4f

private data class GestureThresholds(
    val straightDx: Float,
    val bendDy: Float,
    val backBaseDx: Float,
    val verticalSlop: Float,
    val horizontalSlop: Float,
    val ratioThreshold: Float,
    val backRatio: Float,
    val observationDurationMillis: Long,
)

private sealed interface GestureLockState {
    data object Observing : GestureLockState
    data object Vertical : GestureLockState
    data class Horizontal(val context: HorizontalGestureContext) : GestureLockState
}

private data class HorizontalGestureContext(
    val lockPoint: Offset,
    val primaryDirection: HorizontalDirection,
    var maxDxForward: Float = 0f,
    var finalDirection: GestureDirection? = null,
) {
    val primarySign: Float = if (primaryDirection == HorizontalDirection.Right) 1f else -1f
}

private fun evaluateHorizontalGesture(
    dx: Float,
    dy: Float,
    context: HorizontalGestureContext,
    thresholds: GestureThresholds,
): GestureDirection? {
    val signedDx = context.primarySign * dx
    if (signedDx > context.maxDxForward) {
        context.maxDxForward = signedDx
    }

    val absDx = abs(dx)
    val absDy = abs(dy)

    val backThreshold = max(thresholds.backBaseDx, thresholds.backRatio * context.maxDxForward)
    val isBackward = signedDx <= -backThreshold && absDx > absDy
    if (isBackward) {
        return when (context.primaryDirection) {
            HorizontalDirection.Right -> GestureDirection.RightLeft
            HorizontalDirection.Left -> GestureDirection.LeftRight
        }
    }

    if (absDy >= thresholds.bendDy) {
        val isUp = dy < 0f
        return when (context.primaryDirection) {
            HorizontalDirection.Right -> if (isUp) {
                GestureDirection.RightUp
            } else {
                GestureDirection.RightDown
            }

            HorizontalDirection.Left -> if (isUp) {
                GestureDirection.LeftUp
            } else {
                GestureDirection.LeftDown
            }
        }
    }

    if (signedDx >= thresholds.straightDx) {
        return when (context.primaryDirection) {
            HorizontalDirection.Right -> GestureDirection.Right
            HorizontalDirection.Left -> GestureDirection.Left
        }
    }

    return null
}
