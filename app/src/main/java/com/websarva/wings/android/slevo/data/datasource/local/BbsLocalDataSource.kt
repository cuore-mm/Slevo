package com.websarva.wings.android.slevo.data.datasource.local

import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryWithBoardCount
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.ServiceWithBoardCount
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * ローカルデータソース：サービス／カテゴリ／板の操作をまとめる
 */
interface BbsLocalDataSource {
    /** 登録済みサービス一覧（板数付き）を監視 */
    fun observeServicesWithCount(): Flow<List<ServiceWithBoardCount>>

    /** サービスを登録または更新 */
    suspend fun upsertService(service: BbsServiceEntity)

    /** サービスを削除 */
    suspend fun deleteService(serviceId: Long)

    /** 指定サービスのカテゴリ一覧（板数付き）を監視 */
    fun observeCategoriesWithCount(serviceId: Long): Flow<List<CategoryWithBoardCount>>

    /** カテゴリ一覧を登録または更新 */
    suspend fun insertCategory(category: CategoryEntity): Long

    /** 指定サービスのカテゴリをクリア */
    suspend fun clearCategories(serviceId: Long)

    /** 指定サービスの板一覧を監視 */
    fun observeBoards(serviceId: Long): Flow<List<BoardEntity>>

    /** 板一覧を登録または更新 */
    suspend fun insertOrGetBoard(board: BoardEntity): Long

    /** 指定サービスの板をクリア */
    suspend fun clearBoards(serviceId: Long)

    /** 指定板のカテゴリ関連をクリア */
    suspend fun clearBoardCategories(boardId: Long)

    /** 板⇔カテゴリの紐付けを登録 */
    suspend fun linkBoardCategory(boardId: Long, categoryId: Long)

    /**
     * サービスIDとカテゴリIDから、そのカテゴリに紐づく板一覧を取得
     */
    fun observeBoardsForCategory(serviceId: Long, categoryId: Long): Flow<List<BoardEntity>>
}
