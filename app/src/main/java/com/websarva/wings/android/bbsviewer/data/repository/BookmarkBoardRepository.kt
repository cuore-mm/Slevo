package com.websarva.wings.android.bbsviewer.data.repository

import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkWithGroup
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkBoardRepository @Inject constructor(
    private val boardDao: BookmarkBoardDao,
    private val groupDao: BoardGroupDao,
) {

    fun observeGroups(): Flow<List<BoardGroupEntity>> =
        groupDao.getAllGroupsSorted()

    suspend fun reorderGroups(updated: List<BoardGroupEntity>) {
        groupDao.updateGroups(updated)
    }

    /** 新規グループを末尾に追加 */
    suspend fun addGroupAtEnd(name: String, colorHex: String) {
        // まず既存最大 + 1
        val nextOrder = groupDao.getMaxSortOrder() + 1
        val newGroup = BoardGroupEntity(
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
     * お気に入り解除
     */
    suspend fun deleteBookmark(bookmark: BookmarkBoardEntity) = withContext(Dispatchers.IO) {
        boardDao.deleteBookmark(bookmark)
    }

    /** グループ別のお気に入り板一覧を取得 */
    fun observeGroupsWithBoards(): Flow<List<GroupWithBoards>> =
        groupDao.getGroupsWithBoards()

    /**
     * boardUrl をキーにして、お気に入り情報とグループ情報を Flow で取得します。
     * このメソッドを追加します。
     *
     * @param boardUrl 検索対象の板 URL
     * @return BookmarkWithGroup の Flow
     */
    fun getBookmarkWithGroupByUrlFlow(boardUrl: String): Flow<BookmarkWithGroup?> {
        return boardDao.getBookmarkWithGroupByUrlFlow(boardUrl)
    }
}
