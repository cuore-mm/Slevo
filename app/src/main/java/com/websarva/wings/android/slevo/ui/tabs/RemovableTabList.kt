package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * タブ一覧の削除アニメーション付きリスト表示を共通化する。
 *
 * 削除要求時は対象アイテムを退出アニメーションで非表示にした後、
 * 呼び出し元の削除処理を実行する。
 */
@Composable
internal fun <T> RemovableTabList(
    modifier: Modifier = Modifier,
    tabItems: List<T>,
    keyOf: (T) -> String,
    contentPadding: PaddingValues,
    verticalSpacing: Dp = 12.dp,
    removalDurationMillis: Int = 180,
    onRemoveConfirmed: (T) -> Unit,
    itemContent: @Composable (item: T, isRemoving: Boolean, requestRemove: () -> Unit) -> Unit,
) {
    // --- State ---
    val removingItems = remember { mutableStateMapOf<String, Boolean>() }
    val coroutineScope = rememberCoroutineScope()

    // --- List ---
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        items(tabItems, key = { keyOf(it) }) { item ->
            val itemKey = keyOf(item)
            val isRemoving = removingItems[itemKey] == true

            // --- Removal animation ---
            AnimatedVisibility(
                visible = !isRemoving,
                exit = shrinkVertically(animationSpec = tween(removalDurationMillis)) +
                    fadeOut(animationSpec = tween(removalDurationMillis)),
            ) {
                itemContent(
                    item,
                    isRemoving,
                    {
                        if (!isRemoving) {
                            // 退出アニメーション完了後に削除契約を実行する。
                            removingItems[itemKey] = true
                            coroutineScope.launch {
                                delay(removalDurationMillis.toLong())
                                onRemoveConfirmed(item)
                                removingItems.remove(itemKey)
                            }
                        }
                    }
                )
            }
        }
    }
}
