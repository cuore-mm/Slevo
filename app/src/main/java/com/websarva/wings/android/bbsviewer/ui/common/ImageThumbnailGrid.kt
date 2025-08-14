package com.websarva.wings.android.bbsviewer.ui.common

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

/**
 * 画像URLのリストをサムネイルとして表示するグリッド。
 * 各サムネイルをタップすると [onImageClick] が呼び出される。
 */
@Composable
fun ImageThumbnailGrid(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUrls.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { url ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onImageClick(url) }
                    ) {
                        var isLoading by remember(url) { mutableStateOf(true) }
                        var downloaded by remember(url) { mutableStateOf(0L) }
                        var total by remember(url) { mutableStateOf(0L) }
                        var imageBitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                        val client = remember { OkHttpClient() }

                        LaunchedEffect(url) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val request = Request.Builder().url(url).build()
                                    client.newCall(request).execute().use { response ->
                                        val body = response.body ?: return@use
                                        withContext(Dispatchers.Main) {
                                            total = body.contentLength()
                                        }
                                        val stream = body.byteStream()
                                        val buffer = ByteArray(8 * 1024)
                                        val output = ByteArrayOutputStream()
                                        var bytesRead: Int
                                        var sum = 0L
                                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            sum += bytesRead
                                            withContext(Dispatchers.Main) { downloaded = sum }
                                        }
                                        val bytes = output.toByteArray()
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        withContext(Dispatchers.Main) {
                                            imageBitmap = bmp?.asImageBitmap()
                                            isLoading = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { isLoading = false }
                                }
                            }
                        }

                        imageBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    if (total > 0L) {
                                        Text(
                                            text = "${formatBytes(downloaded)} / ${formatBytes(total)}",
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

