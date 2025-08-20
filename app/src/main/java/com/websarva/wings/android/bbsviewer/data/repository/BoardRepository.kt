package com.websarva.wings.android.bbsviewer.data.repository

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadSummaryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardVisitDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardFetchMetaDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadSummaryEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardVisitEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardFetchMetaEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.AppDatabase
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BoardRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.util.ThreadListParser.parseSubjectTxt
import com.websarva.wings.android.bbsviewer.data.util.ThreadListParser.calculateThreadDate
import com.websarva.wings.android.bbsviewer.ui.util.parseServiceName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardRepository @Inject constructor(
    private val remote: BoardRemoteDataSource,
    private val serviceDao: BbsServiceDao,
    private val boardDao: BoardDao,
    private val threadSummaryDao: ThreadSummaryDao,
    private val boardVisitDao: BoardVisitDao,
    private val fetchMetaDao: BoardFetchMetaDao,
    private val db: AppDatabase,
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun observeThreads(boardId: Long): Flow<List<ThreadInfo>> {
        val baselineFlow = boardVisitDao.observeBaseline(boardId)
            .distinctUntilChanged()
        val threadsFlow = threadSummaryDao.observeThreadSummaries(boardId)
            .distinctUntilChanged()
        val metaFlow = fetchMetaDao.observe(boardId)
            .distinctUntilChanged()
        return combine(threadsFlow, baselineFlow, metaFlow) { summaries, baseline, meta ->
            val base = baseline ?: 0L
            val currentUnixTime = (meta?.lastFetchedAt ?: 0L) / 1000
            summaries.map { summary ->
                val date = if (summary.threadId.toLongOrNull()?.let { it < com.websarva.wings.android.bbsviewer.data.model.THREAD_KEY_THRESHOLD } == true) {
                    calculateThreadDate(summary.threadId)
                } else {
                    com.websarva.wings.android.bbsviewer.data.model.ThreadDate(0, 0, 0, 0, 0, "")
                }
                val momentum = if (
                    summary.threadId.toLongOrNull()?.let { it < com.websarva.wings.android.bbsviewer.data.model.THREAD_KEY_THRESHOLD } == true &&
                    summary.resCount > 0 &&
                    currentUnixTime > 0
                ) {
                    val elapsed = (currentUnixTime - (summary.threadId.toLong()))
                    val days = elapsed / 86400.0
                    if (days > 0) summary.resCount / days else 0.0
                } else 0.0
                ThreadInfo(
                    title = summary.title,
                    key = summary.threadId,
                    resCount = summary.resCount,
                    date = date,
                    momentum = momentum,
                    isNew = summary.firstSeenAt > base
                )
            }
        }
    }

    suspend fun updateBaseline(boardId: Long, baselineAt: Long) {
        boardVisitDao.upsert(BoardVisitEntity(boardId, baselineAt))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun refreshThreadList(
        boardId: Long,
        subjectUrl: String,
        refreshStartAt: Long,
        isManual: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val meta = fetchMetaDao.get(boardId)
        val result = remote.fetchSubjectTxt(subjectUrl, meta?.etag, meta?.lastModified) ?: return@withContext false
        val now = System.currentTimeMillis()
        when (result.statusCode) {
            304 -> {
                db.withTransaction {
                    fetchMetaDao.upsert(
                        BoardFetchMetaEntity(boardId, result.etag ?: meta?.etag, result.lastModified ?: meta?.lastModified, now)
                    )
                    if (isManual) {
                        boardVisitDao.upsert(BoardVisitEntity(boardId, refreshStartAt))
                    }
                }
                true
            }
            200 -> {
                val threads = parseSubjectTxt(result.body ?: return@withContext false)
                db.withTransaction {
                    val existingIds = threadSummaryDao.getThreadIds(boardId)
                    val newIds = mutableListOf<String>()
                    val inserts = mutableListOf<ThreadSummaryEntity>()
                    threads.forEachIndexed { index, t ->
                        newIds.add(t.key)
                        if (t.key in existingIds) {
                            threadSummaryDao.updateExisting(boardId, t.key, t.title, t.resCount, index)
                        } else {
                            inserts.add(
                                ThreadSummaryEntity(
                                    boardId = boardId,
                                    threadId = t.key,
                                    title = t.title,
                                    resCount = t.resCount,
                                    firstSeenAt = now,
                                    isArchived = false,
                                    subjectRank = index
                                )
                            )
                        }
                    }
                    if (inserts.isNotEmpty()) threadSummaryDao.insertAll(inserts)
                    val removed = existingIds.minus(newIds.toSet())
                    if (removed.isNotEmpty()) threadSummaryDao.markArchived(boardId, removed)
                    fetchMetaDao.upsert(
                        BoardFetchMetaEntity(boardId, result.etag, result.lastModified, now)
                    )
                    if (isManual) {
                        boardVisitDao.upsert(BoardVisitEntity(boardId, refreshStartAt))
                    }
                }
                true
            }
            else -> false
        }
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
