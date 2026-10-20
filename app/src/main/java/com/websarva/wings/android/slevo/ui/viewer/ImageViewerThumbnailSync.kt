package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import com.websarva.wings.android.slevo.ui.common.scroll.centerLazyListItemAtIndex
import com.websarva.wings.android.slevo.ui.common.scroll.findLazyListItemCenterDeltaPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * サムネイル一覧の表示領域中心に最も近いアイテムのインデックスを返す。
 *
 * 表示アイテムが存在しない場合は null を返す。
 */
internal fun findCenteredThumbnailIndex(
    layoutInfo: LazyListLayoutInfo,
): Int? {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        // Guard: まだ表示対象がない場合は判定できない。
        return null
    }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return visibleItems.minByOrNull { item ->
        val itemCenter = item.offset + item.size / 2
        abs(itemCenter - viewportCenter)
    }?.index
}

/**
 * 指定サムネイルの中心と表示領域中心の差分をピクセルで返す。
 *
 * サムネイルが可視領域外の場合は null を返す。
 */
internal fun findThumbnailCenterDeltaPx(
    layoutInfo: LazyListLayoutInfo,
    index: Int,
): Int? = findLazyListItemCenterDeltaPx(layoutInfo, index)

/**
 * サムネイル停止時に中央へ最も近いサムネイルを選択し、必要に応じて中央へ寄せる。
 *
 * サムネイルバーを実際に自動スクロールした場合は true を返す。
 */
internal suspend fun syncIdleThumbnailCenter(
    thumbnailListState: LazyListState,
    pagerState: PagerState,
    animate: Boolean = true,
): Boolean {
    val centeredIndex = findCenteredThumbnailIndex(thumbnailListState.layoutInfo)
        ?: return false
    if (!scrollPagerToIndexSafely(pagerState, centeredIndex)) {
        return false
    }
    return centerThumbnailAtIndex(
        listState = thumbnailListState,
        index = centeredIndex,
        animate = animate,
    )
}

/**
 * ページャを指定インデックスへ即時移動し、ユーザー割り込み時は監視継続のため失敗扱いで返す。
 */
internal suspend fun scrollPagerToIndexSafely(
    pagerState: PagerState,
    index: Int,
): Boolean {
    if (pagerState.currentPage == index) {
        return true
    }
    return try {
        pagerState.scrollToPage(index)
        true
    } catch (cancellationException: CancellationException) {
        if (!currentCoroutineContext().isActive) {
            throw cancellationException
        }
        false
    }
}

/**
 * 指定したサムネイルが表示領域の中心に来るようスクロールする。
 *
 * 対象が可視外の場合は一度可視化してから中心に寄せる。
 */
internal suspend fun centerThumbnailAtIndex(
    listState: LazyListState,
    index: Int,
    animate: Boolean = true,
): Boolean = centerLazyListItemAtIndex(listState, index, animate)
