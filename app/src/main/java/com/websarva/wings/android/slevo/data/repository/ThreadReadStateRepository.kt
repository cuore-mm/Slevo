package com.websarva.wings.android.slevo.data.repository

import androidx.room.withTransaction
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.ThreadId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadReadStateRepository @Inject constructor(
    private val db: AppDatabase,
    private val threadHistoryDao: ThreadHistoryDao,
    private val openThreadTabDao: OpenThreadTabDao,
) {
    suspend fun saveReadState(threadId: ThreadId, readState: ThreadReadState) {
        db.withTransaction {
            threadHistoryDao.updateReadState(
                threadId,
                readState.prevResCount,
                readState.lastReadResNo,
                readState.firstNewResNo,
            )
            openThreadTabDao.updateReadState(
                threadId,
                readState.prevResCount,
                readState.lastReadResNo,
                readState.firstNewResNo,
            )
        }
    }
}
