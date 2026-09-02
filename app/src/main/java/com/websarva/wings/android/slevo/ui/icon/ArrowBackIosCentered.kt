package com.websarva.wings.android.slevo.ui.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Material の ArrowBackIos を 24 x 24 の viewport 内で中央に配置したアイコン。
 */
val ArrowBackIosCentered: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowBackIosCentered",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    ).apply {
        group(
            translationX = 5f,
        ) {
            path(
                fill = SolidColor(Color.Black),
            ) {
                moveTo(11.67f, 3.87f)
                lineTo(9.9f, 2.1f)
                lineTo(0f, 12f)
                lineToRelative(9.9f, 9.9f)
                lineToRelative(1.77f, -1.77f)
                lineTo(3.54f, 12f)
                close()
            }
        }
    }.build()
}


@Preview(showBackground = true)
@Composable
private fun ArrowBackIosComparisonPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )

        Icon(
            imageVector = ArrowBackIosCentered,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}
