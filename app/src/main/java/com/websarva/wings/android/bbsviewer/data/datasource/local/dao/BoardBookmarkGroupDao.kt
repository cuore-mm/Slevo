package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardBookmarkGroupDao {
    /** 既存の最大 sortOrder を取得。未登録時は -1 を返す */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM `groups`")
    suspend fun getMaxSortOrder(): Int

    /** 新規グループを挿入し、自動採番された groupId を返す */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: BoardBookmarkGroupEntity): Long

    /** 並び順に沿ってグループ一覧を取得 */
    @Query("SELECT * FROM 'groups' ORDER BY sortOrder ASC")
    fun getAllGroupsSorted(): Flow<List<BoardBookmarkGroupEntity>>

    /** 並び順を更新 */
    @Update
    suspend fun updateGroups(groups: List<BoardBookmarkGroupEntity>)

    @Query("UPDATE `groups` SET name = :name, colorHex = :colorHex WHERE groupId = :groupId")
    suspend fun updateGroupInfo(groupId: Long, name: String, colorHex: String)

    @Query("DELETE FROM `groups` WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    /** グループ順（sortOrder）に並べた全グループ＋その中のお気に入り板を一気に取得 */
    @Transaction
    @Query("SELECT * FROM `groups` ORDER BY sortOrder ASC")
    fun getGroupsWithBoards(): Flow<List<GroupWithBoards>>
}
