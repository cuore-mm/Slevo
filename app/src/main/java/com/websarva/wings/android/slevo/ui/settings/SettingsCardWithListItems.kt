package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ListItem の仕様を型として保持するデータクラス。
 */
data class ListItemSpec(
    val leadingContent: (@Composable () -> Unit)? = null,
    val headlineContent: @Composable () -> Unit,
    val supportingContent: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val enabled: Boolean = true,
)

/**
 * 複数の ListItemSpec を受け取り、それらを `SettingsCard` で包むコンポーザブル。
 * アイテム間には `HorizontalDivider` を挿入します。
 */
@Composable
fun SettingsCardWithListItems(
    items: List<ListItemSpec>,
    cardEnabled: Boolean = true,
) {
    require(items.isNotEmpty()) { "SettingsCardWithListItems requires at least one ListItemSpec" }

    SettingsCard(enabled = cardEnabled) {
        Column {
            items.forEachIndexed { index, spec ->
                val itemEnabled = cardEnabled && spec.enabled
                val modifier = if (itemEnabled && spec.onClick != null) {
                    Modifier
                        .fillMaxWidth()
                        .clickable { spec.onClick.invoke() }
                } else {
                    Modifier.fillMaxWidth()
                }

                ListItem(
                    modifier = modifier,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = spec.leadingContent,
                    headlineContent = spec.headlineContent,
                    supportingContent = spec.supportingContent,
                    trailingContent = spec.trailingContent,
                )

                if (index != items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp, end = 16.dp))
                }
            }
        }
    }
}
