package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * 上部はタイトル・追加情報スロット・閉じるボタン、下部は主要タイトルを表示する。
 */
@Composable
internal fun TabListCard(
    modifier: Modifier = Modifier,
    accentColor: Color?,
    onClick: () -> Unit,
    headerTitle: String,
    headerTrailingContent: @Composable RowScope.() -> Unit = {},
    bodyTitle: String,
    onCloseClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // --- Accent bar ---
            if (accentColor != null) {
                Spacer(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .background(
                            color = accentColor,
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        )
                )
            }

            // --- Card body ---
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
                        headerTrailingContent()
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                )
                                .size(20.dp),
                            onClick = {
                                // タブクローズ操作は一覧遷移より優先して処理する。
                                onCloseClick()
                            }
                        ) {
                            Icon(
                                modifier = Modifier.size(14.dp),
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                }
                // --- Body ---
                Text(
                    text = bodyTitle,
                    style = MaterialTheme.typography.bodyLarge,
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
        headerTrailingContent = {
            Text(
                text = "120",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "+3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        },
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
