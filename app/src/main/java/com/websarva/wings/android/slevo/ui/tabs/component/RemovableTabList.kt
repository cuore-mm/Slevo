package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.websarva.wings.android.slevo.ui.common.SlevoLazyColumnScrollbar

/**
 * タブ一覧の削除アニメーション付きリスト表示を共通化する。
 *
 * 削除要求時は呼び出し元の削除処理を実行し、リスト更新に合わせて
 * `animateItem` の退場アニメーションを適用する。
 *
 * @param userScrollEnabled ユーザーによるスクロールを許可するか。
 *                          長押し選択モード中など、スクロールを一時的に抑制したい場合に `false` を渡す。
 */
@Composable
internal fun <T> RemovableTabList(
    modifier: Modifier = Modifier,
    tabItems: List<T>,
    keyOf: (T) -> String,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    verticalSpacing: Dp = TabListLayoutDefaults.listItemSpacing,
    removalDurationMillis: Int = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS,
    externalRemoveKey: String? = null,
    onExternalRemoveConsumed: () -> Unit = {},
    onRemoveConfirmed: (T) -> Unit,
    userScrollEnabled: Boolean = true,
    itemContent: @Composable (item: T, isRemoving: Boolean, requestRemove: () -> Unit) -> Unit,
) {
    // --- State ---
    val removingItems = remember { mutableStateMapOf<String, Boolean>() }
    // --- Cleanup ---
    LaunchedEffect(tabItems) {
        val activeKeys = tabItems.map(keyOf).toSet()
        val staleKeys = removingItems.keys - activeKeys
        staleKeys.forEach { removingItems.remove(it) }
    }

    // --- External removal ---
    LaunchedEffect(externalRemoveKey, tabItems) {
        val targetKey = externalRemoveKey ?: return@LaunchedEffect
        val targetItem = tabItems.firstOrNull { keyOf(it) == targetKey }
        if (targetItem == null) {
            // 対象が既に無い場合は要求だけ消費して二重実行を防ぐ。
            onExternalRemoveConsumed()
            return@LaunchedEffect
        }
        if (removingItems[targetKey] == true) {
            // 既に削除アニメーション中なら再実行しない。
            onExternalRemoveConsumed()
            return@LaunchedEffect
        }
        removingItems[targetKey] = true
        onRemoveConfirmed(targetItem)
        onExternalRemoveConsumed()
    }

    // --- List ---
    Box {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            userScrollEnabled = userScrollEnabled,
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
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() - TabListLayoutDefaults.scrollbarBottomInset,
                ),
            state = listState,
            enabled = tabItems.size > 1,
        ) {}
    }
}
