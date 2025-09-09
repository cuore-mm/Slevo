package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenBoardTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabsRepository @Inject constructor(
    private val boardDao: OpenBoardTabDao,
    private val threadDao: OpenThreadTabDao,
    private val tabsLocalDataSource: TabsLocalDataSource,
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

    fun observeOpenThreadTabs(): Flow<List<ThreadTabInfo>> =
        threadDao.observeOpenThreadTabs().map { list ->
            list.sortedBy { it.tab.sortOrder }.map { entity ->
                val history = entity.history
                val tab = entity.tab
                ThreadTabInfo(
                    key = history.threadKey,
                    title = history.title,
                    boardName = history.boardName,
                    boardUrl = history.boardUrl,
                    boardId = history.boardId,
                    resCount = history.resCount,
                    prevResCount = history.prevResCount,
                    lastReadResNo = history.lastReadResNo,
                    firstNewResNo = history.firstNewResNo,
                    firstVisibleItemIndex = tab.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = tab.firstVisibleItemScrollOffset
                )
            }
        }

    suspend fun saveOpenThreadTabs(tabs: List<ThreadTabInfo>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = threadDao.getAll().associateBy { it.threadId.value }
            val upserts = mutableListOf<OpenThreadTabEntity>()
            val ids = mutableListOf<String>()
            tabs.forEachIndexed { index, info ->
                val pair = parseBoardUrl(info.boardUrl) ?: return@forEachIndexed
                val (host, boardKey) = pair
                val threadId = ThreadId.of(host, boardKey, info.key)
                val entity = OpenThreadTabEntity(
                    threadId = threadId,
                    sortOrder = index,
                    firstVisibleItemIndex = info.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = info.firstVisibleItemScrollOffset
                )
                val id = threadId.value
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
        }
    }

    fun observeLastSelectedPage(): Flow<Int> =
        tabsLocalDataSource.observeLastSelectedPage()

    suspend fun setLastSelectedPage(page: Int) =
        tabsLocalDataSource.setLastSelectedPage(page)
}
