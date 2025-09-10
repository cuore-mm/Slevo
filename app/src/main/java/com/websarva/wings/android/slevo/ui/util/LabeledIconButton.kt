package com.websarva.wings.android.slevo.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Stable
object AppIndication {
    // アプリ統一で使い回す大きめリップル（bounded=false で外へ広げる／radius 指定でサイズ固定）
    val LargeRipple = ripple(bounded = false, radius = 48.dp)
}

@Composable
fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
        modifier = modifier
            .size(width = 80.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = AppIndication.LargeRipple,
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = label)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.Companion.padding(top = 4.dp)
        )
    }
}
