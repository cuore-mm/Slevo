package com.websarva.wings.android.bbsviewer.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 画像URLのリストをサムネイルとして表示するグリッド。
 * 各サムネイルをタップすると [onImageClick] が呼び出される。
 */
@Composable
fun ImageThumbnailGrid(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit,
    viewModel: ImageThumbnailViewModel = hiltViewModel()
) {
    val stateMap by viewModel.imageStates.collectAsState()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUrls.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { url ->
                    LaunchedEffect(url) { viewModel.loadImage(url) }
                    val itemState = stateMap[url]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onImageClick(url) }
                    ) {
                        itemState?.bitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (itemState == null || itemState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    if (itemState?.total?.let { it > 0L } == true) {
                                        Text(
                                            text = "${formatBytes(itemState.downloaded)} / ${formatBytes(itemState.total)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

