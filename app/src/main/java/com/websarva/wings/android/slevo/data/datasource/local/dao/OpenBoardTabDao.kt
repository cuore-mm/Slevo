package com.websarva.wings.android.slevo.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenBoardTabDao {
    @Query("SELECT * FROM open_board_tabs ORDER BY sortOrder ASC")
    fun observeOpenBoardTabs(): Flow<List<OpenBoardTabEntity>>

    @Query("SELECT * FROM open_board_tabs")
    suspend fun getAll(): List<OpenBoardTabEntity>

    @Upsert
    suspend fun upsertAll(tabs: List<OpenBoardTabEntity>)

    @Query("DELETE FROM open_board_tabs WHERE boardUrl NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM open_board_tabs")
    suspend fun deleteAll()
}
