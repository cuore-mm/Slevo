package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.ui.util.GestureHint

@Composable
fun GestureHintOverlay(
    state: GestureHint,
    modifier: Modifier = Modifier,
) {
    // Surface の共通サイズを定義（幅を固定、最小高さを確保）
    // （具体的な Surface は下の OverlaySurface で共通化）

    when (state) {
        is GestureHint.Hidden -> Unit
        is GestureHint.Direction -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            OverlaySurface {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(id = state.direction.iconRes),
                        contentDescription = stringResource(id = state.direction.labelRes),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    val message = state.action?.let { action ->
                        stringResource(id = action.labelRes)
                    } ?: stringResource(id = R.string.gesture_action_unassigned_message)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        GestureHint.Invalid -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            OverlaySurface {
                Text(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    text = stringResource(id = R.string.gesture_invalid_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

        }
    }
}

/**
 * 共通のオーバーレイ Surface を生成するヘルパー。
 * 幅を固定し最小高さを確保、半透明の背景色を適用します。
 */
@Composable
private fun OverlaySurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .width(240.dp)
            .heightIn(min = 120.dp),
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GestureHintOverlay_DirectionPreview() {
    GestureHintOverlay(
        state = GestureHint.Direction(
            direction = GestureDirection.Right,
            action = GestureAction.ToTop,
        ),
        modifier = Modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GestureHintOverlay_InvalidPreview() {
    GestureHintOverlay(
        state = GestureHint.Invalid,
        modifier = Modifier,
    )
}
