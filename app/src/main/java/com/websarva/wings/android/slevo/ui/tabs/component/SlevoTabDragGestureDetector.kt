package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
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
     * 長押し成立時点でposition changeを消費し、通常clickの誤発火を抑止する。
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

            var overSlop = Offset.Zero
            val dragChange = awaitTouchSlopOrCancellation(longPress.id) { change, offset ->
                overSlop = offset
                change.consume()
            }

            if (dragChange == null) {
                if (currentEvent.changes.none { it.pressed }) {
                    onLongPressReleased()
                } else {
                    onDragCancelled()
                }
                return@awaitEachGesture
            }

            onDragStart(dragChange.position)
            onDrag(dragChange, overSlop)
            val completed = drag(dragChange.id) { change ->
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
