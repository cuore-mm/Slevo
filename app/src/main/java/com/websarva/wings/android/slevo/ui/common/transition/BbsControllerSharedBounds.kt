package com.websarva.wings.android.slevo.ui.common.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Board/Thread下部コントローラーで共有する対象のidentityを表す。
 *
 * BoardとThreadを型で分離し、同じタブidentityを持つタイトルカードと画面種別ボタンだけを
 * Shared Boundsで照合する。
 */
sealed interface BbsControllerSharedBoundsKey {
    /** 板タブを表す共有identity。 */
    data class Board(val identity: String) : BbsControllerSharedBoundsKey

    /** スレッドタブを表す共有identity。 */
    data class Thread(val identity: String) : BbsControllerSharedBoundsKey
}

/**
 * Board/Thread下部コントローラーのroot CardへShared Boundsを追加する。
 *
 * 異なる内容を持つタイトルカードと画面種別ボタンを標準overlay上で変形し、無効時は入力
 * Modifierをそのまま返す。キーのidentityは呼び出し元がTabInfoから直接渡す。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.bbsControllerSharedBounds(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    key: BbsControllerSharedBoundsKey,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this

    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedVisibilityScope,
            resizeMode = ScaleToBounds,
        )
    }
}
