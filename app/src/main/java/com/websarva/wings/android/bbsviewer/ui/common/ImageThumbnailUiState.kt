package com.websarva.wings.android.bbsviewer.ui.common

import android.graphics.Bitmap

data class ImageThumbnailItemState(
    val bitmap: Bitmap? = null,
    val bytes: ByteArray? = null,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val isLoading: Boolean = true
)

data class ImageThumbnailUiState(
    val items: Map<String, ImageThumbnailItemState> = emptyMap()
)

