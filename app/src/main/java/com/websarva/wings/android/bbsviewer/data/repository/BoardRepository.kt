package com.websarva.wings.android.bbsviewer.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BoardRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.util.ThreadListParser.parseSubjectTxt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardRepository @Inject constructor(
    private val remote: BoardRemoteDataSource
) {
    /**
     * subject.txt を取ってパースし、最新のリストを返す。
     * 304 (Not Modified) の場合は null を返す。
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getThreadList(subjectUrl: String, forceRefresh: Boolean = false): List<ThreadInfo>? {
        val text = remote.fetchSubjectTxt(subjectUrl, forceRefresh) ?: return null
        return parseSubjectTxt(text)
    }
}
