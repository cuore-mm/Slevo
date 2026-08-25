package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
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
     * Pointer sequenceを長押し、追加slop、dragの順に処理する。
     * 長押し成立前は親のgestureへ譲り、成立後はMain passで移動を所有する。
     */
    override suspend fun PointerInputScope.detect(
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset) -> Unit,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
                ?: return@awaitEachGesture

            // 長押し成立後はカードメニューを表示し、通常clickをキャンセルする。
            longPress.consume()
            onLongPress()

            // --- Post-long-press ownership ---
            var accumulatedMovement = Offset.Zero
            var dragChange: PointerInputChange? = null
            val touchSlop = viewConfiguration.touchSlop

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.find { it.id == longPress.id }
                if (change == null) {
                    // Pointer消失は通常のUPとは区別し、reorderをキャンセルする。
                    onDragCancel()
                    onDragCancelled()
                    return@awaitEachGesture
                }
                if (change.isConsumed) {
                    // Main passで他handlerが先に所有した場合は奪い返さない。
                    onDragCancel()
                    onDragCancelled()
                    return@awaitEachGesture
                }

                if (!change.pressed) {
                    // Main passでUPを消費し、通常clickへ同じsequenceを渡さない。
                    change.consume()
                    onLongPressReleased()
                    return@awaitEachGesture
                }

                val delta = change.positionChangeIgnoreConsumed()
                change.consume()
                accumulatedMovement += delta

                val distance = accumulatedMovement.getDistance()
                if (distance > touchSlop) {
                    val overSlop = accumulatedMovement * ((distance - touchSlop) / distance)
                    dragChange = change
                    onDragStart(change.position)
                    onDrag(change, overSlop)
                    break
                }
            }

            val startedChange = checkNotNull(dragChange)
            val completed = drag(startedChange.id) { change ->
                onDrag(change, change.positionChange())
                change.consume()
            }
            if (completed) {
                currentEvent.changes.forEach { change ->
                    if (change.changedToUp()) change.consume()
                }
                onDragEnd()
                onDragFinished()
            } else {
                onDragCancel()
                onDragCancelled()
            }
        }
    }
}
