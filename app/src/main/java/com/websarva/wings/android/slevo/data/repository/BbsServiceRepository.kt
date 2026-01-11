package com.websarva.wings.android.slevo.data.repository

import android.util.Log
import androidx.core.net.toUri
import com.websarva.wings.android.slevo.data.datasource.local.BbsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.remote.BbsMenuDataSource
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.CategoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryWithBoardCount
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.ServiceWithBoardCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BbsServiceRepository @Inject constructor(
    private val local: BbsLocalDataSource,
    private val remote: BbsMenuDataSource
) {
    companion object {
        private const val TAG = "BbsServiceRepository"
        private const val DEFAULT_MENU_URL = "https://menu.5ch.net/bbsmenu.json"
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
            Timber.e(e, "サービス追加／更新失敗: $menuUrl")
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

    /**
     * 指定サービスに属するすべての板を取得
     */
    fun getBoards(serviceId: Long): Flow<List<BoardEntity>> =
        local.observeBoards(serviceId)

    /**
     * bbsmenu から boardKey に対応するホストを取得する。
     * DBへの保存は行わず、URL変換用途のみに利用する。
     */
    suspend fun resolveHostByBoardKeyFromMenu(boardKey: String): String? =
        withContext(Dispatchers.IO) {
            val menu = remote.fetchBbsMenu(DEFAULT_MENU_URL) ?: return@withContext null
            val target = menu.asSequence()
                .flatMap { it.boards.asSequence() }
                .mapNotNull { board ->
                    val uri = board.url.toUri()
                    val segment = uri.pathSegments.firstOrNull()
                    if (segment == boardKey) uri else null
                }
                .firstOrNull()
            target?.host
        }
}
