package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.NgIdEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NgIdDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ngId: NgIdEntity): Long

    @Query("SELECT * FROM ng_ids")
    fun getAll(): Flow<List<NgIdEntity>>
}
