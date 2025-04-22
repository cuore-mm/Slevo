package com.websarva.wings.android.bbsviewer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.websarva.wings.android.bbsviewer.data.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 掲示板サービスとそのカテゴリをまとめて取得するDAO
 */
@Dao
interface BbsServiceDao {
    /**
     * サービス一覧と、その中のカテゴリを一括で取得
     */
    @Transaction
    @Query("SELECT * FROM bbs_services ORDER BY displayName")
    fun getAllServicesWithCategories(): Flow<List<BbsServiceWithCategories>>

    /**
     * 新規サービス登録または更新
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(service: BbsServiceEntity)

    /**
     * サービス削除（Cascadeでカテゴリと板も削除される）
     */
    @Delete
    suspend fun deleteService(service: BbsServiceEntity)
}

/**
 * カテゴリおよび板のキャッシュ管理用DAO
 */
@Dao
interface CategoryDao {
    /**
     * 指定サービスのカテゴリとその板一覧を取得
     */
    @Transaction
    @Query("SELECT * FROM categories WHERE serviceId = :serviceId ORDER BY name")
    fun getCategoriesWithBoards(serviceId: String): Flow<List<CategoryWithBoards>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoards(boards: List<BoardEntity>)

    @Query("DELETE FROM boards WHERE serviceId = :serviceId")
    suspend fun clearBoardsFor(serviceId: String)

    @Query("DELETE FROM categories WHERE serviceId = :serviceId")
    suspend fun clearCategoriesFor(serviceId: String)

    /** serviceId ごとの CategoryEntity 一覧を取得 */
    @Query("""
    SELECT * 
      FROM categories 
     WHERE serviceId = :serviceId 
     ORDER BY name
  """)
    fun getCategoriesForService(
        serviceId: String
    ): Flow<List<CategoryEntity>>

    /** サービスID＋カテゴリ名で板一覧を取得 */
    @Query("""
      SELECT * 
        FROM boards 
       WHERE serviceId = :serviceId 
         AND categoryName = :categoryName
       ORDER BY name
    """)
    fun getBoardsForCategory(
        serviceId: String,
        categoryName: String
    ): Flow<List<BoardEntity>>

    /** serviceId＋categoryName ごとの板数を取得 */
    @Query("""
    SELECT COUNT(*) 
      FROM boards 
     WHERE serviceId = :serviceId 
       AND categoryName = :categoryName
  """)
    fun getBoardCount(
        serviceId: String,
        categoryName: String
    ): Flow<Int>
}

/**
 * CategoryEntity と紐付く BoardEntity 一覧をまとめる
 */
data class CategoryWithBoards(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "name",
        entityColumn = "categoryName"
    )
    val boards: List<BoardEntity>
)

/**
 * BbsServiceEntity と紐付くカテゴリ一覧をまとめる
 */
data class BbsServiceWithCategories(
    @Embedded val service: BbsServiceEntity,
    @Relation(
        parentColumn = "serviceId",
        entityColumn = "serviceId"
    )
    val categories: List<CategoryEntity>
)
