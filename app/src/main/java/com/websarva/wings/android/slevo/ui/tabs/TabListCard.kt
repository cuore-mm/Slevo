package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.net.URI

/**
 * タブ一覧カードに共通する外枠とアクセントバーを描画する。
 *
 * ヘッダと本文の内容は呼び出し側で指定し、板/スレで表示内容を切り替える。
 */
@Composable
internal fun TabListCard(
    modifier: Modifier = Modifier,
    accentColor: Color?,
    onClick: () -> Unit,
    headerContent: @Composable RowScope.() -> Unit,
    bodyContent: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
    ) {
        // --- Accent bar ---
        if (accentColor != null) {
            Spacer(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // --- Card body ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // --- Header ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    headerContent()
                }
                // --- Body ---
                bodyContent()
            }
        }
    }
}

/**
 * 板URLからサービス名に相当するホスト名を取り出す。
 */
internal fun extractServiceName(boardUrl: String): String {
    return runCatching { URI(boardUrl).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: boardUrl // URL解析に失敗した場合はそのまま表示する。
}
