package com.websarva.wings.android.slevo.ui.util

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.data.model.GestureDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 水平方向の向きを簡潔に表す列挙型。
 */
private enum class HorizontalDirection {
    Right,
    Left,
}

/**
 * ジェスチャー検出の結果を表すシールド型。
 *
 * None は判定不能、Invalid は方向混在、Direction は方向確定を表す。
 */
private sealed interface GestureDetectionResult {
    data object None : GestureDetectionResult
    data object Invalid : GestureDetectionResult
    data class Direction(val value: GestureDirection) : GestureDetectionResult
}

/**
 * ドラッグ軌跡から方向ジェスチャーを検出し、進行中と確定の状態を通知する。
 */
@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.detectDirectionalGesture(
    enabled: Boolean,
    threshold: Dp = 48.dp,
    onGestureProgress: (GestureDirection?) -> Unit = {},
    onGestureInvalid: () -> Unit = {},
    onGesture: (GestureDirection) -> Unit,
): Modifier = composed {
    // 有効フラグが false の場合は何もしない Modifier を返す
    if (!enabled) {
        this
    } else {
        // DP 単位の閾値をピクセルに変換
        val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
        pointerInput(true, thresholdPx) {
            if (!enabled) return@pointerInput
            // path: ドラッグの累積オフセットを記録するリスト
            // totalOffset: 前回からのドラッグ量を累積した合計オフセット
            // lastDirection: 前回通知した方向（変化があった時のみ通知する）
            // isInvalid: すでに不正（複数方向が混在）と判定済みか
            var path = mutableListOf(Offset.Zero)
            var totalOffset = Offset.Zero
            var lastDirection: GestureDirection? = null
            var isInvalid = false

            detectDragGestures(
                onDragStart = {
                    // ドラッグ開始時はプログレスをリセットして状態を初期化
                    onGestureProgress(null)
                    path = mutableListOf(Offset.Zero)
                    totalOffset = Offset.Zero
                    lastDirection = null
                    isInvalid = false
                },
                onDrag = { change, dragAmount ->
                    // 既に不正判定なら以降の移動は消費して無視
                    if (isInvalid) {
                        change.consume()
                        return@detectDragGestures
                    }
                    // 合計移動量を更新してパスに追加
                    totalOffset += dragAmount
                    path.add(totalOffset)

                    // 現在のパスを元に方向を判定
                    when (val result = detectGestureDirection(path, thresholdPx)) {
                        GestureDetectionResult.None -> Unit // まだ判定できない
                        GestureDetectionResult.Invalid -> {
                            // 初めて Invalid になった場合のみ onGestureInvalid を呼ぶ
                            if (!isInvalid) {
                                // 既に何か方向が通知されていたら progress をリセット
                                if (lastDirection != null) {
                                    onGestureProgress(null)
                                    lastDirection = null
                                }
                                isInvalid = true
                                onGestureInvalid()
                            }
                        }

                        is GestureDetectionResult.Direction -> {
                            // 新しい方向が検出されたら onGestureProgress を呼ぶ（前回と同じなら何もしない）
                            val direction = result.value
                            if (direction != lastDirection) {
                                lastDirection = direction
                                onGestureProgress(direction)
                            }
                        }
                    }
                    // イベントを消費（他へ伝搬しない）
                    change.consume()
                },
                onDragCancel = {
                    // キャンセル時は進行中の表示をリセット
                    if (lastDirection != null) {
                        onGestureProgress(null)
                        lastDirection = null
                    } else {
                        onGestureProgress(null)
                    }
                    // まだ isInvalid が false（未通知）の場合は無効通知を送る
                    if (!isInvalid) {
                        onGestureInvalid()
                    }
                    isInvalid = false
                },
                onDragEnd = {
                    // ドラッグ終了時に最終判定を行い、確定したら onGesture を呼ぶ
                    val result = detectGestureDirection(path, thresholdPx)
                    when (result) {
                        is GestureDetectionResult.Direction -> {
                            // 有効な方向として確定
                            onGesture(result.value)
                            if (lastDirection != null) {
                                onGestureProgress(null)
                                lastDirection = null
                            }
                        }

                        GestureDetectionResult.Invalid -> {
                            // 不正終了（混在など）は invalid を通知
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
                            // 判定不能（短い移動など）は progress をリセットして invalid 通知
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

/**
 * 本文領域のドラッグ入力を消費し、親の Pager がドラッグ開始するのを防ぐ。
 *
 * 子要素の処理後に移動イベントのみを消費するため、縦スクロールや本文内ジェスチャーは維持される。
 */
@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.blockParentPagerSwipe(enabled: Boolean = true): Modifier = composed {
    // Guard: 無効時は何もしない。
    if (!enabled) {
        this
    } else {
        pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        // ドラッグの移動量のみ消費し、タップや押下の検出は残す。
                        if (change.position != change.previousPosition) {
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}

/**
 * ドラッグ軌跡からジェスチャー方向を判定する。
 *
 * path は累積オフセットの履歴で、thresholdPx は判定に使用する閾値を表す。
 */
private fun detectGestureDirection(
    path: List<Offset>,
    thresholdPx: Float,
): GestureDetectionResult {
    // --- Guard ---
    // 最低でも開始点と 1 点は必要
    if (path.size < 2) return GestureDetectionResult.None

    // --- Initial direction ---
    // 最初に水平成分が閾値を越えたインデックスを探す
    val firstIndex = path.indexOfFirst { offset ->
        abs(offset.x) >= thresholdPx
    }
    if (firstIndex == -1) return GestureDetectionResult.None

    val firstPoint = path[firstIndex]
    // その点が主に水平移動であることを確認（水平成分が垂直成分より大きい）
    if (abs(firstPoint.x) < abs(firstPoint.y)) return GestureDetectionResult.None

    // 最初の水平方向を決定（x が正なら右、負なら左）
    val firstDirection = if (firstPoint.x > 0f) {
        HorizontalDirection.Right
    } else {
        HorizontalDirection.Left
    }

    // もし firstPoint が path の最後の要素なら、その時点で横方向のみ確定
    if (firstIndex == path.lastIndex) {
        return when (firstDirection) {
            HorizontalDirection.Right -> GestureDetectionResult.Direction(GestureDirection.Right)
            HorizontalDirection.Left -> GestureDetectionResult.Direction(GestureDirection.Left)
        }
    }

    // --- Switch detection ---
    val switchIndex = findDirectionSwitchIndex(path, firstIndex, firstDirection, thresholdPx)
    val switchPoint = path[switchIndex]

    // --- Range analysis ---
    // 以降の点を走査して X/Y の最大・最小を求める
    var maxX = switchPoint.x
    var minX = switchPoint.x
    var maxY = switchPoint.y
    var minY = switchPoint.y

    for (i in switchIndex + 1 until path.size) {
        val point = path[i]
        maxX = max(maxX, point.x)
        minX = min(minX, point.x)
        maxY = max(maxY, point.y)
        minY = min(minY, point.y)
    }

    // --- Direction mapping ---
    // 最初の方向（右/左）ごとの判定
    return when (firstDirection) {
        HorizontalDirection.Right -> {
            // 右に最初に動いた場合の追加変化を判定
            // movedUp: 最初の y から上方向へ閾値以上移動したか
            // movedDown: 最初の y から下方向へ閾値以上移動したか
            // movedBack: 最初の x から戻る方向（左）へ閾値以上移動したか
            // movedForward: 最初の x からさらに進む方向（右）へ閾値以上移動したか
            val movedUp = switchPoint.y - minY >= thresholdPx
            val movedDown = maxY - switchPoint.y >= thresholdPx
            val movedBack = switchPoint.x - minX >= thresholdPx
            val movedForward = maxX - switchPoint.x >= thresholdPx

            // 複数のフラグが true なら混在していると判断
            val movementCount = listOf(movedUp, movedDown, movedBack, movedForward).count { it }
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
            // 左に最初に動いた場合の追加変化を判定
            val movedUp = switchPoint.y - minY >= thresholdPx
            val movedDown = maxY - switchPoint.y >= thresholdPx
            val movedBack = maxX - switchPoint.x >= thresholdPx
            val movedForward = switchPoint.x - minX >= thresholdPx

            val movementCount = listOf(movedUp, movedDown, movedBack, movedForward).count { it }
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

/**
 * 初期方向からの切り替わり点を探索し、基準となるインデックスを返す。
 */
private fun findDirectionSwitchIndex(
    path: List<Offset>,
    firstIndex: Int,
    firstDirection: HorizontalDirection,
    thresholdPx: Float,
): Int {
    var switchIndex = firstIndex
    var switchPoint = path[firstIndex]

    return when (firstDirection) {
        HorizontalDirection.Right -> {
            var furthestX = switchPoint.x
            var minY = switchPoint.y
            var maxY = switchPoint.y
            var minX = switchPoint.x

            for (i in firstIndex + 1 until path.size) {
                val point = path[i]

                if (point.x > furthestX) {
                    furthestX = point.x
                    switchIndex = i
                    switchPoint = point
                    minY = point.y
                    maxY = point.y
                    minX = point.x
                    continue
                }

                minY = min(minY, point.y)
                maxY = max(maxY, point.y)
                minX = min(minX, point.x)

                val movedUp = switchPoint.y - minY >= thresholdPx
                val movedDown = maxY - switchPoint.y >= thresholdPx
                val movedBack = switchPoint.x - minX >= thresholdPx

                if (movedUp || movedDown || movedBack) {
                    return switchIndex
                }
            }

            switchIndex
        }

        HorizontalDirection.Left -> {
            var furthestX = switchPoint.x
            var minY = switchPoint.y
            var maxY = switchPoint.y
            var maxX = switchPoint.x

            for (i in firstIndex + 1 until path.size) {
                val point = path[i]

                if (point.x < furthestX) {
                    furthestX = point.x
                    switchIndex = i
                    switchPoint = point
                    minY = point.y
                    maxY = point.y
                    maxX = point.x
                    continue
                }

                minY = min(minY, point.y)
                maxY = max(maxY, point.y)
                maxX = max(maxX, point.x)

                val movedUp = switchPoint.y - minY >= thresholdPx
                val movedDown = maxY - switchPoint.y >= thresholdPx
                val movedBack = maxX - switchPoint.x >= thresholdPx

                if (movedUp || movedDown || movedBack) {
                    return switchIndex
                }
            }

            switchIndex
        }
    }
}
