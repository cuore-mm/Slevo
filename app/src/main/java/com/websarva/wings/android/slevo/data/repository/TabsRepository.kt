package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenBoardTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.util.ThreadNewResCalculator
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.mergeBoardTabMetadata
import com.websarva.wings.android.slevo.ui.tabs.model.mergeThreadTabMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 対象行の永続 command が返す明示的な結果。
 * Controller はこの値を使って success、no-op、failure を presentation から独立して判別する。
 */
sealed interface TabMutationResult {
    /** 対象行の変更が transaction 内で成功した。 */
    data object Success : TabMutationResult

    /** 対象なし、または既に同値で変更がなかった。 */
    data object NoOp : TabMutationResult

    /** DB command が失敗した。 */
    data class Failure(val cause: Throwable) : TabMutationResult
}

/**
 * 開いている板タブとスレッドタブを永続化し、UI 表示モデルとして監視する Repository。
 * スレッドタブは Phase 2 以降、タブ固有状態に `thread_states` と履歴既読状態を合成して返す。
 */
@Singleton
class TabsRepository @Inject constructor(
    private val boardDao: OpenBoardTabDao,
    private val threadDao: OpenThreadTabDao,
    private val tabsLocalDataSource: TabsLocalDataSource,
    private val threadStateRepository: ThreadStateRepository,
    private val gate: DatabaseWriteGate = DatabaseWriteGate(),
    private val db: AppDatabase,
) {
    private companion object {
        /** SQLiteのbind変数上限999未満に収める対象ID chunkサイズ。 */
        const val BULK_DELETE_CHUNK_SIZE = 900
    }

    fun observeOpenBoardTabs(): Flow<List<BoardTabInfo>> =
        boardDao.observeOpenBoardTabs().map { list ->
            list.sortedBy { it.sortOrder }.map { entity ->
                BoardTabInfo(
                    boardId = entity.boardId,
                    boardName = entity.boardName,
                    boardUrl = entity.boardUrl,
                    serviceName = entity.serviceName,
                    firstVisibleItemIndex = entity.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = entity.firstVisibleItemScrollOffset,
                    isPinned = entity.isPinned
                )
            }
        }

    suspend fun saveOpenBoardTabs(tabs: List<BoardTabInfo>) {
        gate.withWritePermit {
            db.withTransaction {
                val existing = boardDao.getAll().associateBy { it.boardUrl }
                val upserts = mutableListOf<OpenBoardTabEntity>()
                val ids = mutableListOf<String>()
                tabs.forEachIndexed { index, info ->
                    val entity = OpenBoardTabEntity(
                        boardUrl = info.boardUrl,
                        boardId = info.boardId,
                        boardName = info.boardName,
                        serviceName = info.serviceName,
                        sortOrder = index,
                        isPinned = info.isPinned,
                        firstVisibleItemIndex = info.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = info.firstVisibleItemScrollOffset
                    )
                    ids.add(info.boardUrl)
                    if (existing[info.boardUrl] != entity) {
                        upserts.add(entity)
                    }
                }
                if (upserts.isNotEmpty()) {
                    boardDao.upsertAll(upserts)
                }
                if (ids.isEmpty()) {
                    boardDao.deleteAll()
                } else {
                    boardDao.deleteNotIn(ids)
                }
            }
        }
    }

    /** Board の存在を一行だけ保証し、既存の pin／scroll／解決済み metadata を保持する。 */
    suspend fun ensureOpenBoardTab(tabInfo: BoardTabInfo): TabMutationResult = runCatching {
        // --- write permit ---
        gate.withWritePermit {
            db.withTransaction {
                // --- row merge ---
                val existing = boardDao.getByBoardUrl(tabInfo.boardUrl)
                val next = if (existing == null) {
                    OpenBoardTabEntity(
                        boardUrl = tabInfo.boardUrl,
                        boardId = tabInfo.boardId,
                        boardName = tabInfo.boardName,
                        serviceName = tabInfo.serviceName,
                        sortOrder = (boardDao.getMaxSortOrder() ?: -1) + 1,
                        isPinned = tabInfo.isPinned,
                        firstVisibleItemIndex = tabInfo.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = tabInfo.firstVisibleItemScrollOffset,
                    )
                } else {
                    val merged = mergeBoardTabMetadata(
                        current = BoardTabInfo(
                            boardId = existing.boardId,
                            boardName = existing.boardName,
                            boardUrl = existing.boardUrl,
                            serviceName = existing.serviceName,
                            firstVisibleItemIndex = existing.firstVisibleItemIndex,
                            firstVisibleItemScrollOffset = existing.firstVisibleItemScrollOffset,
                            isPinned = existing.isPinned,
                        ),
                        incoming = tabInfo,
                    )
                    existing.copy(
                        boardId = merged.boardId,
                        boardName = merged.boardName,
                        serviceName = merged.serviceName,
                    )
                }

                // --- targeted write ---
                if (existing == next) TabMutationResult.NoOp else {
                    boardDao.upsert(next)
                    TabMutationResult.Success
                }
            }
        }
    }.getOrElse(TabMutationResult::Failure)

    /** Board の対象行だけを削除する。 */
    suspend fun deleteOpenBoardTab(boardUrl: String): TabMutationResult = runCatching {
        gate.withWritePermit {
            if (boardDao.deleteByBoardUrl(boardUrl) == 0) TabMutationResult.NoOp else TabMutationResult.Success
        }
    }.getOrElse(TabMutationResult::Failure)

    /** 指定板タブ集合を一つのtransactionで対象行だけ削除する。 */
    suspend fun deleteOpenBoardTabs(boardUrls: List<String>): TabMutationResult = runCatching {
        val distinctUrls = boardUrls.distinct()
        if (distinctUrls.isEmpty()) {
            return@runCatching TabMutationResult.NoOp
        }
        gate.withWritePermit {
            db.withTransaction {
                var deletedCount = 0
                distinctUrls.chunked(BULK_DELETE_CHUNK_SIZE).forEach { chunk ->
                    deletedCount += boardDao.deleteByBoardUrls(chunk)
                }
                if (deletedCount == 0) TabMutationResult.NoOp else TabMutationResult.Success
            }
        }
    }.getOrElse(TabMutationResult::Failure)

    /** Board の対象行の pin 列だけを更新する。 */
    suspend fun setBoardTabPinned(boardUrl: String, isPinned: Boolean): TabMutationResult = runCatching {
        gate.withWritePermit {
            val current = boardDao.getByBoardUrl(boardUrl) ?: return@withWritePermit TabMutationResult.NoOp
            if (current.isPinned == isPinned) TabMutationResult.NoOp
            else if (boardDao.updatePinned(boardUrl, isPinned) == 0) TabMutationResult.NoOp
            else TabMutationResult.Success
        }
    }.getOrElse(TabMutationResult::Failure)

    /** Board の対象行のスクロール列だけを更新する。 */
    suspend fun updateBoardTabScrollPosition(
        boardUrl: String,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ): TabMutationResult = runCatching {
        gate.withWritePermit {
            if (boardDao.updateScrollPosition(
                    boardUrl,
                    firstVisibleItemIndex,
                    firstVisibleItemScrollOffset,
                ) == 0
            ) TabMutationResult.NoOp else TabMutationResult.Success
        }
    }.getOrElse(TabMutationResult::Failure)

    /** Board の解決済み metadata だけを更新し、対象行の順序・pin・scroll を保持する。 */
    suspend fun updateBoardTabInfo(tabInfo: BoardTabInfo): TabMutationResult = runCatching {
        gate.withWritePermit {
            val current = boardDao.getByBoardUrl(tabInfo.boardUrl) ?: return@withWritePermit TabMutationResult.NoOp
            val mergedInfo = mergeBoardTabMetadata(
                current = BoardTabInfo(
                    boardId = current.boardId,
                    boardName = current.boardName,
                    boardUrl = current.boardUrl,
                    serviceName = current.serviceName,
                    firstVisibleItemIndex = current.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = current.firstVisibleItemScrollOffset,
                    isPinned = current.isPinned,
                ),
                incoming = tabInfo,
            )
            val merged = current.copy(
                boardId = mergedInfo.boardId,
                boardName = mergedInfo.boardName,
                serviceName = mergedInfo.serviceName,
            )
            if (merged == current) TabMutationResult.NoOp else {
                boardDao.upsert(merged)
                TabMutationResult.Success
            }
        }
    }.getOrElse(TabMutationResult::Failure)

    /**
     * 板タブの sortOrder だけを一つの transaction で再採番する。
     * DBにのみ存在するキーは末尾へ残し、要求にないキーを削除しない。
     */
    suspend fun reorderOpenBoardTabs(requestedKeys: List<String>): TabMutationResult = runCatching {
        gate.withWritePermit {
            db.withTransaction {
                val current = boardDao.getAll().sortedBy { it.sortOrder }
                val currentKeys = current.map { it.boardUrl }
                val finalKeys = mergeRequestedOrder(currentKeys, requestedKeys)
                if (finalKeys == currentKeys) {
                    TabMutationResult.NoOp
                } else {
                    finalKeys.forEachIndexed { index, key -> boardDao.updateSortOrder(key, index) }
                    TabMutationResult.Success
                }
            }
        }
    }.getOrElse(TabMutationResult::Failure)

    /**
     * 開いているスレッドタブを、客観状態と履歴既読状態を合成した表示モデルとして監視する。
     * 履歴がないタブは未訪問扱いにし、新着数とスクロール位置を 0 に丸める。
     */
    fun observeOpenThreadTabs(): Flow<List<ThreadTabInfo>> =
        threadDao.observeOpenThreadTabsWithState().map { list ->
            list.sortedBy { it.sortOrder }.map(::toThreadTabInfo)
        }

    /** 対象タブの表示用スナップショットを取得する。通常の更新処理で書き込み前の読み取りに使用する。 */
    suspend fun getOpenThreadTab(threadId: ThreadId): ThreadTabInfo? =
        threadDao.getOpenThreadTabWithState(threadId)?.let(::toThreadTabInfo)

    /**
     * スレッドタブの存在を対象行だけで保証し、必要な ThreadState を同じトランザクションで保存する。
     * 既存行の並び順、固定状態、スクロール位置は読み出した値をそのまま維持する。
     */
    suspend fun ensureOpenThreadTab(tabInfo: ThreadTabInfo): Boolean = gate.withWritePermit {
        db.withTransaction {
            val existing = threadDao.getByThreadId(tabInfo.id)
            val canonicalState = threadStateRepository.getThreadState(tabInfo.id)
            val stateToSave = canonicalState?.let { state ->
                mergeThreadTabMetadata(
                    current = ThreadTabInfo(
                        id = tabInfo.id,
                        title = state.title,
                        boardName = state.boardName,
                        boardUrl = state.boardUrl,
                        boardId = state.boardId,
                        resCount = state.latestResCount,
                        isPinned = existing?.isPinned ?: false,
                        firstVisibleItemIndex = existing?.firstVisibleItemIndex ?: 0,
                        firstVisibleItemScrollOffset = existing?.firstVisibleItemScrollOffset ?: 0,
                    ),
                    incoming = tabInfo,
                )
            } ?: tabInfo
            // このトランザクションで読み出した行とマージしてから、その ThreadState だけを更新する。
            threadStateRepository.saveThreadStateUngated(stateToSave.toThreadStateUpdate())
            if (existing == null) {
                val nextSortOrder = (threadDao.getMaxSortOrder() ?: -1) + 1
                threadDao.upsert(
                    OpenThreadTabEntity(
                        threadId = tabInfo.id,
                        sortOrder = nextSortOrder,
                        isPinned = tabInfo.isPinned,
                        firstVisibleItemIndex = tabInfo.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = tabInfo.firstVisibleItemScrollOffset,
                    )
                )
            }
            true
        }
    }

    /**
     * 指定タブの行だけを削除する。対象がなければ false を返し、他の行は変更しない。
     */
    suspend fun deleteOpenThreadTab(threadId: ThreadId): Boolean = gate.withWritePermit {
        db.withTransaction {
            val deleted = threadDao.deleteByThreadId(threadId) > 0
            if (deleted) {
                threadStateRepository.collectGarbageUngated()
            }
            deleted
        }
    }

    /** 指定スレッドタブ集合をchunk化して一つのtransactionで削除し、GCを一度だけ実行する。 */
    suspend fun deleteOpenThreadTabs(threadIds: List<ThreadId>): Boolean = gate.withWritePermit {
        val distinctIds = threadIds.distinctBy { it.value }
        if (distinctIds.isEmpty()) {
            return@withWritePermit false
        }
        db.withTransaction {
            var deletedCount = 0
            distinctIds.chunked(BULK_DELETE_CHUNK_SIZE).forEach { chunk ->
                deletedCount += threadDao.deleteByThreadIds(chunk.map { it.value })
            }
            if (deletedCount > 0) {
                // 既存の遅延GC契約に従い、bulk transactionの末尾で一度だけ収集する。
                threadStateRepository.collectGarbageUngated()
            }
            deletedCount > 0
        }
    }

    /** 指定タブの pin 列だけを更新し、値が変わった場合だけ true を返す。 */
    suspend fun setThreadTabPinned(threadId: ThreadId, isPinned: Boolean): Boolean = gate.withWritePermit {
        db.withTransaction {
            val current = threadDao.getByThreadId(threadId) ?: return@withTransaction false
            if (current.isPinned == isPinned) return@withTransaction false
            threadDao.updatePinned(threadId, isPinned) > 0
        }
    }

    /** 指定スレッドの共通 ThreadState だけを更新する。open-thread-tab の一覧は置換しない。 */
    suspend fun updateThreadState(update: ThreadStateRepository.ThreadStateUpdate) {
        gate.withWritePermit {
            db.withTransaction { threadStateRepository.saveThreadStateUngated(update) }
        }
    }

    /**
     * スレッドタブの sortOrder だけを一つの transaction で再採番する。
     * ThreadState、固定状態、スクロール位置、タブ集合は変更しない。
     */
    suspend fun reorderOpenThreadTabs(requestedKeys: List<String>): TabMutationResult = runCatching {
        gate.withWritePermit {
            db.withTransaction {
                val current = threadDao.getAll().sortedBy { it.sortOrder }
                val currentKeys = current.map { it.threadId.value }
                val finalKeys = mergeRequestedOrder(currentKeys, requestedKeys)
                if (finalKeys == currentKeys) {
                    TabMutationResult.NoOp
                } else {
                    finalKeys.forEachIndexed { index, key ->
                        threadDao.updateSortOrder(ThreadId(key), index)
                    }
                    TabMutationResult.Success
                }
            }
        }
    }.getOrElse(TabMutationResult::Failure)

    /**
     * 初回読込後の専用一括処理からだけ呼び出す全件置換 API。
     * 通常の追加・削除・固定・情報・スクロール処理では対象行単位の更新 API を使用する。
     */
    suspend fun replaceOpenThreadTabsForBulkOperation(tabs: List<ThreadTabInfo>) {
        gate.withWritePermit {
            db.withTransaction {
                val existing = threadDao.getAll().associateBy { it.threadId.value }
                threadStateRepository.saveThreadStatesUngated(
                    tabs.map { info ->
                        ThreadStateRepository.ThreadStateUpdate(
                            threadId = info.id,
                            boardId = info.boardId,
                            boardUrl = info.boardUrl,
                            boardName = info.boardName,
                            title = info.title,
                            latestResCount = info.resCount,
                        )
                    }
                )
                val upserts = mutableListOf<OpenThreadTabEntity>()
                val ids = mutableListOf<String>()
                tabs.forEachIndexed { index, info ->
                    val entity = OpenThreadTabEntity(
                        threadId = info.id,
                        sortOrder = index,
                        isPinned = info.isPinned,
                        firstVisibleItemIndex = info.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = info.firstVisibleItemScrollOffset
                    )
                    val id = info.id.value
                    ids.add(id)
                    if (existing[id] != entity) {
                        upserts.add(entity)
                    }
                }
                if (upserts.isNotEmpty()) {
                    threadDao.upsertAll(upserts)
                }
                if (ids.isEmpty()) {
                    threadDao.deleteAll()
                } else {
                    threadDao.deleteNotIn(ids)
                }
                threadStateRepository.collectGarbageUngated()
            }
        }
    }

    /**
     * 指定 threadId のタブ固有スクロール位置だけを更新する。
     * タブ一覧構造（sortOrder, isPinned など）は変更しない。
     * 対象タブが存在しない場合は no-op として扱う。
     */
    suspend fun updateThreadTabScrollPosition(
        threadId: ThreadId,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        gate.withWritePermit {
            threadDao.updateThreadScrollPosition(
                threadId = threadId,
                firstVisibleItemIndex = firstVisibleItemIndex,
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            )
        }
    }

    fun observeLastSelectedTabsPage(): Flow<Int> =
        tabsLocalDataSource.observeLastSelectedTabsPage()

    suspend fun setLastSelectedTabsPage(page: Int) =
        tabsLocalDataSource.setLastSelectedTabsPage(page)

    /** ThreadTabInfo から共通状態更新の入力へ変換する。 */
    private fun ThreadTabInfo.toThreadStateUpdate(): ThreadStateRepository.ThreadStateUpdate =
        ThreadStateRepository.ThreadStateUpdate(
            threadId = id,
            boardId = boardId,
            boardUrl = boardUrl,
            boardName = boardName,
            title = title,
            latestResCount = resCount,
        )

    /** DAO の合成行を UI 表示モデルへ変換する。 */
    private fun toThreadTabInfo(entity: OpenThreadTabDao.OpenThreadTabWithState): ThreadTabInfo {
        val readState = if (entity.hasHistory) {
            ThreadReadState(
                prevResCount = entity.historyPrevResCount ?: 0,
                lastReadResNo = entity.historyLastReadResNo ?: 0,
                firstNewResNo = entity.historyFirstNewResNo,
            )
        } else {
            null
        }
        return ThreadTabInfo(
            id = entity.threadId,
            title = entity.title,
            boardName = entity.boardName,
            boardUrl = entity.boardUrl,
            boardId = entity.boardId,
            resCount = entity.latestResCount,
            newResCount = ThreadNewResCalculator.calculate(entity.latestResCount, readState),
            hasHistory = entity.hasHistory,
            prevResCount = readState?.prevResCount ?: 0,
            lastReadResNo = readState?.lastReadResNo ?: 0,
            firstNewResNo = readState?.firstNewResNo,
            firstVisibleItemIndex = if (entity.hasHistory) entity.firstVisibleItemIndex else 0,
            firstVisibleItemScrollOffset = if (entity.hasHistory) {
                entity.firstVisibleItemScrollOffset
            } else {
                0
            },
            isPinned = entity.isPinned,
        )
    }

    /** 要求順とDB上の現在順を統合し、存在するキーだけの一意な順序を作る。 */
    private fun mergeRequestedOrder(
        currentKeys: List<String>,
        requestedKeys: List<String>,
    ): List<String> {
        val currentSet = currentKeys.toSet()
        return buildList {
            requestedKeys.forEach { key ->
                if (key in currentSet && key !in this) add(key)
            }
            currentKeys.forEach { key ->
                if (key !in this) add(key)
            }
        }
    }
}
