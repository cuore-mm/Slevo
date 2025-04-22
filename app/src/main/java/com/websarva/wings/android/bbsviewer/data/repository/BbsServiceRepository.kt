package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.local.dao.CategoryDao
import com.websarva.wings.android.bbsviewer.data.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.local.dao.BbsServiceWithCategories
import com.websarva.wings.android.bbsviewer.data.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.local.entity.CategoryEntity
import com.websarva.wings.android.bbsviewer.data.local.dao.CategoryWithBoards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * BBSサービスとそのカテゴリ・板のキャッシュ管理を行うリポジトリ
 */
class BbsServiceRepository @Inject constructor(
    private val serviceDao: BbsServiceDao,
    private val categoryDao: CategoryDao,
    private val networkRepo: BbsMenuRepository
) {
    /**
     * 登録済みサービスとそのキャッシュ済みカテゴリを監視取得する
     */
    fun getAllServices(): Flow<List<BbsServiceWithCategories>> =
        serviceDao.getAllServicesWithCategories()

    /**
     * サービスを登録または更新し、menuUrlが設定されていればカテゴリを取得してキャッシュする
     */
    suspend fun addService(service: BbsServiceEntity) = withContext(Dispatchers.IO) {
        serviceDao.upsertService(service)
        service.menuUrl?.let { menuUrl ->
            // メニュー取得とキャッシュ
            val categories = networkRepo.fetchBbsMenu(menuUrl) ?: return@let
            // 既存キャッシュのクリア
            categoryDao.clearBoardsFor(service.serviceId)
            categoryDao.clearCategoriesFor(service.serviceId)
            // エンティティへマッピング
            val catEntities = categories.map { CategoryEntity(serviceId = service.serviceId, name = it.categoryName) }
            val boardEntities = categories.flatMap { cat ->
                cat.boards.map { bd ->
                    BoardEntity(
                        url = bd.url,
                        name = bd.name,
                        serviceId = service.serviceId,
                        categoryName = cat.categoryName
                    )
                }
            }
            // Roomへ保存
            categoryDao.insertCategories(catEntities)
            categoryDao.insertBoards(boardEntities)
        }
    }

    /**
     * 登録済みサービスを削除する（カテゴリと板も合わせて削除される）
     */
    suspend fun removeService(service: BbsServiceEntity) =
        withContext(Dispatchers.IO) { serviceDao.deleteService(service) }

    /**
     * 指定サービスのキャッシュ済みカテゴリおよび板を取得する
     */
    fun getCategoriesForService(serviceId: String): Flow<List<CategoryWithBoards>> =
        categoryDao.getCategoriesWithBoards(serviceId)

    /** serviceId＋categoryName から Flow<List<BoardEntity>> を返す */
    fun getBoardsForCategory(
        serviceId: String,
        categoryName: String
    ): Flow<List<BoardEntity>> =
        categoryDao.getBoardsForCategory(serviceId, categoryName)

    /**
     * 既存サービスのカテゴリキャッシュをネットワークから更新する
     */
    suspend fun refreshCategories(serviceId: String) = withContext(Dispatchers.IO) {
        // サービス情報を取得してmenuUrlを確認
        val service = serviceDao.getAllServicesWithCategories()
            .map { it.firstOrNull { s -> s.service.serviceId == serviceId }?.service }
            .first() ?: return@withContext
        service.menuUrl?.let { menuUrl ->
            val categories = networkRepo.fetchBbsMenu(menuUrl) ?: return@let
            categoryDao.clearBoardsFor(serviceId)
            categoryDao.clearCategoriesFor(serviceId)
            val catEntities = categories.map { CategoryEntity(serviceId, it.categoryName) }
            val boardEntities = categories.flatMap { c ->
                c.boards.map { b ->
                    BoardEntity(
                        url = b.url,
                        name = b.name,
                        serviceId = serviceId,
                        categoryName = c.categoryName
                    )
                }
            }
            categoryDao.insertCategories(catEntities)
            categoryDao.insertBoards(boardEntities)
        }
    }

    /**
     * 単一板サービスに板を追加する
     */
    suspend fun addBoardToService(board: BoardEntity) = withContext(Dispatchers.IO) {
        val category = CategoryEntity(
            serviceId = board.serviceId,
            name = board.categoryName
        )
        categoryDao.insertCategories(listOf(category))
        categoryDao.insertBoards(listOf(board))
    }

    /**
     * serviceId ごとの生カテゴリリストをそのまま返す
     */
    fun getCategoryEntities(
        serviceId: String
    ): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesForService(serviceId)

    fun getBoardCount(
        serviceId: String,
        categoryName: String
    ): Flow<Int> =
        categoryDao.getBoardCount(serviceId, categoryName)
}

