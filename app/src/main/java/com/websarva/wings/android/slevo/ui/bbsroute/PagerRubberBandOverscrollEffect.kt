package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pagerの端で消費できなかった横deltaをラバーバンド変位へ変換する。
 *
 * スクロールイベントは下部コントローラーから受け取り、表示用の変位だけを公開する。
 * 実際のPager位置は変更せず、ドラッグ終了後は変位をspringで0へ戻す。
 */
internal class PagerRubberBandOverscrollEffect(
    private val scope: CoroutineScope,
    private val resistanceLimitPx: Float,
) : OverscrollEffect {
    private val offsetState = mutableFloatStateOf(0f)
    private var rawOffsetPx = 0f
    private var returnAnimationJob: Job? = null

    private val returnAnimationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 本文表示へ適用する、抵抗後の境界変位をpxで返す。 */
    val offsetPx: Float
        get() = offsetState.floatValue

    /** Pagerのスクロールで消費されなかったdeltaを境界変位へ変換する。 */
    @Suppress("UNUSED_PARAMETER")
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        // --- 既存の境界変位を解放 ---
        val releaseDelta = calculateReleaseDelta(rawOffsetPx, delta.x)
        val scrollDelta = delta.copy(x = delta.x - releaseDelta)

        // --- Pager移動と境界deltaの蓄積 ---
        val consumedByScroll = performScroll(scrollDelta)
        val unconsumed = scrollDelta - consumedByScroll
        val unconsumedX = unconsumed.x
        val nextRawOffset = rawOffsetPx + releaseDelta + unconsumedX
        if (releaseDelta != 0f || unconsumedX != 0f) {
            updateRawOffset(nextRawOffset)
        }

        // 残りのdeltaはeffectが消費し、親のnested scrollへ伝播させない。
        return consumedByScroll + Offset(releaseDelta + unconsumedX, 0f)
    }

    /** Pagerのfling終了後に残った境界変位の復帰を開始する。 */
    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        try {
            // flingの物理計算とpage settleはPagerへ委譲する。
            performFling(velocity)
        } finally {
            // releaseとcancelの経路でも境界変位を復帰させる。
            launchReturnAnimation()
        }
    }

    override val isInProgress: Boolean
        get() = rawOffsetPx != 0f || returnAnimationJob?.isActive == true

    override val node: DelegatableNode = object : Modifier.Node() {}

    /** gestureが無効になったときに残っている境界変位を復帰させる。 */
    fun reset() {
        launchReturnAnimation()
    }

    /** 現在の未抵抗距離を抵抗後の表示距離へ反映する。 */
    private fun updateRawOffset(rawOffset: Float) {
        returnAnimationJob?.cancel()
        rawOffsetPx = if (rawOffset.isFinite()) rawOffset else 0f
        offsetState.floatValue = calculateRubberBandOffset(rawOffsetPx, resistanceLimitPx)
    }

    /** 境界変位をspringで0へ戻すanimationを開始する。 */
    private fun launchReturnAnimation() {
        returnAnimationJob?.cancel()
        if (offsetState.floatValue == 0f) {
            rawOffsetPx = 0f
            return
        }

        val initialOffset = offsetState.floatValue
        returnAnimationJob = scope.launch {
            animate(
                initialValue = initialOffset,
                targetValue = 0f,
                animationSpec = returnAnimationSpec,
            ) { value, _ ->
                offsetState.floatValue = value
            }
            rawOffsetPx = 0f
            offsetState.floatValue = 0f
        }
    }

    /** 既存の境界変位を戻すために入力deltaから先に消費する量を求める。 */
    private fun calculateReleaseDelta(currentOffset: Float, incomingDelta: Float): Float {
        if (currentOffset == 0f || incomingDelta == 0f ||
            sign(currentOffset) == sign(incomingDelta)
        ) {
            return 0f
        }

        return sign(incomingDelta) * min(abs(incomingDelta), abs(currentOffset))
    }
}

/**
 * 1ページPagerにも境界deltaを渡すため、実際のPagerStateへ処理を委譲する。
 *
 * Pagerのスクロール位置やMutatorMutexは委譲先が保持し、ここではscrollableがoverscrollを
 * dispatchするための方向可否だけを常に有効として公開する。
 */
internal class PagerRubberBandScrollableState(
    private val delegate: ScrollableState,
) : ScrollableState by delegate {
    override val canScrollForward: Boolean
        get() = true

    override val canScrollBackward: Boolean
        get() = true
}

/**
 * 指の移動距離を端ほど圧縮した表示距離へ変換する。
 *
 * 結果は入力と同じ符号を保ち、正の抵抗限界へ漸近する。異常値や無効な限界値では0を返す。
 */
internal fun calculateRubberBandOffset(rawOffsetPx: Float, resistanceLimitPx: Float): Float {
    if (!rawOffsetPx.isFinite() || !resistanceLimitPx.isFinite() || resistanceLimitPx <= 0f) {
        return 0f
    }

    val cappedRawOffset = rawOffsetPx.coerceIn(
        -resistanceLimitPx * RUBBER_BAND_INPUT_MULTIPLIER,
        resistanceLimitPx * RUBBER_BAND_INPUT_MULTIPLIER,
    )
    val distance = abs(cappedRawOffset)
    val resistedDistance = resistanceLimitPx * (1f - 1f / (distance / resistanceLimitPx + 1f))
    return sign(cappedRawOffset) * resistedDistance
}

private const val RUBBER_BAND_INPUT_MULTIPLIER = 8f
