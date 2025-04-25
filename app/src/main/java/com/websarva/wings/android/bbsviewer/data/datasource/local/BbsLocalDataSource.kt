package com.websarva.wings.android.bbsviewer.data.datasource.local

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryWithCount
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ServiceWithBoardCount
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * サービス／カテゴリ／ボードの永続化操作をまとめて提供するローカルデータソース
 */
interface BbsLocalDataSource {
    /**
     * サービス一覧とそれぞれのボード数を取得する
     * @return サービス情報とボード数をまとめたリストをFlowで返却
     */
    fun observeServicesWithCount(): Flow<List<ServiceWithBoardCount>>

    /**
     * サービス情報を新規登録または更新
     * @param service 保存対象のBbsServiceEntity
     */
    suspend fun upsertService(service: BbsServiceEntity)

    /**
     * 指定サービスを削除（Cascadeでカテゴリ・ボードも併せて削除）
     * @param service 削除対象のBbsServiceEntity
     */
    suspend fun deleteService(service: BbsServiceEntity)

    /**
     * カテゴリ一覧を一括登録または更新
     * @param categories 保存対象のCategoryEntityリスト
     */
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    /**
     * 指定サービス(domain)に紐づくすべてのカテゴリを削除
     * @param domain サービスドメイン
     */
    suspend fun clearCategories(domain: String)

    /**
     * カテゴリとそのボード件数を取得
     * @param domain サービスドメイン
     * @return カテゴリ名とボード数を持つCategoryWithCountのリストをFlowで返却
     */
    fun observeCategoryCounts(domain: String): Flow<List<CategoryWithCount>>

    /**
     * 指定カテゴリに属するボード一覧を取得
     * @param domain サービスドメイン
     * @param categoryName カテゴリ名
     * @return BoardEntityのリストをFlowで返却
     */
    fun observeBoards(domain: String, categoryName: String): Flow<List<BoardEntity>>

    /**
     * ボード一覧を一括登録または更新
     * @param boards 保存対象のBoardEntityリスト
     */
    suspend fun upsertBoards(boards: List<BoardEntity>)

    /**
     * 指定サービス(domain)に紐づくすべてのボードを削除
     * @param domain サービスドメイン
     */
    suspend fun clearBoards(domain: String)

    /**
     * 特定ボードを削除
     * @param board 削除対象のBoardEntity
     */
    suspend fun deleteBoard(board: BoardEntity)

    /**
     * サービス、カテゴリ、ボードをまとめて登録するトランザクション
     * - 全操作を原子的に行い、途中失敗時はロールバック
     *
     * @param service 登録対象のサービス
     * @param categories 登録対象のカテゴリリスト
     * @param boards 登録対象のボードリスト
     */
    suspend fun saveAll(
        service: BbsServiceEntity,
        categories: List<CategoryEntity>,
        boards: List<BoardEntity>
    )
}
