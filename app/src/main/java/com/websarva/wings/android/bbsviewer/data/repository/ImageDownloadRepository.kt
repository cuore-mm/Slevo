package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.remote.ImageDownloadRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.ImageDownloadState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageDownloadRepository @Inject constructor(
    private val remote: ImageDownloadRemoteDataSource
) {
    fun downloadImage(url: String): Flow<ImageDownloadState> = remote.downloadImage(url)
}
