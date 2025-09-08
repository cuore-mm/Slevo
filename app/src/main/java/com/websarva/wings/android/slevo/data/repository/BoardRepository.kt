package com.websarva.wings.android.slevo.data.repository

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.ThreadSummaryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardVisitDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardFetchMetaDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.ThreadSummaryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.BoardVisitEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.BoardFetchMetaEntity
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.remote.BoardRemoteDataSource
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.util.ThreadListParser.parseSubjectTxt
import com.websarva.wings.android.slevo.data.util.ThreadListParser.calculateThreadDate
import com.websarva.wings.android.slevo.ui.util.parseServiceName
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
    /**
     * 指定した板IDのスレッド一覧を監視するFlowを返す。
     * スレッド情報・基準時刻・メタ情報を組み合わせてThreadInfoリストを生成。
     */
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
                val date = if (summary.threadId.toLongOrNull()?.let { it < com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD } == true) {
                    calculateThreadDate(summary.threadId)
                } else {
                    com.websarva.wings.android.slevo.data.model.ThreadDate(0, 0, 0, 0, 0, "")
                }
                val momentum = if (
                    summary.threadId.toLongOrNull()?.let { it < com.websarva.wings.android.slevo.data.model.THREAD_KEY_THRESHOLD } == true &&
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

    /**
     * 板の既読基準時刻を更新する。
     * @param boardId 板ID
     * @param baselineAt 新しい基準時刻
     */
    suspend fun updateBaseline(boardId: Long, baselineAt: Long) {
        boardVisitDao.upsert(BoardVisitEntity(boardId, baselineAt))
    }

    /**
     * subject.txtを取得し、スレッド一覧をDBに反映する。
     * ETag/Last-Modifiedによる差分取得対応。
     * @param boardId 板ID
     * @param subjectUrl subject.txtのURL
     * @param refreshStartAt 取得開始時刻
     * @param isManual 手動更新かどうか
     * @param onProgress 進捗コールバック
     * @return 成功時true
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun refreshThreadList(
        boardId: Long,
        subjectUrl: String,
        refreshStartAt: Long,
        isManual: Boolean,
        onProgress: (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val meta = fetchMetaDao.get(boardId)
        val result = remote.fetchSubjectTxt(subjectUrl, meta?.etag, meta?.lastModified, onProgress)
            ?: return@withContext false
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

    /**
     * setting.txtから板名(BBS_TITLE)を取得する。
     * @param settingUrl setting.txtのURL
     * @return 板名
     */
    suspend fun fetchBoardName(settingUrl: String): String? {
        val text = remote.fetchSettingTxt(settingUrl) ?: return null
        return text.lines()
            .firstOrNull { it.startsWith("BBS_TITLE=") }
            ?.substringAfter("=")
    }

    /**
     * setting.txtから名無し名(BBS_NONAME_NAME)を取得する。
     * @param settingUrl setting.txtのURL
     * @return 名無し名
     */
    suspend fun fetchBoardNoname(settingUrl: String): String? {
        val text = remote.fetchSettingTxt(settingUrl) ?: return null
        return text.lines()
            .firstOrNull { it.startsWith("BBS_NONAME_NAME=") }
            ?.substringAfter("=")
    }

    /**
     * boardUrl から既存の板情報を取得する。
     * @param boardUrl 検索対象のURL
     * @return 該当する [BoardEntity] があれば返す
     */
    suspend fun findBoardByUrl(boardUrl: String): BoardEntity? =
        boardDao.findBoardByUrl(boardUrl)

    /**
     * 指定した板情報をDBに登録し、そのIDを返す。
     * 既存の場合はIDのみ返す。
     * @param boardInfo 板情報
     * @return 板ID
     */
    suspend fun ensureBoard(boardInfo: BoardInfo): Long = withContext(Dispatchers.IO) {
        // 既存の板IDが0でなければそのまま返す
        var bId = boardInfo.boardId
        if (bId == 0L) {
            // サービス名をURLから抽出
            val serviceName = parseServiceName(boardInfo.url)
            // サービス情報がDBに存在しなければ新規登録
            val service = serviceDao.findByDomain(serviceName) ?: run {
                val svc = BbsServiceEntity(domain = serviceName, displayName = serviceName, menuUrl = null)
                val id = serviceDao.upsert(svc)
                svc.copy(serviceId = id)
            }
            // 板情報をDBに登録（既存の場合はIDのみ取得）
            val insertedId = boardDao.insertBoard(
                BoardEntity(
                    serviceId = service.serviceId,
                    url = boardInfo.url,
                    name = boardInfo.name
                )
            )
            // 挿入成功ならそのID、失敗ならURLで再検索
            bId = if (insertedId != -1L) insertedId else boardDao.findBoardIdByUrl(boardInfo.url)
        }
        // 板IDを返す
        bId
    }
}
