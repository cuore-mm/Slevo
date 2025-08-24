package com.websarva.wings.android.bbsviewer.data.datasource.local.impl

import com.websarva.wings.android.bbsviewer.data.datasource.local.BbsLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.BoardCategoryCrossRefDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.CategoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardCategoryCrossRef
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room データベース操作を実装したローカルデータソース
 */
@Singleton
class BbsLocalDataSourceImpl @Inject constructor(
    private val serviceDao: BbsServiceDao,
    private val categoryDao: CategoryDao,
    private val boardDao: BoardDao,
    private val crossRefDao: BoardCategoryCrossRefDao
) : BbsLocalDataSource {

    override fun observeServicesWithCount() = serviceDao.getServicesWithBoardCount()

    override suspend fun upsertService(service: BbsServiceEntity) {
        serviceDao.upsert(service)
    }

    override suspend fun deleteService(serviceId: Long) {
        serviceDao.deleteById(serviceId)
    }

    override fun observeCategoriesWithCount(serviceId: Long) =
        categoryDao.getCategoriesWithBoardCount(serviceId)

    override suspend fun insertCategory(category:CategoryEntity) =
        categoryDao.insertCategory(category)


    override suspend fun clearCategories(serviceId: Long) {
        categoryDao.clearForService(serviceId)
    }

    override fun observeBoards(serviceId: Long): Flow<List<BoardEntity>> =
        boardDao.getBoardsForService(serviceId)

    override suspend fun insertOrGetBoard(board: BoardEntity): Long {
        // 1) まず挿入を試みる
        val rowId = boardDao.insertBoard(board)
        if (rowId != -1L) {
            // 新しく挿入できた → そのまま ID を返す
            return rowId
        }
        // 既に存在していた → URL で再取得
        return boardDao.findBoardIdByUrl(board.url)
    }


    override suspend fun clearBoards(serviceId: Long) {
        boardDao.clearForService(serviceId)
    }

    override suspend fun clearBoardCategories(boardId: Long) {
        crossRefDao.clearForBoard(boardId)
    }

    override suspend fun linkBoardCategory(boardId: Long, categoryId: Long) {
        crossRefDao.insert(BoardCategoryCrossRef(boardId, categoryId))
    }

    override fun observeBoardsForCategory(serviceId: Long, categoryId: Long): Flow<List<BoardEntity>> =
        // crossRef テーブル経由で取得
        crossRefDao.getBoardIdsForCategory(categoryId)
            .flatMapLatest { boardIds ->
                boardDao.getBoardsByIds(boardIds)
            }
}
