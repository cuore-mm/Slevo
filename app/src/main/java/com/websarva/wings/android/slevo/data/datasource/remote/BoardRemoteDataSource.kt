package com.websarva.wings.android.slevo.data.datasource.remote

data class SubjectFetchResult(
    val body: String?,
    val etag: String?,
    val lastModified: String?,
    val statusCode: Int
)

interface BoardRemoteDataSource {
    suspend fun fetchSubjectTxt(
        url: String,
        etag: String?,
        lastModified: String?,
        onProgress: (Float) -> Unit = {},
    ): SubjectFetchResult?

    suspend fun fetchSettingTxt(url: String): String?
}
