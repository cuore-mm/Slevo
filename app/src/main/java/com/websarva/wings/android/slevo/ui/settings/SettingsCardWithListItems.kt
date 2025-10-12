package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
)

data class SwitchSpec(
    val modifier: Modifier = Modifier.scale(0.8f),
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val enabled: Boolean = true,
)

// Textベース定義を手早く作るためのファクトリ（拡張）
// 見出しの太さなどは引数で調整可能にしておく
@Composable
fun listItemSpecOfBasic(
    headlineText: String,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    switchSpec: SwitchSpec? = null,
    onClick: (() -> Unit)? = null,
    headlineStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium
    ),
    supportingStyle: TextStyle = MaterialTheme.typography.labelLarge,
): ListItemSpec {
    val haptic = LocalHapticFeedback.current
    val trailingContent: (@Composable () -> Unit)? = switchSpec?.let { spec ->
        {
            Switch(
                modifier = spec.modifier,
                checked = spec.checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    spec.onCheckedChange(it)
                },
                enabled = spec.enabled,
            )
        }
    }
    return ListItemSpec(
        leadingContent = leadingContent,
        headlineContent = { Text(text = headlineText, style = headlineStyle) },
        supportingContent = supportingText?.let { { Text(text = it, style = supportingStyle) } },
        trailingContent = trailingContent,
        onClick = switchSpec?.let {
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick?.invoke()
            }
        } ?: onClick,
    )
}

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
                val modifier = if (cardEnabled && spec.onClick != null) {
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
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                }
            }
        }
    }
}
