package com.websarva.wings.android.bbsviewer.data.model

sealed class ImageDownloadState {
    data class Progress(val downloaded: Long, val total: Long) : ImageDownloadState()
    data class Success(val data: ByteArray) : ImageDownloadState()
    data class Error(val throwable: Throwable? = null) : ImageDownloadState()
}
