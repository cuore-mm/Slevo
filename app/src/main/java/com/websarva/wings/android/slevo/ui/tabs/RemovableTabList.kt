package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.ui.common.SlevoLazyColumnScrollbar

/**
 * タブ一覧の削除アニメーション付きリスト表示を共通化する。
 *
 * 削除要求時は呼び出し元の削除処理を実行し、リスト更新に合わせて
 * `animateItem` の退場アニメーションを適用する。
 */
@Composable
internal fun <T> RemovableTabList(
    modifier: Modifier = Modifier,
    tabItems: List<T>,
    keyOf: (T) -> String,
    contentPadding: PaddingValues,
    verticalSpacing: Dp = 12.dp,
    removalDurationMillis: Int = 200,
    onRemoveConfirmed: (T) -> Unit,
    itemContent: @Composable (item: T, isRemoving: Boolean, requestRemove: () -> Unit) -> Unit,
) {
    // --- State ---
    val removingItems = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    // --- Cleanup ---
    LaunchedEffect(tabItems) {
        val activeKeys = tabItems.map(keyOf).toSet()
        val staleKeys = removingItems.keys - activeKeys
        staleKeys.forEach { removingItems.remove(it) }
    }

    // --- List ---
    Box {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            items(tabItems, key = { keyOf(it) }) { item ->
                val itemKey = keyOf(item)
                val isRemoving = removingItems[itemKey] == true

                // --- Removal animation ---
                Box(
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(removalDurationMillis),
                        fadeOutSpec = tween(removalDurationMillis),
                        placementSpec = tween(removalDurationMillis),
                    )
                ) {
                    itemContent(
                        item,
                        isRemoving
                    ) {
                        if (!isRemoving) {
                            // 退出アニメーションはリスト更新に合わせて適用される。
                            removingItems[itemKey] = true
                            onRemoveConfirmed(item)
                        }
                    }
                }
            }
        }
        SlevoLazyColumnScrollbar(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding() - 24.dp),
            state = listState,
            enabled = tabItems.size > 1,
        ) {}
    }
}
