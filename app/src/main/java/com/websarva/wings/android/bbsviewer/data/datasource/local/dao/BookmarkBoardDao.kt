package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.*
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardWithBookmarkAndGroup
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkWithGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkBoardDao {

    /**
     * サービスID(serviceId)・カテゴリID(categoryId) に紐づく Board + Bookmark + Group をまとめて取得
     */
    @Transaction
    @Query("""
        SELECT
          b.*,
          bm.id        AS bookmarkId,
          bm.groupId   AS groupId,
          g.name       AS groupName,
          g.colorHex   AS groupColorHex
        FROM boards AS b
        INNER JOIN board_category_cross_ref AS bc
          ON b.boardId = bc.boardId
        INNER JOIN categories AS c
          ON bc.categoryId = c.categoryId
        LEFT JOIN bookmark_boards AS bm
          ON bm.boardId = b.boardId
        LEFT JOIN `groups` AS g
          ON bm.groupId = g.groupId
        WHERE b.serviceId = :serviceId
          AND c.categoryId = :categoryId
    """)
    fun getBoardsWithBookmarkAndGroup(
        serviceId: Long,
        categoryId: Long
    ): Flow<List<BoardWithBookmarkAndGroup>>

    /** お気に入り登録／更新 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(b: BookmarkBoardEntity)

    /** お気に入り解除 */
    @Delete
    suspend fun deleteBookmark(b: BookmarkBoardEntity)

    /** Board に紐づく Bookmark レコードを取得 */
    @Query("SELECT * FROM bookmark_boards WHERE boardId = :boardId LIMIT 1")
    suspend fun findBookmarkByBoardId(boardId: Long): BookmarkBoardEntity?

    /** グループの登録／更新。戻り値は生成された groupId */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(g: BoardGroupEntity): Long

    /** グループ削除 */
    @Delete
    suspend fun deleteGroup(g: BoardGroupEntity)

    /**
     * boardUrl（boards.url）から単一 Board + Bookmark + Group 情報を取得
     */
    @Transaction
    @Query("""
        SELECT
    b.*,
    bm.id        AS id,           -- ← ここを bookmarkId→id に
    bm.groupId   AS groupId,
    g.name       AS groupName,
    g.colorHex   AS groupColorHex
        FROM boards AS b
        LEFT JOIN bookmark_boards AS bm
          ON bm.boardId = b.boardId
        LEFT JOIN `groups` AS g
          ON bm.groupId = g.groupId
        WHERE b.url = :boardUrl
        LIMIT 1
    """)
    fun getBookmarkWithGroupByUrlFlow(
        boardUrl: String
    ): Flow<BookmarkWithGroup?>
}
