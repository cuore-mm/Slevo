package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardWithBookmarkAndGroup
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkBoardRepository @Inject constructor(
    private val boardDao: BookmarkBoardDao,
    private val groupDao: BoardBookmarkGroupDao,
) {

    fun observeGroups(): Flow<List<BoardBookmarkGroupEntity>> =
        groupDao.getAllGroupsSorted()

    suspend fun reorderGroups(updated: List<BoardBookmarkGroupEntity>) {
        groupDao.updateGroups(updated)
    }

    /** 新規グループを末尾に追加 */
    suspend fun addGroupAtEnd(name: String, colorHex: String) {
        // まず既存最大 + 1
        val nextOrder = groupDao.getMaxSortOrder() + 1
        val newGroup = BoardBookmarkGroupEntity(
            name      = name,
            colorHex  = colorHex,
            sortOrder = nextOrder
        )
        groupDao.insertGroup(newGroup)
    }

    /**
     * お気に入り登録または更新
     * @param bookmark 登録・更新対象の BookmarkBoardEntity
     */
    suspend fun upsertBookmark(bookmark: BookmarkBoardEntity) = withContext(Dispatchers.IO) {
        boardDao.upsertBookmark(bookmark)
    }

    /**
     * boardId を指定してお気に入り解除
     */
    suspend fun deleteBookmark(boardId: Long) = withContext(Dispatchers.IO) {
        boardDao.deleteBookmarkByBoardId(boardId)
    }

    /** グループ別のお気に入り板一覧を取得 */
    fun observeGroupsWithBoards(): Flow<List<GroupWithBoards>> =
        groupDao.getGroupsWithBoards()

    fun getBoardWithBookmarkAndGroupByUrlFlow(boardUrl: String): Flow<BoardWithBookmarkAndGroup?> {
        return boardDao.getBoardWithBookmarkAndGroupByUrlFlow(boardUrl)
    }
}
