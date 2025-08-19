package com.websarva.wings.android.bbsviewer.data.datasource.remote

data class SubjectFetchResult(
    val body: String?,
    val etag: String?,
    val lastModified: String?,
    val statusCode: Int
)

interface BoardRemoteDataSource {
    suspend fun fetchSubjectTxt(url: String, etag: String?, lastModified: String?): SubjectFetchResult?

    suspend fun fetchSettingTxt(url: String): String?
}
