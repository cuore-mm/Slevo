package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

/**
 * スクロール位置を表す値オブジェクト。
 *
 * `firstVisibleItemIndex` と `firstVisibleItemScrollOffset` を一組として扱う。
 */
internal data class ScrollPosition(
    val index: Int,
    val offset: Int,
)

/**
 * 直近保存位置と比較して重複保存を抑制する。
 *
 * 同一位置が連続して保存対象になった場合、初回のみ保存を許可する。
 */
internal class ScrollPositionSaveState {
    private var lastSaved: ScrollPosition? = null

    /**
     * 渡された位置が前回保存位置と異なる場合に true を返し、保存済みとして記録する。
     */
    fun shouldSave(position: ScrollPosition): Boolean {
        if (lastSaved == position) {
            return false
        }
        lastSaved = position
        return true
    }
}

/**
 * 連続更新中のスクロール位置を、一定間隔ごとに重複抑制しつつ emit する。
 *
 * `distinctUntilChanged` で同一位置の連続更新を圧縮し、
 * `sample` で一定間隔ごとの最新値を取り出す。
 */
@OptIn(FlowPreview::class)
internal fun Flow<ScrollPosition>.scrollPositionsForPersistence(
    intervalMillis: Long,
): Flow<ScrollPosition> {
    return distinctUntilChanged()
        .sample(intervalMillis)
}

/**
 * タブのスクロール位置を、連続更新中・非アクティブ化時・破棄時に保存する。
 *
 * 定期保存は連続更新中でも一定間隔で最新化し、
 * 非アクティブ化時保存と破棄時保存は離脱時点の最終位置を補完する。
 */
@Composable
internal fun ObserveScrollPositionPersistence(
    tabKey: Any,
    listState: LazyListState,
    isActive: Boolean,
    onSave: (index: Int, offset: Int) -> Unit,
) {
    val saveState = remember(tabKey) { ScrollPositionSaveState() }

    fun persistIfChanged(position: ScrollPosition) {
        if (saveState.shouldSave(position)) {
            onSave(position.index, position.offset)
        }
    }

    // --- 定期保存 ---
    LaunchedEffect(listState, isActive, tabKey) {
        if (isActive) {
            snapshotFlow {
                ScrollPosition(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                )
            }
                .scrollPositionsForPersistence(intervalMillis = 200L)
                .collectLatest { position ->
                    persistIfChanged(position)
                }
        }
    }

    // --- 非アクティブ化時保存 ---
    // periodic save(sample) は連続更新中の最新化を担い、この保存は
    // 「次のsample前に離脱した瞬間」の最終位置を補完する役割を持つ。
    var wasActive by remember(tabKey) { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (wasActive && !isActive) {
            persistIfChanged(
                ScrollPosition(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                )
            )
        }
        wasActive = isActive
    }

    // --- 破棄時保存 ---
    DisposableEffect(tabKey, listState) {
        onDispose {
            persistIfChanged(
                ScrollPosition(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                )
            )
        }
    }
}
