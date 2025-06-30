package com.websarva.wings.android.bbsviewer.data.repository

import android.util.Log
import androidx.core.net.toUri
import com.websarva.wings.android.bbsviewer.data.datasource.local.BbsLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BbsMenuDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.CategoryEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryWithBoardCount
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ServiceWithBoardCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BbsServiceRepository @Inject constructor(
    private val local: BbsLocalDataSource,
    private val remote: BbsMenuDataSource
) {
    companion object {
        private const val TAG = "BbsServiceRepository"
    }

    /** サービス一覧＋板数 */
    fun getAllServicesWithCount(): Flow<List<ServiceWithBoardCount>> =
        local.observeServicesWithCount()

    /**
     * サービス追加／更新
     */
    suspend fun addOrUpdateService(menuUrl: String) = withContext(Dispatchers.IO) {
        try {
            // リモートからカテゴリ→板データ取得
            val allCategories = remote.fetchBbsMenu(menuUrl) ?: emptyList()
            val nonEmpty = allCategories.filter { it.boards.isNotEmpty() }

            // ドメイン抽出
            val uri = menuUrl.toUri()
            val host = uri.host ?: throw IllegalArgumentException("Invalid URL: $menuUrl")
            val parts = host.split('.')
            val domain = if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host

            // 1) Service 登録（autoGenerate ID が返る）
            val service = BbsServiceEntity(
                domain = domain,
                displayName = domain,
                menuUrl = menuUrl
            )
            local.upsertService(service)

            // 2) 既存のカテゴリ／板クリア
            val svcId = local.observeServicesWithCount()
                .map { list -> list.first { it.service.domain == domain }.service.serviceId }
                .first()
            local.clearCategories(svcId)
            local.clearBoards(svcId)

            // 3) カテゴリ登録＋ID取得
            nonEmpty.forEach { cat ->
                val newCat = CategoryEntity(serviceId = svcId, name = cat.categoryName)
                val catId = local.insertCategory(newCat)   // ← カテゴリIDを直接取得

                // 4) ボード登録＋カテゴリ紐付け
                cat.boards.forEach { bd ->
                    val newBoard = BoardEntity(
                        serviceId = svcId,
                        url       = bd.url,
                        name      = bd.name
                    )
                    // 挿入 or 既存取得
                    val boardId = local.insertOrGetBoard(newBoard)
                    // 複数カテゴリ分、同一boardIdで複数リンクしてもOK
                    local.linkBoardCategory(boardId, catId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "サービス追加／更新失敗: $menuUrl", e)
        }
    }

    /** サービス削除 */
    suspend fun removeService(serviceId: Long) = withContext(Dispatchers.IO) {
        local.deleteService(serviceId)
    }

    /** カテゴリ一覧＋板数 */
    fun getCategoriesWithCount(serviceId: Long): Flow<List<CategoryWithBoardCount>> =
        local.observeCategoriesWithCount(serviceId)

    /**
     * 指定サービス・カテゴリの板一覧を取得
     */
    fun getBoardsForCategory(serviceId: Long, categoryId: Long): Flow<List<BoardEntity>> =
        local.observeBoardsForCategory(serviceId, categoryId)

    /** 板名で検索（全サービス） */
    fun searchBoards(query: String): Flow<List<BoardEntity>> =
        local.searchBoards(query)

    /** 板名で検索（サービス単位） */
    fun searchBoardsInService(serviceId: Long, query: String): Flow<List<BoardEntity>> =
        local.searchBoardsInService(serviceId, query)

    /** 板名からカテゴリIDを取得 */
    fun findCategoryIdsForBoardName(serviceId: Long, query: String): Flow<List<Long>> =
        local.findCategoryIdsForBoardName(serviceId, query)
}
