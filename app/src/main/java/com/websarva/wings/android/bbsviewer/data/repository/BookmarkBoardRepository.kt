package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.BookmarkBoardLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkWithGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkBoardRepository @Inject constructor(
    private val local: BookmarkBoardLocalDataSource
) {
    fun getBoardsWithGroup(serviceId: Long, categoryId: Long) =
        local.observe(serviceId, categoryId)

    suspend fun toggleBookmark(boardId: Long) =
        local.toggleBookmark(boardId)

    suspend fun assignGroup(bookmarkId: Int, name: String, colorHex: String) {
        local.setGroup(bookmarkId, BoardGroupEntity(name = name, colorHex = colorHex))
    }

    suspend fun removeGroup(bookmarkId: Int) =
        local.clearGroup(bookmarkId)

    /**
     * 新規にお気に入り登録するとき、同時にグループを作成して紐付け
     */
    suspend fun addWithGroup(boardId: Long, groupName: String, groupColorHex: String) {
        local.addBookmarkWithGroup(
            boardId,
            BoardGroupEntity(name = groupName, colorHex = groupColorHex)
        )
    }

    /**
      * boardUrl から お気に入り＋グループ情報をまとめて取得する Flow
      */
     fun getBookmarkWithGroupByUrl(url: String): Flow<BookmarkWithGroup?> =
           local.findWithGroupByUrl(url)
}
