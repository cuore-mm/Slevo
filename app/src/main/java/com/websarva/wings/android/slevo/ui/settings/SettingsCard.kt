package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * 設定画面のカードUIコンポーネント。
 *
 * @param modifier 追加の修飾子
 * @param onClick カードがクリックされたときのコールバック。nullまたは`enabled=false` の場合、カードはクリック不可になる。
 * @param enabled カードを有効化するかどうか（false のとき見た目を薄くしてクリック不可にする）
 * @param content カード内に表示するコンテンツ
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp)
    // disabled の場合は見た目を薄くする
    val effectiveModifier = if (enabled) cardModifier else cardModifier.alpha(0.5f)
    val shape = MaterialTheme.shapes.largeIncreased
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )

    // onClick は enabled が true のときのみ有効にする
    if (onClick != null && enabled) {
        Card(
            modifier = effectiveModifier,
            onClick = onClick,
            shape = shape,
            colors = colors,
        ) {
            content()
        }
    } else {
        Card(
            modifier = effectiveModifier,
            shape = shape,
            colors = colors,
        ) {
            content()
        }
    }
}
