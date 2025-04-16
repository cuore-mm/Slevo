package com.websarva.wings.android.bbsviewer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.local.entity.BookmarkThreadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkThreadDao {
    // 全てのブックマークされたスレッドを、追加日時の降順で取得します
    @Query("SELECT * FROM bookmark_threads")
    fun getAllBookmarks(): Flow<List<BookmarkThreadEntity>>

    // ブックマークを新規追加または既存のものを更新します
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkThreadEntity)

    // ブックマークをエンティティごと削除します
    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkThreadEntity)

    // 指定したスレッドIDのお気に入りスレッドを取得します
    @Query("SELECT * FROM bookmark_threads WHERE id = :id LIMIT 1")
    suspend fun getBookmarkById(id: String): BookmarkThreadEntity?
}

