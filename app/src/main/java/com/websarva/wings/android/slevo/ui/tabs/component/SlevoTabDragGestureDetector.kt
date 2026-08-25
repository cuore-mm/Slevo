package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import kotlinx.coroutines.CancellationException
import sh.calvin.reorderable.DragGestureDetector

/**
 * 長押し時にメニューを表示し、追加の touch slop 後に reorder drag へ移行する検出器。
 * 長押し前の移動は消費せず、親のスクロールまたは既存の横スワイプ判定へ委譲する。
 */
internal class SlevoTabDragGestureDetector(
    private val onLongPress: () -> Unit,
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
                    ?: return@awaitEachGesture

                // 長押し成立後はカードメニューを表示し、通常clickをキャンセルする。
                longPress.consume()
                longPressStarted = true
                onLongPress()

                // --- Post-long-press ownership ---
                var accumulatedMovement = Offset.Zero
                var dragStarted = false
                val touchSlop = viewConfiguration.touchSlop

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.find { it.id == longPress.id }
                    if (change == null) {
                        // Pointer消失は通常のUPとは区別し、reorderをキャンセルする。
                        onDragCancel()
                        onDragCancelled()
                        longPressStarted = false
                        return@awaitEachGesture
                    }

                    if (!change.pressed) {
                        // Main passでUPを消費し、drag前はMenuOpen、drag後は正常終了にする。
                        change.consume()
                        if (dragStarted) {
                            onDragEnd()
                            onDragFinished()
                        } else {
                            onLongPressReleased()
                        }
                        longPressStarted = false
                        return@awaitEachGesture
                    }

                    val delta = change.positionChangeIgnoreConsumed()
                    change.consume()
                    if (dragStarted) {
                        // LongPress成立後は他handlerのconsume状態に関係なくreorderを継続する。
                        onDrag(change, delta)
                        continue
                    }

                    accumulatedMovement += delta
                    val distance = accumulatedMovement.getDistance()
                    if (distance > touchSlop) {
                        val overSlop = accumulatedMovement * ((distance - touchSlop) / distance)
                        dragStarted = true
                        onDragStart(change.position)
                        onDrag(change, overSlop)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            if (longPressStarted) {
                // Composition破棄中も保持中のPreview/draftを残さず、Reorderableへcancelを通知する。
                onDragCancel()
                onDragCancelled()
            }
            throw cancellation
        }
    }
}
