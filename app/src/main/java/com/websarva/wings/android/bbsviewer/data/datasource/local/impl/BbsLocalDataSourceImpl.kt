package com.websarva.wings.android.bbsviewer.data.datasource.local.impl

import androidx.room.withTransaction
import com.websarva.wings.android.bbsviewer.data.datasource.local.AppDatabase
import com.websarva.wings.android.bbsviewer.data.datasource.local.BbsLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryWithCount
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ServiceWithBoardCount
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room データベース操作を実装したローカルデータソース
 */
@Singleton
class BbsLocalDataSourceImpl @Inject constructor(
    private val database: AppDatabase,
    private val serviceDao: BbsServiceDao,
    private val categoryDao: CategoryDao,
    private val boardDao: BoardDao
) : BbsLocalDataSource {

    /** サービス一覧＋ボード件数の監視取得 */
    override fun observeServicesWithCount(): Flow<List<ServiceWithBoardCount>> =
        serviceDao.getServicesWithBoardCount()

    /** サービス登録/更新 */
    override suspend fun upsertService(service: BbsServiceEntity) =
        serviceDao.upsertService(service)

    /** サービス削除 */
    override suspend fun deleteServices(domains: List<String>) {
        serviceDao.deleteByDomains(domains)
    }

    /** カテゴリ登録/更新 */
    override suspend fun upsertCategories(categories: List<CategoryEntity>) =
        categoryDao.insertCategories(categories)

    /** カテゴリ一括削除 */
    override suspend fun clearCategories(domain: String) =
        categoryDao.clearCategoriesFor(domain)

    /** カテゴリ件数取得 */
    override fun observeCategoryCounts(domain: String): Flow<List<CategoryWithCount>> =
        categoryDao.getCategoryWithCount(domain)

    /** ボード一覧取得 */
    override fun observeBoards(domain: String, categoryName: String): Flow<List<BoardEntity>> =
        boardDao.getBoards(domain, categoryName)

    /** ボード登録/更新 */
    override suspend fun upsertBoards(boards: List<BoardEntity>) =
        boardDao.insertBoards(boards)

    /** ボード一括削除 */
    override suspend fun clearBoards(domain: String) =
        boardDao.clearBoardsForService(domain)

    /** 個別ボード削除 */
    override suspend fun deleteBoard(board: BoardEntity) =
        boardDao.deleteBoard(board)

    /**
     * 全エンティティをトランザクションでまとめて登録
     * - serviceDao, categoryDao, boardDao の操作をwithTransactionで原子的に実行
     */
    override suspend fun saveAll(
        service: BbsServiceEntity,
        categories: List<CategoryEntity>,
        boards: List<BoardEntity>
    ) {
        database.withTransaction {
            serviceDao.upsertService(service)
            categoryDao.clearCategoriesFor(service.domain)
            categoryDao.insertCategories(categories)
            boardDao.clearBoardsForService(service.domain)
            boardDao.insertBoards(boards)
        }
    }
}
