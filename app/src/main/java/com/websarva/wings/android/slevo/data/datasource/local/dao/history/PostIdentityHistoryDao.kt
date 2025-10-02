package com.websarva.wings.android.slevo.data.datasource.local.dao.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostIdentityHistoryDao {
    @Upsert
    suspend fun upsert(history: PostIdentityHistoryEntity)

    @Query(
        "SELECT id FROM post_identity_histories WHERE boardId = :boardId AND type = :type ORDER BY lastUsedAt DESC"
    )
    suspend fun findIdsOrdered(boardId: Long, type: String): List<Long>

    @Query(
        "SELECT value FROM post_identity_histories WHERE boardId = :boardId AND type = :type ORDER BY lastUsedAt DESC"
    )
    fun observeValues(boardId: Long, type: String): Flow<List<String>>

    @Query("DELETE FROM post_identity_histories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
