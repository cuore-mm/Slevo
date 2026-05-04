package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenBoardTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.util.ThreadNewResCalculator
import com.websarva.wings.android.slevo.ui.tabs.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
                    firstVisibleItemScrollOffset = entity.firstVisibleItemScrollOffset
                )
            }
        }

    suspend fun saveOpenBoardTabs(tabs: List<BoardTabInfo>) = withContext(Dispatchers.IO) {
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

    /**
     * 開いているスレッドタブを、客観状態と履歴既読状態を合成した表示モデルとして監視する。
     * 履歴がないタブは未訪問扱いにし、新着数とスクロール位置を 0 に丸める。
     */
    fun observeOpenThreadTabs(): Flow<List<ThreadTabInfo>> =
        threadDao.observeOpenThreadTabsWithState().map { list ->
            list.sortedBy { it.sortOrder }.map { entity ->
                val readState = if (entity.hasHistory) {
                    ThreadReadState(
                        prevResCount = entity.historyPrevResCount ?: 0,
                        lastReadResNo = entity.historyLastReadResNo ?: 0,
                        firstNewResNo = entity.historyFirstNewResNo,
                    )
                } else {
                    null
                }
                ThreadTabInfo(
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
                    }
                )
            }
        }

    /**
     * 開いているスレッドタブの並び順とスクロール位置を保存する。
     * タイトル・レス数などの客観状態は `thread_states` へ保存し、タブテーブルには書き込まない。
     */
    suspend fun saveOpenThreadTabs(tabs: List<ThreadTabInfo>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = threadDao.getAll().associateBy { it.threadId.value }
            threadStateRepository.saveThreadStates(
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
            threadStateRepository.collectGarbage()
        }
    }

    fun observeLastSelectedPage(): Flow<Int> =
        tabsLocalDataSource.observeLastSelectedPage()

    suspend fun setLastSelectedPage(page: Int) =
        tabsLocalDataSource.setLastSelectedPage(page)
}
