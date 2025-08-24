package com.websarva.wings.android.slevo.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenBoardTabDao {
    @Query("SELECT * FROM open_board_tabs ORDER BY sortOrder ASC")
    fun observeOpenBoardTabs(): Flow<List<OpenBoardTabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<OpenBoardTabEntity>)

    @Query("DELETE FROM open_board_tabs")
    suspend fun deleteAll()
}
