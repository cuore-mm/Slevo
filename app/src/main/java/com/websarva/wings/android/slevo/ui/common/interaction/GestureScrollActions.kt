package com.websarva.wings.android.slevo.ui.common.interaction

import androidx.compose.foundation.lazy.LazyListState
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.ui.common.scroll.resolveBottomTargetIndex
import com.websarva.wings.android.slevo.ui.common.scroll.waitForViewportUpdate

/**
 * 画面共通の ToTop / ToBottom ジェスチャー実行を行う。
 *
 * 実行対象外のアクションが渡された場合は `false` を返し、呼び出し側で固有処理を続行できる。
 */
suspend fun executeGestureScrollAction(
    action: GestureAction,
    listState: LazyListState,
    fallbackItemCount: Int,
    showBottomBar: (() -> Unit)? = null,
): Boolean {
    return when (action) {
        GestureAction.ToTop -> {
            showBottomBar?.invoke()
            listState.scrollToItem(0)
            true
        }

        GestureAction.ToBottom -> {
            showBottomBar?.invoke()
            waitForViewportUpdate(listState)
            val targetIndex = resolveBottomTargetIndex(
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                fallbackCount = fallbackItemCount,
            )
            listState.scrollToItem(targetIndex)
            true
        }

        else -> false
    }
}
