package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import java.net.URI

/**
 * タブ一覧カードの共通外枠と情報配置を提供する。
 *
 * 上部はタイトル・メタ情報・閉じるボタン、下部は主要タイトルを表示する。
 */
@Composable
internal fun TabListCard(
    modifier: Modifier = Modifier,
    accentColor: Color?,
    onClick: () -> Unit,
    headerTitle: String,
    headerMeta: String?,
    headerBadgeText: String?,
    bodyTitle: String,
    onCloseClick: () -> Unit,
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (headerMeta != null) {
                            Text(
                                text = headerMeta,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (headerBadgeText != null) {
                            // 新着バッジは板画面の強調スタイルに合わせる。
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = headerBadgeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(999.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                // タブクローズ操作は一覧遷移より優先して処理する。
                                onCloseClick()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                }
                // --- Body ---
                Text(
                    text = bodyTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TabListCardPreview() {
    TabListCard(
        modifier = Modifier.padding(12.dp),
        accentColor = MaterialTheme.colorScheme.primary,
        onClick = {},
        headerTitle = "example.com",
        headerMeta = "120",
        headerBadgeText = "+3",
        bodyTitle = "カードのタイトル",
        onCloseClick = {},
    )
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
