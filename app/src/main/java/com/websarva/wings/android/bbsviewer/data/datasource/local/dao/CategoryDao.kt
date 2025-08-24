package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * カテゴリ情報およびカテゴリに紐づくボード数のキャッシュ管理を行うDAO
 */
@Dao
interface CategoryDao {
    /**
     * 単一カテゴリをINSERT or REPLACEして、自動採番されたcategoryIdを返す
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE serviceId = :serviceId")
    suspend fun clearForService(serviceId: Long)

    @Transaction
    @Query(
        """
        SELECT c.*, COUNT(b.boardId) AS boardCount
          FROM categories AS c
     LEFT JOIN board_category_cross_ref AS bc ON c.categoryId = bc.categoryId
     LEFT JOIN boards AS b ON bc.boardId = b.boardId
         WHERE c.serviceId = :serviceId
      GROUP BY c.categoryId
        """
    )
    fun getCategoriesWithBoardCount(serviceId: Long): Flow<List<CategoryWithBoardCount>>

    @Query("SELECT * FROM categories WHERE serviceId = :serviceId")
    fun getCategoriesForService(serviceId: Long): Flow<List<CategoryEntity>>
}


/**
 * カテゴリ一覧取得時に板数を含めるためのデータクラス
 */
data class CategoryWithBoardCount(
    @Embedded val category: CategoryEntity,
    val boardCount: Int
)
