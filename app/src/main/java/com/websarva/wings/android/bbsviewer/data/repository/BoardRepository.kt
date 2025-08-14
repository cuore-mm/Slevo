package com.websarva.wings.android.bbsviewer.data.repository

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BoardRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.util.ThreadListParser.parseSubjectTxt
import com.websarva.wings.android.bbsviewer.ui.util.parseServiceName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardRepository @Inject constructor(
    private val remote: BoardRemoteDataSource,
    private val serviceDao: BbsServiceDao,
    private val boardDao: BoardDao,
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

    suspend fun fetchBoardName(settingUrl: String): String? {
        val text = remote.fetchSettingTxt(settingUrl) ?: return null
        Log.d("BoardRepository", "Fetched setting text: $text")
        return text.lines()
            .firstOrNull { it.startsWith("BBS_TITLE=") }
            ?.substringAfter("=")
    }

    suspend fun fetchBoardNoname(settingUrl: String): String? {
        val text = remote.fetchSettingTxt(settingUrl) ?: return null
        return text.lines()
            .firstOrNull { it.startsWith("BBS_NONAME_NAME=") }
            ?.substringAfter("=")
    }

    /**
     * 指定した板を boards テーブルに登録し、その ID を返す。
     * 既に存在する場合はその ID を返すのみ。
     */
    suspend fun ensureBoard(boardInfo: BoardInfo): Long = withContext(Dispatchers.IO) {
        var bId = boardInfo.boardId
        if (bId == 0L) {
            val serviceName = parseServiceName(boardInfo.url)
            val service = serviceDao.findByDomain(serviceName) ?: run {
                val svc = BbsServiceEntity(domain = serviceName, displayName = serviceName, menuUrl = null)
                val id = serviceDao.upsert(svc)
                svc.copy(serviceId = id)
            }
            val insertedId = boardDao.insertBoard(
                BoardEntity(
                    serviceId = service.serviceId,
                    url = boardInfo.url,
                    name = boardInfo.name
                )
            )
            bId = if (insertedId != -1L) insertedId else boardDao.findBoardIdByUrl(boardInfo.url)
        }
        bId
    }
}
