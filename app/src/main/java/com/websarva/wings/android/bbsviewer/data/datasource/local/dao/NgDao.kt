package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.NgEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ng: NgEntity): Long

    @Query("SELECT * FROM ng_entries")
    fun getAll(): Flow<List<NgEntity>>

    @Query("DELETE FROM ng_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
