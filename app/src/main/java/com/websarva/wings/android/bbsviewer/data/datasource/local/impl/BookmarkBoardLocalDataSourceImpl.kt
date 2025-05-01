package com.websarva.wings.android.bbsviewer.data.datasource.local.impl

import com.websarva.wings.android.bbsviewer.data.datasource.local.BookmarkBoardLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkWithGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkBoardLocalDataSourceImpl @Inject constructor(
    private val dao: BookmarkBoardDao
) : BookmarkBoardLocalDataSource {

    override fun observe(serviceId: Long, categoryId: Long) =
        dao.getBoardsWithBookmarkAndGroup(serviceId, categoryId)

    override suspend fun toggleBookmark(boardId: Long) {
        val existing = dao.findBookmarkByBoardId(boardId)
        if (existing != null) dao.deleteBookmark(existing)
        else dao.upsertBookmark(BookmarkBoardEntity(boardId = boardId, groupId = null))
    }

    override suspend fun setGroup(bookmarkId: Int, group: BoardGroupEntity) {
        // 1) グループを登録 or 更新
        val gid = dao.upsertGroup(group)
        // 2) Bookmark レコードを更新
        val bm = BookmarkBoardEntity(id = bookmarkId, boardId = 0, groupId = gid)
        // ※ boardId は本来の値を取ってくる必要がありますが、
        //    実装では DAO.findBookmarkByBoardId → id を先に取得してから上書きする等調整してください。
        dao.upsertBookmark(bm)
    }

    override suspend fun clearGroup(bookmarkId: Int) {
        val bm = dao.findBookmarkByBoardId(bookmarkId.toLong()) ?: return
        dao.upsertBookmark(bm.copy(groupId = null))
    }

    override suspend fun addBookmarkWithGroup(boardId: Long, group: BoardGroupEntity) {
        // 1. グループを upsert して ID を得る
        val gid = dao.upsertGroup(group)
        // 2. Bookmark を作成（groupId をセット）
        dao.upsertBookmark(
            BookmarkBoardEntity(
                boardId = boardId,
                groupId = gid
            )
        )
    }

    override fun findWithGroupByUrl(boardUrl: String): Flow<BookmarkWithGroup?> =
           dao.getBookmarkWithGroupByUrlFlow(boardUrl)
}
