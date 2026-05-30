package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme

/**
 * 汎用アンカー選択メニューで使用する単一選択肢。
 */
data class SelectionMenuOption<T>(
    val value: T,
    val label: String,
)

/**
 * アンカー位置に重ねる単一選択メニュー。
 *
 * 選択済み項目は primary color / 太字 / 右端チェックアイコンで表示する。
 */
@Composable
fun <T> AnchoredSelectionMenu(
    expanded: Boolean,
    anchorBoundsInWindow: IntRect?,
    options: List<SelectionMenuOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AnchoredOverlayMenu(
        expanded = expanded,
        anchorBoundsInWindow = anchorBoundsInWindow,
        hazeState = null,
        horizontalAlignment = HorizontalAnchorAlignment.Start,
        verticalAlignment = VerticalAnchorAlignment.OverlapTop,
        verticalSpacing = 32.dp,
        onDismissRequest = onDismissRequest,
    ) {
        options.forEach { option ->
            val isSelected = option.value == selectedValue
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // 選択通知後にメニューを閉じる。
                        onSelect(option.value)
                        onDismissRequest()
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.label,
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    fontWeight = fontWeight,
                )
                Spacer(modifier = Modifier.width(24.dp))
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnchoredSelectionMenuPreview() {
    SlevoTheme {
        AnchoredSelectionMenu(
            expanded = true,
            anchorBoundsInWindow = IntRect(320, 80, 420, 128),
            options = listOf(
                SelectionMenuOption("light", "ライト"),
                SelectionMenuOption("dark", "ダーク"),
                SelectionMenuOption("system", "システムテーマに従う"),
            ),
            selectedValue = "system",
            onSelect = {},
            onDismissRequest = {},
        )
    }
}
