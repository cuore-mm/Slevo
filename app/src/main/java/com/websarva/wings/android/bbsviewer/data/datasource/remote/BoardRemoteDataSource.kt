package com.websarva.wings.android.bbsviewer.data.datasource.remote

interface BoardRemoteDataSource {
    suspend fun fetchSubjectTxt(url: String, forceRefresh: Boolean = false): String?
}
