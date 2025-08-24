package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BoardWithBookmarkAndGroup
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.GroupWithBoards
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkBoardRepository @Inject constructor(
    private val boardDao: BookmarkBoardDao,
    private val groupDao: BoardBookmarkGroupDao,
    private val boardEntityDao: BoardDao,
    private val boardRepository: BoardRepository,
) {

    fun observeGroups(): Flow<List<BoardBookmarkGroupEntity>> =
        groupDao.getAllGroupsSorted()

    suspend fun reorderGroups(updated: List<BoardBookmarkGroupEntity>) {
        groupDao.updateGroups(updated)
    }

    /** 新規グループを末尾に追加 */
    suspend fun addGroupAtEnd(name: String, colorName: String) {
        if (groupDao.findByName(name) == null) {
            // まず既存最大 + 1
            val nextOrder = groupDao.getMaxSortOrder() + 1
            val newGroup = BoardBookmarkGroupEntity(
                name      = name,
                colorName = colorName,
                sortOrder = nextOrder
            )
            groupDao.insertGroup(newGroup)
        }
    }

    suspend fun updateGroup(groupId: Long, name: String, colorName: String) {
        val existing = groupDao.findByName(name)
        if (existing == null || existing.groupId == groupId) {
            groupDao.updateGroupInfo(groupId, name, colorName)
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        groupDao.deleteGroupById(groupId)
    }

    /**
     * お気に入り登録または更新
     * @param bookmark 登録・更新対象の BookmarkBoardEntity
     */
    suspend fun upsertBookmark(bookmark: BookmarkBoardEntity) = withContext(Dispatchers.IO) {
        boardDao.upsertBookmark(bookmark)
    }

    /**
     * BoardInfo からお気に入り登録。boardId が未登録の場合は boards テーブルに挿入する。
     * @return 登録に使用した boardId
     */
    suspend fun upsertBookmark(boardInfo: BoardInfo, groupId: Long): Long = withContext(Dispatchers.IO) {
        val bId = boardRepository.ensureBoard(boardInfo)
        boardDao.upsertBookmark(BookmarkBoardEntity(bId, groupId))
        bId
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

    suspend fun findBoardByUrl(boardUrl: String): BoardEntity? =
        boardDao.findBoardByUrl(boardUrl)

    fun observeAllBoards(): Flow<List<BoardEntity>> =
        boardEntityDao.getAllBoards()
}
