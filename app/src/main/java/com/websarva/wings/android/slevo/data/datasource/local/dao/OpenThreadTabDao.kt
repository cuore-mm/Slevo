package com.websarva.wings.android.slevo.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenThreadTabDao {
    @Query("SELECT * FROM open_thread_tabs ORDER BY sortOrder ASC")
    fun observeOpenThreadTabs(): Flow<List<OpenThreadTabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tabs: List<OpenThreadTabEntity>)

    @Query("DELETE FROM open_thread_tabs")
    suspend fun deleteAll()
}
