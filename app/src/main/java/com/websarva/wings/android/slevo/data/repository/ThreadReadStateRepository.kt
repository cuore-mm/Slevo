package com.websarva.wings.android.slevo.data.repository

import androidx.room.withTransaction
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadReadStateRepository @Inject constructor(
    private val threadHistoryDao: ThreadHistoryDao,
    private val openThreadTabDao: OpenThreadTabDao,
    private val db: AppDatabase,
) {
    suspend fun saveReadState(threadId: ThreadId, readState: ThreadReadState) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                openThreadTabDao.updateReadState(
                    threadId,
                    readState.prevResCount,
                    readState.lastReadResNo,
                    readState.firstNewResNo
                )
                threadHistoryDao.updateReadState(
                    threadId,
                    readState.prevResCount,
                    readState.lastReadResNo,
                    readState.firstNewResNo
                )
            }
        }
}
