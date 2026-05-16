package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.ui.common.AnchoredSelectionMenu
import com.websarva.wings.android.slevo.ui.common.SelectionMenuOption
import kotlin.math.roundToInt

/**
 * ListItem の仕様を型として保持するデータクラス。
 */
data class ListItemSpec(
    val leadingContent: (@Composable () -> Unit)? = null,
    val headlineContent: (@Composable () -> Unit),
    val supportingContent: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val itemModifier: Modifier = Modifier,
    val overlayContent: (@Composable () -> Unit)? = null,
)

data class SwitchSpec(
    val modifier: Modifier = Modifier.scale(0.8f),
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val enabled: Boolean = true,
)

/**
 * supporting text の用途を表す表示ロール。
 */
enum class SupportingTextRole {
    /** 現在選択中の値を示すテキスト。 */
    SelectedValue,

    /** 補足説明を示すテキスト。 */
    Description,
}

// Textベース定義を手早く作るためのファクトリ（拡張）
// 見出しの太さなどは引数で調整可能にしておく
@Composable
fun listItemSpecOfBasic(
    headlineText: String,
    supportingText: String? = null,
    supportingTextRole: SupportingTextRole = SupportingTextRole.Description,
    leadingContent: (@Composable () -> Unit)? = null,
    switchSpec: SwitchSpec? = null,
    onClick: (() -> Unit)? = null,
    headlineStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium
    ),
    customSupportingStyle: TextStyle? = null,
): ListItemSpec {
    val haptic = LocalHapticFeedback.current
    val supportingStyle = customSupportingStyle ?: when (supportingTextRole) {
        SupportingTextRole.SelectedValue -> MaterialTheme.typography.labelLarge.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Normal,
        )

        SupportingTextRole.Description -> MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Normal,
        )
    }

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
 * 1つの設定項目にアンカー付き単一選択メニューを関連付けた `ListItemSpec` を作成する。
 *
 * メニューの表示位置は項目の描画領域をアンカーとして決定する。
 */
@Composable
fun <T> listItemSpecOfSelectionMenu(
    headlineText: String,
    supportingText: String,
    selectedValue: T,
    options: List<SelectionMenuOption<T>>,
    onSelect: (T) -> Unit,
    supportingTextRole: SupportingTextRole = SupportingTextRole.SelectedValue,
    headlineStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium
    ),
): ListItemSpec {
    var isExpanded by remember { mutableStateOf(false) }
    var anchorBoundsInWindow by remember { mutableStateOf<IntRect?>(null) }

    val baseSpec = listItemSpecOfBasic(
        headlineText = headlineText,
        supportingText = supportingText,
        supportingTextRole = supportingTextRole,
        onClick = { isExpanded = true },
        headlineStyle = headlineStyle,
    )

    return baseSpec.copy(
        itemModifier = Modifier.onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInWindow()
            anchorBoundsInWindow = IntRect(
                left = rect.left.roundToInt(),
                top = rect.top.roundToInt(),
                right = rect.right.roundToInt(),
                bottom = rect.bottom.roundToInt(),
            )
        },
        overlayContent = {
            AnchoredSelectionMenu(
                expanded = isExpanded,
                anchorBoundsInWindow = anchorBoundsInWindow,
                options = options,
                selectedValue = selectedValue,
                onSelect = onSelect,
                onDismissRequest = { isExpanded = false },
            )
        }
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
                    spec.itemModifier
                        .fillMaxWidth()
                        .clickable { spec.onClick.invoke() }
                } else {
                    spec.itemModifier.fillMaxWidth()
                }

                Box {
                    ListItem(
                        modifier = modifier,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = spec.leadingContent,
                        headlineContent = spec.headlineContent,
                        supportingContent = spec.supportingContent,
                        trailingContent = spec.trailingContent,
                    )
                    spec.overlayContent?.invoke()
                }

                if (index != items.lastIndex) {
                    val dividerStartPadding = if (spec.leadingContent != null) 56.dp else 16.dp
                    HorizontalDivider(
                        modifier = Modifier.padding(start = dividerStartPadding, end = 16.dp)
                    )
                }
            }
        }
    }
}
