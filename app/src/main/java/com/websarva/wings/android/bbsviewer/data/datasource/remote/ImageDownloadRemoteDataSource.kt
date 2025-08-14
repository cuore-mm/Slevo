package com.websarva.wings.android.bbsviewer.data.datasource.remote

import com.websarva.wings.android.bbsviewer.data.model.ImageDownloadState
import kotlinx.coroutines.flow.Flow

interface ImageDownloadRemoteDataSource {
    fun downloadImage(url: String): Flow<ImageDownloadState>
}
