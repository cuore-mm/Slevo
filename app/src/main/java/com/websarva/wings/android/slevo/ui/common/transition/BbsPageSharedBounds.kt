package com.websarva.wings.android.slevo.ui.common.transition

import android.util.Log
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/**
 * TabsカードとBoard / Threadの表示ページ全体で共有するidentityを表す。
 *
 * BoardとThreadを型で分離し、navigation routeではなく表示中タブのstable identityで照合する。
 */
sealed interface BbsPageSharedBoundsKey {
    /** 板タブの表示ページを表す共有identity。 */
    data class Board(val identity: String) : BbsPageSharedBoundsKey

    /** スレッドタブの表示ページを表す共有identity。 */
    data class Thread(val identity: String) : BbsPageSharedBoundsKey
}

/**
 * TabsカードとBoard / Thread表示ページ全体をcontainer transformで接続する。
 *
 * 内容とサイズが異なるカード・ページを同じoverlay上で拡縮し、無効時は元のModifierを返す。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.bbsPageSharedBounds(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    key: BbsPageSharedBoundsKey,
    enabled: Boolean,
    debugLabel: String,
): Modifier {
    Log.d(
        "BbsPageSharedBounds",
        "COMPOSE $debugLabel key=$key enabled=$enabled " +
                "scope=${System.identityHashCode(sharedTransitionScope)}",
    )

    if (!enabled) return this

    return with(sharedTransitionScope) {
        val state = rememberSharedContentState(key)

        DisposableEffect(state) {
            Log.d(
                "BbsPageSharedBounds",
                "ATTACH $debugLabel key=$key",
            )

            onDispose {
                Log.d(
                    "BbsPageSharedBounds",
                    "DISPOSE $debugLabel key=$key match=${state.isMatchFound}",
                )
            }
        }

        LaunchedEffect(state) {
            var lastValue: Boolean? = null

            while (true) {
                withFrameNanos { }

                val current = state.isMatchFound
                if (current != lastValue) {
                    Log.d(
                        "BbsPageSharedBounds",
                        "MATCH $debugLabel key=$key match=$current",
                    )
                    lastValue = current
                }
            }
        }

        LaunchedEffect(animatedVisibilityScope.transition) {
            snapshotFlow {
                Triple(
                    animatedVisibilityScope.transition.currentState,
                    animatedVisibilityScope.transition.targetState,
                    animatedVisibilityScope.transition.isRunning,
                )
            }.collect {
                Log.d(
                    "BbsPageSharedBounds",
                    "VISIBILITY $debugLabel key=$key " +
                            "scope=${System.identityHashCode(animatedVisibilityScope)} " +
                            "current=${it.first} target=${it.second} running=${it.third}",
                )
            }
        }

        sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> tween(BbsPageTransitionDurationMillis) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
            .clipToBounds()
    }
}

/** TabsカードとBoard / Thread表示ページ全体の共有遷移時間。 */
const val BbsPageTransitionDurationMillis = 500
