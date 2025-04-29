package com.websarva.wings.android.bbsviewer.data.repository

import android.util.Log
import androidx.core.net.toUri
import com.websarva.wings.android.bbsviewer.data.datasource.local.BbsLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryWithCount
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ServiceWithBoardCount
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BbsMenuDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * リポジトリ: BBSサービスの登録・更新・削除、およびカテゴリ・ボード情報の取得とキャッシュ管理を提供する。
 */
@Singleton
class BbsServiceRepository @Inject constructor(
    private val local: BbsLocalDataSource,
    private val remote: BbsMenuDataSource
) {
    companion object {
        private const val TAG = "BbsServiceRepository"
    }

    /**
     * 登録済みサービスと、そのサービスに紐づくボード件数を取得する。
     * @return Flow で監視可能な ServiceWithBoardCount のリスト
     */
    fun getAllServicesWithCount(): Flow<List<ServiceWithBoardCount>> =
        local.observeServicesWithCount()

    /**
     * 新規サービスを追加または既存サービスを更新する。
     * @param menuUrl メニュー取得用の URL
     * @details
     *  - リモートからカテゴリ・ボード情報を取得し、
     *    ドメインを抽出して BbsServiceEntity、CategoryEntity、BoardEntity にマッピング後
     *    local.saveAll で一括保存を行う。
     *  - 例外発生時はログに出力し、呼び出し元には例外を投げない。
     */
    suspend fun addService(menuUrl: String) = withContext(Dispatchers.IO) {
        try {
            val allCategories  = remote.fetchBbsMenu(menuUrl) ?: emptyList()
            // “ボードがゼロ件” のカテゴリは除外
            val nonEmptyCategories = allCategories.filter { it.boards.isNotEmpty() }
            val uri = menuUrl.toUri()
            val host = uri.host ?: throw IllegalArgumentException("Invalid URL: $menuUrl")
            val parts = host.split('.')
            val domain = if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
            val service = BbsServiceEntity(domain = domain, menuUrl = menuUrl)
            val catEntities = nonEmptyCategories.map { cat ->
                CategoryEntity(domain = domain, name = cat.categoryName)
            }
            val boardEntities = nonEmptyCategories.flatMap { cat ->
                cat.boards.map { bd ->
                    BoardEntity(
                        url = bd.url,
                        name = bd.name,
                        domain = domain,
                        categoryName = cat.categoryName
                    )
                }
            }
            local.saveAll(service, catEntities, boardEntities)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add or update service for URL: $menuUrl", e)
        }
    }

    /**
     * 選択された複数サービスをまとめて削除する。
     * @param domains 削除対象のサービスドメイン名リスト
     * @details CASCADE によって関連するカテゴリ・ボードも同時に削除される。
     */
    suspend fun removeService(domains: List<String>) = withContext(Dispatchers.IO) {
        local.deleteServices(domains)
    }

    /**
     * 指定ドメインのカテゴリごとのボード件数を取得する。
     * @param domain 対象サービスのドメイン
     * @return Flow で監視可能な CategoryWithCount のリスト
     */
    fun getCategoryCounts(domain: String): Flow<List<CategoryWithCount>> =
        local.observeCategoryCounts(domain)

    /**
     * 指定サービスのカテゴリ名に紐づくボード一覧を取得する。
     * @param domain 対象サービスのドメイン
     * @param categoryName 対象カテゴリ名
     * @return Flow で監視可能な BoardEntity のリスト
     */
    fun getBoardsForCategory(domain: String, categoryName: String): Flow<List<BoardEntity>> =
        local.observeBoards(domain, categoryName)

    /**
     * リモート情報を用いて指定サービスのカテゴリ・ボード情報を更新する。
     * @param domain 対象サービスのドメイン
     * @details
     *  - 現在の BbsServiceEntity を取得し、menuUrl があれば再フェッチ
     *  - local.saveAll でトランザクション内に一括置換
     */
    suspend fun refreshCategories(domain: String) = withContext(Dispatchers.IO) {
        val service = local.observeServicesWithCount()
            .map { list -> list.firstOrNull { it.service.domain == domain }?.service }
            .first() ?: return@withContext

        service.menuUrl?.let { menuUrl ->
            val categories = remote.fetchBbsMenu(menuUrl) ?: return@let
            val catEntities = categories.map {
                CategoryEntity(domain = domain, name = it.categoryName)
            }
            val boardEntities = categories.flatMap { cat ->
                cat.boards.map { bd ->
                    BoardEntity(
                        url = bd.url,
                        name = bd.name,
                        domain = domain,
                        categoryName = cat.categoryName
                    )
                }
            }
            local.saveAll(service, catEntities, boardEntities)
        }
    }
}
