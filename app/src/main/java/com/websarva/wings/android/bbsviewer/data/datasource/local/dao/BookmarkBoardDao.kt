package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.*
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BoardWithBookmarkAndGroup
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkWithGroup
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
          bm.groupId   AS groupId,
          g.name       AS groupName,
          g.colorName   AS groupColorName
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

    /** boardId を指定してお気に入り解除 */
    @Query("DELETE FROM bookmark_boards WHERE boardId = :boardId")
    suspend fun deleteBookmarkByBoardId(boardId: Long)

    /** Board に紐づく Bookmark レコードを取得 */
    @Query("SELECT * FROM bookmark_boards WHERE boardId = :boardId LIMIT 1")
    suspend fun findBookmarkByBoardId(boardId: Long): BookmarkBoardEntity?

    /**
     * boardUrl（boards.url）から単一 Board + Bookmark + Group 情報を取得
     */
    @Transaction
    @Query("""
        SELECT
    b.*,
    bm.groupId   AS groupId,
    g.name       AS groupName,
    g.colorName  AS groupColorName
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

    @Transaction
    @Query("SELECT * FROM boards WHERE url = :boardUrl LIMIT 1")
    fun getBoardWithBookmarkAndGroupByUrlFlow(boardUrl: String): Flow<BoardWithBookmarkAndGroup?>

    /** boardUrl から BoardEntity を取得 */
    @Query("SELECT * FROM boards WHERE url = :boardUrl LIMIT 1")
    suspend fun findBoardByUrl(boardUrl: String): BoardEntity?
}
