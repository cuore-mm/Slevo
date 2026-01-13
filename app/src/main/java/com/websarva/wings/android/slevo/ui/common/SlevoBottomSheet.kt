package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * カスタムボトムシート。
 * ドラッグハンドルや背景色などのスタイルを統一している。
 *
 * @param onDismissRequest シートを閉じるときのコールバック
 * @param modifier 修飾子
 * @param sheetState シートの状態
 * @param sheetMaxWidth シートの最大幅
 * @param sheetGesturesEnabled ジェスチャー操作の有効・無効
 * @param shape シートの形状
 * @param containerColor コンテナの背景色
 * @param contentColor コンテンツの色
 * @param tonalElevation エレベーション
 * @param scrimColor 背景のスクリムの色
 * @param dragHandle ドラッグハンドル
 * @param contentWindowInsets ウィンドウインセット
 * @param properties シートのプロパティ
 * @param content シートの内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlevoBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { SlevoDragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = content,
    )
}

/**
 * ボトムシートのドラッグハンドル。
 * アクセシビリティ上の「操作できる」扱いを消去し、不要な読み上げを抑制している。
 */
@Composable
private fun SlevoDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() } // down/move/up全部食う
                        }
                    }
                }
                .clearAndSetSemantics { } // 「操作できる」扱いを消す
        )
    }
}

/**
 * ボトムシートのタイトルを表示する。
 *
 * @param text タイトル文字列
 */
@Composable
fun BottomSheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
}

/**
 * ボトムシート内の1行のアイテムを表示する。
 * アイコンがnullの場合はアイコンを表示しないが、テキストの位置はアイコンがある場合と揃える。
 *
 * @param text 表示するテキスト
 * @param icon 表示するアイコン（nullの場合は非表示）
 * @param onClick クリック時の処理
 */
@Composable
fun BottomSheetListItem(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // アイコンがない場合でもテキストの位置を揃えるために空のスペースを確保 (Iconのデフォルトサイズは24dp)
            Spacer(modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun SlevoBottomSheetPreview() {
    MaterialTheme {
        SlevoBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "スレッドメニュー",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()

                ListItem(headlineContent = { Text("URLをコピー") })
                ListItem(headlineContent = { Text("ブラウザで開く") })
                ListItem(
                    headlineContent = {
                        Text("削除（危険）", color = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomSheetTitlePreview() {
    BottomSheetTitle(text = "No. 1")
}

@Preview(showBackground = true)
@Composable
private fun BottomSheetListItemPreview() {
    Column {
        BottomSheetListItem(
            text = "Copy with Icon",
            icon = Icons.Outlined.ContentCopy,
            onClick = {}
        )
        BottomSheetListItem(
            text = "Copy without Icon",
            icon = null,
            onClick = {}
        )
    }
}
