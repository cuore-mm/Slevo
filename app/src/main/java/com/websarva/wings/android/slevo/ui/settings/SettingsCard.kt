package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 設定画面のカードUIコンポーネント。
 *
 * @param modifier 追加の修飾子
 * @param onClick カードがクリックされたときのコールバック。nullの場合、カードはクリック不可になる。
 * @param content カード内に表示するコンテンツ
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp)
    val shape = MaterialTheme.shapes.extraLarge
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            onClick = onClick,
            shape = shape,
            colors = colors,
        ) {
            content()
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
        ) {
            content()
        }
    }
}
