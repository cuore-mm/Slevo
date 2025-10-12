package com.websarva.wings.android.slevo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun GestureActionDialog(
    direction: GestureDirection,
    currentAction: GestureAction?,
    actions: List<GestureAction>,
    onDismissRequest: () -> Unit,
    onActionSelected: (GestureAction?) -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        GestureActionDialogContent(
            direction = direction,
            currentAction = currentAction,
            actions = actions,
            onActionSelected = onActionSelected,
        )
    }
}

@Composable
fun GestureActionDialogContent(
    direction: GestureDirection,
    currentAction: GestureAction?,
    actions: List<GestureAction>,
    onActionSelected: (GestureAction?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints {
        val maxH = this.maxHeight * 0.9f
        Card(
            modifier = modifier.heightIn(max = maxH),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 24.dp)
            ) {
                Text(
                    text = stringResource(id = direction.labelRes),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                HorizontalDivider()

                // 初期表示時に見せたいアイテム位置を計算
                val computedIndex = if (currentAction == null) {
                    0
                } else {
                    val idx = actions.indexOf(currentAction)
                    if (idx >= 0) idx else 0
                }
                // 総アイテム数（未割当ヘッダー + actions.size）
                val totalItems = actions.size + 1
                val initialIndex = computedIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))

                // 初期スクロール位置を指定して状態を作る（これにより最初からスクロール済みで描画される）
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)


                LazyColumnScrollbar(
                    state = listState,
                    settings = ScrollbarSettings.Default.copy(
                        selectionMode = ScrollbarSelectionMode.Disabled,
                        thumbUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        thumbThickness = 3.dp,                  // デフォルト 6.dp → 細く
                        scrollbarPadding = 0.dp,                  // デフォルト 8.dp → 端に寄せる
                    )
                ) {
                    LazyColumn(
                        state = listState,
                    ) {
                        item {
                            GestureActionSelectionRow(
                                label = stringResource(id = R.string.gesture_action_unassigned),
                                selected = currentAction == null,
                                onClick = { onActionSelected(null) }
                            )
                            Spacer(modifier = Modifier.Companion.height(4.dp))
                        }
                        itemsIndexed(actions) { index, action ->
                            GestureActionSelectionRow(
                                label = stringResource(id = action.labelRes),
                                selected = currentAction == action,
                                onClick = { onActionSelected(action) }
                            )
                            if (index != actions.lastIndex) {
                                Spacer(modifier = Modifier.Companion.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureActionSelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.Companion.width(8.dp))
        Text(text = label)
    }
}

@Preview(showBackground = true)
@Composable
private fun GestureActionDialogPreview() {
    val actions = GestureAction.entries.toList()
    MaterialTheme {
        GestureActionDialogContent(
            direction = GestureDirection.entries.first(),
            currentAction = actions.firstOrNull(),
            actions = actions,
            onActionSelected = {},
        )
    }
}
