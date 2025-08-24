package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.ThreadBookmarkWithGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkThreadDao {
    @Transaction // グループ情報も一緒に取得するためトランザクション化
    @Query("SELECT * FROM bookmark_threads") // ソート順は指定しない
    fun getAllBookmarksWithGroup(): Flow<List<ThreadBookmarkWithGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkThreadEntity)

    // threadKeyとboardUrlを元に削除
    @Query("DELETE FROM bookmark_threads WHERE threadKey = :threadKey AND boardUrl = :boardUrl")
    suspend fun deleteBookmark(threadKey: String, boardUrl: String)

    @Transaction // グループ情報も一緒に取得するためトランザクション化
    @Query("SELECT * FROM bookmark_threads WHERE threadKey = :threadKey AND boardUrl = :boardUrl LIMIT 1")
    fun getBookmarkWithGroup(threadKey: String, boardUrl: String): Flow<ThreadBookmarkWithGroup?>

}
