package com.websarva.wings.android.slevo.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenThreadTabDao {
    @Query("SELECT * FROM open_thread_tabs ORDER BY sortOrder ASC")
    fun observeOpenThreadTabs(): Flow<List<OpenThreadTabEntity>>

    @Query("SELECT * FROM open_thread_tabs")
    suspend fun getAll(): List<OpenThreadTabEntity>

    @Upsert
    suspend fun upsertAll(tabs: List<OpenThreadTabEntity>)

    @Query("DELETE FROM open_thread_tabs WHERE threadId NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM open_thread_tabs")
    suspend fun deleteAll()

    @Query(
        "UPDATE open_thread_tabs SET prevResCount = :prevResCount, lastReadResNo = :lastReadResNo, " +
            "firstNewResNo = :firstNewResNo WHERE threadId = :threadId"
    )
    suspend fun updateReadState(
        threadId: ThreadId,
        prevResCount: Int,
        lastReadResNo: Int,
        firstNewResNo: Int?,
    )
}
