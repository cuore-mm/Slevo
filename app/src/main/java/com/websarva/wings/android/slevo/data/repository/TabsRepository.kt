package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenBoardTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.ui.tabs.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabsRepository @Inject constructor(
    private val boardDao: OpenBoardTabDao,
    private val threadDao: OpenThreadTabDao,
    private val tabsLocalDataSource: TabsLocalDataSource
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
        boardDao.deleteAll()
        boardDao.insertAll(
            tabs.mapIndexed { index, info ->
                OpenBoardTabEntity(
                    boardUrl = info.boardUrl,
                    boardId = info.boardId,
                    boardName = info.boardName,
                    serviceName = info.serviceName,
                    sortOrder = index,
                    firstVisibleItemIndex = info.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = info.firstVisibleItemScrollOffset
                )
            }
        )
    }

    fun observeOpenThreadTabs(): Flow<List<ThreadTabInfo>> =
        threadDao.observeOpenThreadTabs().map { list ->
            list.sortedBy { it.sortOrder }.map { entity ->
                ThreadTabInfo(
                    key = entity.threadKey,
                    title = entity.title,
                    boardName = entity.boardName,
                    boardUrl = entity.boardUrl,
                    boardId = entity.boardId,
                    resCount = entity.resCount,
                    lastReadResNo = entity.lastReadResNo,
                    firstNewResNo = entity.firstNewResNo,
                    firstVisibleItemIndex = entity.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = entity.firstVisibleItemScrollOffset
                )
            }
        }

    suspend fun saveOpenThreadTabs(tabs: List<ThreadTabInfo>) = withContext(Dispatchers.IO) {
        threadDao.deleteAll()
        threadDao.insertAll(
            tabs.mapIndexed { index, info ->
                OpenThreadTabEntity(
                    threadKey = info.key,
                    boardUrl = info.boardUrl,
                    boardId = info.boardId,
                    boardName = info.boardName,
                    title = info.title,
                    resCount = info.resCount,
                    lastReadResNo = info.lastReadResNo,
                    firstNewResNo = info.firstNewResNo,
                    sortOrder = index,
                    firstVisibleItemIndex = info.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = info.firstVisibleItemScrollOffset
                )
            }
        )
    }

    fun observeLastSelectedPage(): Flow<Int> =
        tabsLocalDataSource.observeLastSelectedPage()

    suspend fun setLastSelectedPage(page: Int) =
        tabsLocalDataSource.setLastSelectedPage(page)
}
