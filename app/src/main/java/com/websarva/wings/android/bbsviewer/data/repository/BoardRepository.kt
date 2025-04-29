package com.websarva.wings.android.bbsviewer.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.data.datasource.local.BoardRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.util.ThreadListParser.parseSubjectTxt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardRepository @Inject constructor(
    private val remote: BoardRemoteDataSource
) {
    /**
     * subject.txt を取ってパースし、最新のリストを返す（更新なしでも常に List を返す）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getThreadList(subjectUrl: String): List<ThreadInfo> {
        val text = remote.fetchSubjectTxt(subjectUrl) ?: return emptyList()
        return parseSubjectTxt(text)
    }
}
