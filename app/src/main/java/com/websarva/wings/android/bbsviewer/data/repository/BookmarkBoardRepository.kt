package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardWithBookmarkAndGroup
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.util.parseServiceName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkBoardRepository @Inject constructor(
    private val boardDao: BookmarkBoardDao,
    private val groupDao: BoardBookmarkGroupDao,
    private val serviceDao: BbsServiceDao,
    private val boardEntityDao: BoardDao,
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
        var bId = boardInfo.boardId
        if (bId == 0L) {
            // URL からサービス名を取得し、存在しなければサービスも登録
            val serviceName = parseServiceName(boardInfo.url)
            val service = serviceDao.findByDomain(serviceName) ?: run {
                val svc = BbsServiceEntity(domain = serviceName, displayName = serviceName, menuUrl = null)
                val id = serviceDao.upsert(svc)
                svc.copy(serviceId = id)
            }

            val insertedId = boardEntityDao.insertBoard(
                BoardEntity(
                    serviceId = service.serviceId,
                    url = boardInfo.url,
                    name = boardInfo.name
                )
            )
            bId = if (insertedId != -1L) insertedId else boardEntityDao.findBoardIdByUrl(boardInfo.url)
        }

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

    fun observeAllBoards(): Flow<List<BoardEntity>> =
        boardEntityDao.getAllBoards()

    suspend fun findBoardByUrl(boardUrl: String): BoardEntity? =
        boardDao.findBoardByUrl(boardUrl)
}
