package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * カテゴリ名とそのカテゴリ内に属するボード件数を保持するデータクラス
 * Room のクエリ結果をマッピングするために使用
 *
 * @property name カテゴリ名
 * @property domain サービスを識別するドメイン名
 * @property boardCount カテゴリ内のボード数
 */
data class CategoryWithCount(
    val name: String,
    val domain: String,
    val boardCount: Int
)

/**
 * カテゴリ情報およびカテゴリに紐づくボード数のキャッシュ管理を行うDAO
 */
@Dao
interface CategoryDao {

    /**
     * カテゴリリストを一括挿入または更新（同一主キーが存在する場合は置換）
     *
     * @param categories 保存対象のCategoryEntityリスト
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    /**
     * 指定サービス(domain)に紐づく全カテゴリを削除
     *
     * @param serviceId 削除対象のサービスドメイン名
     */
    @Query("DELETE FROM categories WHERE domain = :serviceId")
    suspend fun clearCategoriesFor(serviceId: String)

    /**
     * 指定サービス内の各カテゴリについて、
     * そのカテゴリに属するボード数を集計し CategoryWithCount として取得する
     *
     * - LEFT JOIN を用いることで、ボードが0件のカテゴリも取得可能
     * - 集計結果は Flow でリアクティブに購読可能
     *
     * @param domain サービスを一意に識別するドメイン名
     * @return カテゴリごとの板数を持つ CategoryWithCount のリスト
     */
    @Transaction
    @Query(
        """
        SELECT
          c.name       AS name,
          c.domain     AS domain,
          COUNT(b.url) AS boardCount
        FROM categories AS c
        LEFT JOIN boards AS b
          ON c.domain = b.domain
         AND c.name   = b.categoryName
        WHERE c.domain = :domain
        GROUP BY c.domain, c.name
        ORDER BY MIN(c.ROWID)   -- ← 挿入順に並び替え
        """
    )
    fun getCategoryWithCount(domain: String): Flow<List<CategoryWithCount>>
}
