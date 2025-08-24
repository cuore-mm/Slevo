package com.websarva.wings.android.slevo.ui.bottombar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R

@Composable
fun BookmarkSelectBottomBar(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onOpen: () -> Unit
) {
    BottomAppBar(
        modifier = modifier.height(56.dp),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                BottomBarItem(
                    icon = Icons.Default.Star,
                    label = stringResource(R.string.edit),
                    onClick = onEdit
                )
//                BottomBarItem(
//                    icon = Icons.Default.OpenInBrowser,
//                    label = "開く",
//                    onClick = onOpen
//                )
            }
        }
    )
}

@Composable
fun BbsSelectBottomBar(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    BottomAppBar(
        modifier = modifier.height(56.dp),
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                BottomBarItem(
                    icon = Icons.Default.Delete,
                    label = "削除",
                    onClick = onDelete
                )
//                BottomBarItem(
//                    icon = Icons.Default.OpenInBrowser,
//                    label = "開く",
//                    onClick = onOpen
//                )
            }
        }
    )
}

@Composable
fun BottomBarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BookmarkSelectBottomBarPreview() {
    BookmarkSelectBottomBar(
        onEdit = {},
        onOpen = {}
    )
}

@Preview(showBackground = true)
@Composable
fun BbsSelectBottomBarPreview() {
    BbsSelectBottomBar(
        onDelete = {},
        onOpen = {}
    )
}
