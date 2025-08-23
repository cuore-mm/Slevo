package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.PostHistoryEntity

@Dao
interface PostHistoryDao {
    @Insert
    suspend fun insert(history: PostHistoryEntity)
}
