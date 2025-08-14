package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.remote.ImageDownloadRemoteDataSource
import javax.inject.Inject

class ImageDownloadRepository @Inject constructor(
    private val remote: ImageDownloadRemoteDataSource
) {
    fun downloadImage(url: String) = remote.downloadImage(url)
}

