package com.websarva.wings.android.slevo.ui.common.scroll

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.abs

private const val TARGET_ITEM_MISSING = Int.MIN_VALUE

/**
 * LazyColumnの指定アイテムとviewport中央の距離をピクセルで返す。
 *
 * 対象がまだ表示されていない場合はnullを返す。content paddingやitem間隔は、
 * `LazyListLayoutInfo`が返す実測値に含まれるため個別に渡す必要がない。
 */
internal fun findLazyListItemCenterDeltaPx(
    layoutInfo: LazyListLayoutInfo,
    index: Int,
): Int? {
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == index }
        ?: return null
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = targetItem.offset + targetItem.size / 2
    return itemCenter - viewportCenter
}

/**
 * LazyColumnの指定アイテムをviewport中央へ近づける。
 *
 * 対象が非表示の場合は先に可視化し、レイアウト確定後に実測差分を補正する。
 * スクロール可能範囲を超える補正はLazyListStateが境界でクランプする。
 */
internal suspend fun centerLazyListItemAtIndex(
    listState: LazyListState,
    index: Int,
    animate: Boolean = true,
): Boolean {
    if (index < 0) {
        // Guard: 空一覧や解決失敗では無効なindexをLazyListへ渡さない。
        return false
    }

    return try {
        var didAutoScroll = false

        // --- 対象の可視化 ---
        val initialDelta = findLazyListItemCenterDeltaPx(listState.layoutInfo, index)
        if (initialDelta == null) {
            if (animate) {
                listState.animateScrollToItem(index)
            } else {
                listState.scrollToItem(index)
            }
            didAutoScroll = true
        } else if (abs(initialDelta) <= 1) {
            // Guard: 既に中央付近なら不要なスクロールを発生させない。
            return false
        }

        // --- レイアウト同期 ---
        val updatedDelta = findLazyListItemCenterDeltaPx(listState.layoutInfo, index)
            ?: snapshotFlow {
                val layoutInfo = listState.layoutInfo
                when {
                    layoutInfo.totalItemsCount == 0 -> null
                    index !in 0 until layoutInfo.totalItemsCount -> TARGET_ITEM_MISSING
                    else -> findLazyListItemCenterDeltaPx(layoutInfo, index)
                }
            }
                .filterNotNull()
                .first()

        if (updatedDelta == TARGET_ITEM_MISSING) {
            // Fallback: スクロール中に対象が削除された場合は現在位置を維持する。
            return didAutoScroll
        }
        if (abs(updatedDelta) <= 1) {
            // Guard: レイアウト更新後も中央付近なら補正しない。
            return didAutoScroll
        }

        // --- 中央位置の補正 ---
        if (animate) {
            listState.animateScrollBy(updatedDelta.toFloat())
        } else {
            listState.scrollBy(updatedDelta.toFloat())
        }
        true
    } catch (cancellationException: CancellationException) {
        if (!currentCoroutineContext().isActive) {
            throw cancellationException
        }
        false
    }
}
