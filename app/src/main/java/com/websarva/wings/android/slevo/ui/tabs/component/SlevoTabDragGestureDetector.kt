package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlinx.coroutines.CancellationException
import sh.calvin.reorderable.DragGestureDetector

private const val REORDER_TOUCH_SLOP_MULTIPLIER = 1.5f
private const val PREVIEW_MOVEMENT_RESISTANCE = 0.25f

/**
 * 長押し時にメニューを表示し、追加の touch slop 後に reorder drag へ移行する検出器。
 * 長押し前の移動は消費せず、親のスクロールまたは既存の横スワイプ判定へ委譲する。
 */
internal class SlevoTabDragGestureDetector(
    private val onLongPress: () -> Unit,
    private val onLongPressMoved: (Offset) -> Unit,
    private val onDragThresholdActivated: (Offset) -> Unit,
    private val onLongPressReleased: () -> Unit,
    private val onDragFinished: () -> Unit,
    private val onDragCancelled: () -> Unit,
) : DragGestureDetector {
    /**
     * Pointer sequenceを長押し、追加slop、drag、終了の順に処理する。
     * 長押し成立前は親のgestureへ譲り、成立後はUPまたはcancelまでMain passで所有する。
     */
    override suspend fun PointerInputScope.detect(
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset) -> Unit,
    ) {
        var longPressStarted = false
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id)
                    ?: run {
                        logTabReorder { "LONG_PRESS_ABORT id=${down.id}" }
                        return@awaitEachGesture
                    }

                // 長押し成立後はカードメニューを表示し、通常clickをキャンセルする。
                longPress.consume()
                longPressStarted = true
                logTabReorder {
                    "LONG_PRESS id=${longPress.id} pos=${longPress.position}"
                }
                onLongPress()

                // --- Post-long-press ownership ---
                var accumulatedMovement = Offset.Zero
                var dragStarted = false
                val touchSlop = viewConfiguration.touchSlop * REORDER_TOUCH_SLOP_MULTIPLIER

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.find { it.id == longPress.id }
                    if (change == null) {
                        // Pointer消失は通常のUPとは区別し、reorderをキャンセルする。
                        logTabReorder { "POINTER_MISSING id=${longPress.id}" }
                        onDragCancel()
                        onDragCancelled()
                        longPressStarted = false
                        return@awaitEachGesture
                    }

                    if (!change.pressed) {
                        // Main passでUPを消費し、drag前はMenuOpen、drag後は正常終了にする。
                        logTabReorder { "UP id=${change.id} dragStarted=$dragStarted" }
                        change.consume()
                        if (dragStarted) {
                            logTabReorder { "DRAG_END id=${change.id}" }
                            onDragEnd()
                            onDragFinished()
                        } else {
                            logTabReorder { "MENU_OPEN id=${change.id}" }
                            onLongPressReleased()
                        }
                        longPressStarted = false
                        return@awaitEachGesture
                    }

                    val delta = change.positionChangeIgnoreConsumed()
                    val wasConsumed = change.isConsumed
                    logTabReorder {
                        "MOVE id=${change.id} pressed=${change.pressed} " +
                            "consumed=$wasConsumed delta=$delta " +
                            "dragStarted=$dragStarted"
                    }
                    change.consume()
                    if (dragStarted) {
                        // LongPress成立後は他handlerのconsume状態に関係なくreorderを継続する。
                        logTabReorder { "DRAG_MOVE id=${change.id} delta=$delta" }
                        onDrag(change, delta)
                        continue
                    }

                    accumulatedMovement += delta
                    val distance = accumulatedMovement.getDistance()
                    logTabReorder {
                        "SLOP accumulated=$accumulatedMovement distance=$distance " +
                            "threshold=$touchSlop"
                    }
                    if (distance > touchSlop) {
                        val overSlop = accumulatedMovement * ((distance - touchSlop) / distance)
                        val previewOffset = accumulatedMovement * PREVIEW_MOVEMENT_RESISTANCE
                        dragStarted = true
                        logTabReorder {
                            "DRAG_START id=${change.id} pos=${change.position} " +
                                "overSlop=$overSlop previewOffset=$previewOffset"
                        }
                        // 論理dragは全量をReorderableへ渡し、描画側だけhandoffで補間する。
                        val handoffOffset = accumulatedMovement *
                                (PREVIEW_MOVEMENT_RESISTANCE - 1f)
                        onDragThresholdActivated(handoffOffset)
                        onDragStart(change.position)
                        // 閾値到達時は累積量全体を渡し、描画側のhandoffで抵抗位置から連続的に追従させる。
                        onDrag(change, accumulatedMovement)
                    } else {
                        // 閾値までは移動量を圧縮し、メニュープレビューへだけ反映する。
                        onLongPressMoved(accumulatedMovement * PREVIEW_MOVEMENT_RESISTANCE)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            if (longPressStarted) {
                // Composition破棄中も保持中のPreview/draftを残さず、Reorderableへcancelを通知する。
                logTabReorder { "COROUTINE_CANCEL longPressStarted=$longPressStarted" }
                onDragCancel()
                onDragCancelled()
            }
            throw cancellation
        }
    }
}
