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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

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

    /**
     * 開いているスレッドタブを、客観状態と履歴既読状態を合成した表示モデルとして監視する。
     * 履歴がないタブは未訪問扱いにし、新着数とスクロール位置を 0 に丸める。
     */
    fun observeOpenThreadTabs(): Flow<List<ThreadTabInfo>> =
        threadDao.observeOpenThreadTabsWithState().map { list ->
            list.sortedBy { it.sortOrder }.map(::toThreadTabInfo)
        }

    /** 対象タブの表示用 snapshot を取得する。通常 mutation の read-before-write に使用する。 */
    suspend fun getOpenThreadTab(threadId: ThreadId): ThreadTabInfo? =
        threadDao.getOpenThreadTabWithState(threadId)?.let(::toThreadTabInfo)

    /**
     * スレッドタブを対象行だけで ensure し、必要な ThreadState を同じ transaction で保存する。
     * 既存行の sort、pin、scroll は読み出した値をそのまま維持する。
     */
    suspend fun ensureOpenThreadTab(tabInfo: ThreadTabInfo): Boolean = gate.withWritePermit {
        db.withTransaction {
            val existing = threadDao.getByThreadId(tabInfo.id)
            threadStateRepository.saveThreadStateUngated(tabInfo.toThreadStateUpdate())
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
     * 初回読込後の専用 bulk orchestration からだけ呼び出す全件置換 API。
     * 通常の add/delete/pin/info/scroll 経路では targeted mutation API を使用する。
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
}
