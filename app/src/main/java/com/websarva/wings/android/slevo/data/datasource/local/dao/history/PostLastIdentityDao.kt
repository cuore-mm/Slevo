package com.websarva.wings.android.slevo.data.datasource.local.dao.history

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostLastIdentityEntity

@Dao
interface PostLastIdentityDao {
    @Upsert
    suspend fun upsert(entity: PostLastIdentityEntity)

    @Query("SELECT * FROM post_last_identities WHERE boardId = :boardId")
    suspend fun findByBoardId(boardId: Long): PostLastIdentityEntity?

    @Query("DELETE FROM post_last_identities WHERE boardId = :boardId")
    suspend fun deleteByBoardId(boardId: Long)
}
