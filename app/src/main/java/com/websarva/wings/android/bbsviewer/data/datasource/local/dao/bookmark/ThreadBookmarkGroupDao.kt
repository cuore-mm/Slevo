package com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bookmark

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.GroupWithThreadBookmarks
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.ThreadBookmarkGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadBookmarkGroupDao {
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM thread_bookmark_groups")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ThreadBookmarkGroupEntity): Long

    @Query("SELECT * FROM thread_bookmark_groups ORDER BY sortOrder ASC")
    fun getAllGroupsSorted(): Flow<List<ThreadBookmarkGroupEntity>>

    @Update
    suspend fun updateGroups(groups: List<ThreadBookmarkGroupEntity>)

    @Query("UPDATE thread_bookmark_groups SET name = :name, colorName = :colorName WHERE groupId = :groupId")
    suspend fun updateGroupInfo(groupId: Long, name: String, colorName: String)

    @Query("DELETE FROM thread_bookmark_groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    // 必要に応じて特定のIDでグループを取得するメソッドなども追加
    @Query("SELECT * FROM thread_bookmark_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: Long): ThreadBookmarkGroupEntity?

    @Query("SELECT * FROM thread_bookmark_groups WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ThreadBookmarkGroupEntity?

    // グループとそのグループに属するスレッドブックマークをまとめて取得し、グループの表示順でソート
    @Transaction // 複数のテーブルにまたがるクエリのためトランザクション化
    @Query("SELECT * FROM thread_bookmark_groups ORDER BY sortOrder ASC")
    fun getSortedGroupsWithThreadBookmarks(): Flow<List<GroupWithThreadBookmarks>>
}
