package com.websarva.wings.android.slevo.data.repository

import androidx.core.net.toUri
import androidx.room.withTransaction
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardFetchMetaDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardVisitDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.ThreadSummaryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.BoardFetchMetaEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.BoardVisitEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.ThreadSummaryEntity
import com.websarva.wings.android.slevo.data.datasource.remote.BoardRemoteDataSource
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.util.ThreadDerivedInfo
import com.websarva.wings.android.slevo.data.util.ThreadInfoDerivedCalculator
import com.websarva.wings.android.slevo.data.util.ThreadListParser.parseSubjectTxt
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.parseServiceName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
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
    private val threadStateRepository: ThreadStateRepository,
    private val db: AppDatabase,
) {
    private companion object {
        /**
         * `boardId` バインド 1 件を含めても SQLite 変数上限に十分余裕を持たせるための分割サイズ。
         */
        const val SQL_VARIABLE_CHUNK_SIZE = 900
    }

    /**
     * 指定した板IDのスレッド一覧を監視するFlowを返す。
     * スレッド情報・基準時刻・メタ情報を組み合わせてThreadInfoリストを生成。
     */
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
            // --- Mapping ---
            // subject.txt 由来の要約を表示用 ThreadInfo に変換する。
            summaries.map { summary ->
                val derived = resolveThreadDerivedInfo(
                    threadKey = summary.threadId,
                    resCount = summary.resCount,
                    nowSeconds = currentUnixTime,
                )
                ThreadInfo(
                    title = summary.title,
                    key = summary.threadId,
                    resCount = summary.resCount,
                    date = derived.date,
                    momentum = derived.momentum,
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
                        BoardFetchMetaEntity(
                            boardId,
                            result.etag ?: meta?.etag,
                            result.lastModified ?: meta?.lastModified,
                            now
                        )
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
                    val boardEntity = boardDao.findBoardById(boardId)
                    val boardKey = boardEntity?.url?.let { parseBoardUrl(it) }
                    val existingIds = threadSummaryDao.getAllThreadIds(boardId).toHashSet()
                    val newIds = mutableListOf<String>()
                    val inserts = mutableListOf<ThreadSummaryEntity>()
                    threads.forEachIndexed { index, t ->
                        newIds.add(t.key)
                        if (t.key in existingIds) {
                            threadSummaryDao.updateExisting(
                                boardId,
                                t.key,
                                t.title,
                                t.resCount,
                                index
                            )
                        } else {
                            inserts.add(
                                ThreadSummaryEntity(
                                    boardId = boardId,
                                    threadId = t.key,
                                    title = t.title,
                                    resCount = t.resCount,
                                    firstSeenAt = now,
                                    subjectRank = index
                                )
                            )
                        }
                    }
                    if (inserts.isNotEmpty()) threadSummaryDao.insertAll(inserts)
                    if (boardEntity != null && boardKey != null) {
                        // subject.txt 由来の一覧を、板キャッシュと同じ順序で共通客観状態へ反映する。
                        threadStateRepository.saveThreadStates(
                            threads.map { thread ->
                                ThreadStateRepository.ThreadStateUpdate(
                                    threadId = ThreadId.of(boardKey.first, boardKey.second, thread.key),
                                    boardId = boardId,
                                    boardUrl = boardEntity.url,
                                    boardName = boardEntity.name,
                                    title = thread.title,
                                    latestResCount = thread.resCount,
                                    updatedAt = now,
                                )
                            }
                        )
                    }
                    val removed = calculateRemovedThreadIds(existingIds.toList(), newIds)
                    deleteThreadSummariesInChunks(boardId, removed)
                    if (removed.isNotEmpty()) threadStateRepository.collectGarbage()
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
     * setting.txtから板名を取得する。
     * @param settingUrl setting.txtのURL
     * @return 板名
     */
    suspend fun fetchBoardName(settingUrl: String): String? {
        val text = remote.fetchSettingTxt(settingUrl) ?: return null
        val lines = text.lines()
        // --- Primary key lookup ---
        lines.firstOrNull { it.startsWith("BBS_TITLE_ORIG=") }
            ?.substringAfter("=")
            ?.let { return it }
        // --- Fallback ---
        return lines.firstOrNull { it.startsWith("BBS_TITLE=") }
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
     * boardKey から既存板のホストを取得する。
     *
     * `requiredDomain` が指定された場合は、そのドメインに一致するhostのみ返す。
     */
    suspend fun resolveHostByBoardKey(
        boardKey: String,
        requiredDomain: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        // --- Query ---
        val candidates = boardDao.findBoardsByUrlPattern("%/$boardKey/%")
        // --- Match ---
        val matched = candidates.firstOrNull { entity ->
            val segments = entity.url.toUri().pathSegments
            if (segments.firstOrNull() != boardKey) {
                return@firstOrNull false
            }

            if (requiredDomain == null) {
                return@firstOrNull true
            }

            // 入力元URLのドメイン制約がある場合のみ、host末尾一致を確認する。
            val host = entity.url.toUri().host ?: return@firstOrNull false
            host.endsWith(".$requiredDomain")
        } ?: return@withContext null
        matched.url.toUri().host
    }

    /**
     * 指定した板情報をDBに登録し、そのIDを返す。
     * 既存の場合はIDのみ返す。
     * @param boardInfo 板情報
     * @return 板ID
     */
    suspend fun ensureBoard(boardInfo: BoardInfo): Long = withContext(Dispatchers.IO) {
        // --- Guard ---
        if (boardInfo.boardId != 0L) {
            // 既に登録済みのため、そのまま返す。
            return@withContext boardInfo.boardId
        }
        // --- Existing lookup ---
        boardDao.findBoardByUrl(boardInfo.url)?.let { existing ->
            // 既存板がある場合は新規登録を行わない。
            return@withContext existing.boardId
        }

        // --- Service resolve ---
        val serviceName = parseServiceName(boardInfo.url)
        db.withTransaction {
            val existingService = serviceDao.findByDomain(serviceName)
            val service = existingService ?: run {
                val newService = BbsServiceEntity(
                    domain = serviceName,
                    displayName = serviceName,
                    menuUrl = null
                )
                serviceDao.insertService(newService)
                // 再取得して最終的な serviceId を確定させる。
                serviceDao.findByDomain(serviceName) ?: newService
            }

            // --- Validation ---
            if (service.serviceId == 0L) {
                // サービス未確定のため、板登録は行わず 0 を返す。
                return@withTransaction 0L
            }

            // --- Mapping ---
            // BoardInfo を永続化用の BoardEntity に変換する（URLは一意扱い）。
            val boardEntity = BoardEntity(
                serviceId = service.serviceId,
                url = boardInfo.url,
                name = boardInfo.name
            )

            // --- Persistence ---
            val insertedId = boardDao.insertBoard(boardEntity)
            // 既存登録済みの場合は URL で再取得する。
            if (insertedId != -1L) insertedId else boardDao.findBoardIdByUrl(boardInfo.url)
        }
    }

    /**
     * 削除対象 ID をチャンク分割して削除する。
     *
     * 1 回の SQL 変数数を抑え、`too many SQL variables` を回避する。
     */
    private suspend fun deleteThreadSummariesInChunks(boardId: Long, threadIds: List<String>) {
        // 空集合の場合は SQL を発行せずに終了する。
        if (threadIds.isEmpty()) {
            return
        }

        // SQLite の変数上限を超えないように固定サイズで分割する。
        chunkThreadIdsForDeletion(threadIds, SQL_VARIABLE_CHUNK_SIZE).forEach { chunk ->
            threadSummaryDao.deleteByThreadIds(boardId, chunk)
        }
    }
}

/**
 * 削除対象のスレッド ID を SQL 安全サイズに分割する。
 *
 * 返却順は入力順を維持し、全要素を重複なく 1 回ずつ含む。
 */
internal fun chunkThreadIdsForDeletion(threadIds: List<String>, chunkSize: Int): List<List<String>> {
    // 分割単位が不正な場合は呼び出し側の設定ミスとして即時失敗させる。
    require(chunkSize > 0) { "chunkSize must be greater than 0" }
    return threadIds.chunked(chunkSize)
}

/**
 * 既存スレッド一覧から、最新 subject.txt に存在しない ID を抽出する。
 */
internal fun calculateRemovedThreadIds(
    existingIds: List<String>,
    latestSubjectIds: List<String>,
): List<String> {
    val latestIdSet = latestSubjectIds.toHashSet()
    return existingIds.filterNot { it in latestIdSet }
}

/**
 * スレッドの派生情報を算出する。
 *
 * fetch metadata 未設定時は時刻基準が無いため、勢いは 0.0 で固定する。
 */
internal fun resolveThreadDerivedInfo(
    threadKey: String,
    resCount: Int,
    nowSeconds: Long,
): ThreadDerivedInfo {
    // --- Guard ---
    // fetch metadata が無い間は勢いを出さず、作成日時のみ算出する。
    if (nowSeconds <= 0L) {
        return ThreadDerivedInfo(
            date = ThreadInfoDerivedCalculator.calculateDate(threadKey),
            momentum = 0.0,
        )
    }

    // --- Derived info ---
    return ThreadInfoDerivedCalculator.calculate(
        threadKey = threadKey,
        resCount = resCount,
        nowSeconds = nowSeconds,
    )
}
