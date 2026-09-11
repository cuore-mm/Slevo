package com.websarva.wings.android.slevo.ui.common.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
): Modifier {
    if (!enabled) return this

    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = fadeIn(animationSpec = tween(BbsPageTransitionDurationMillis)),
            exit = fadeOut(animationSpec = tween(BbsPageTransitionDurationMillis)),
            boundsTransform = { _, _ -> tween(BbsPageTransitionDurationMillis) },
            resizeMode = scaleToBounds(),
        )
    }
}

/** TabsカードとBoard / Thread表示ページ全体の共有遷移時間。 */
const val BbsPageTransitionDurationMillis = 300
