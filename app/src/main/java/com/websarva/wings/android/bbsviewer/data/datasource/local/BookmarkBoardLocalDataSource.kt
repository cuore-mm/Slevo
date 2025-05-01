package com.websarva.wings.android.bbsviewer.data.datasource.local

import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardWithBookmarkAndGroup
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkWithGroup
import kotlinx.coroutines.flow.Flow

interface BookmarkBoardLocalDataSource {
    fun observe(serviceId: Long, categoryId: Long): Flow<List<BoardWithBookmarkAndGroup>>
    suspend fun toggleBookmark(boardId: Long)
    suspend fun setGroup(bookmarkId: Int, group: BoardGroupEntity)
    suspend fun clearGroup(bookmarkId: Int)

    /** 新規お気に入り＋グループ作成を一度に */
    suspend fun addBookmarkWithGroup(boardId: Long, group: BoardGroupEntity)

    /** URL から Bookmark＋Group を返す Flow */
    fun findWithGroupByUrl(boardUrl: String): Flow<BookmarkWithGroup?>
}
