package com.websarva.wings.android.slevo.data.datasource.local.dao.bbs

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import kotlinx.coroutines.flow.Flow

/**
 * BBSサービス情報と関連ボード件数を管理するDAOインターフェース
 */
@Dao
interface BbsServiceDao {
    /**
     * サービスを登録または更新
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(service: BbsServiceEntity): Long

    /**
     * サービス一覧を板数付きで取得
     */
    @Transaction
    @Query(
        """
        SELECT s.*, COUNT(b.boardId) AS boardCount
          FROM services AS s
     LEFT JOIN boards AS b ON s.serviceId = b.serviceId
      GROUP BY s.serviceId
        """
    )
    fun getServicesWithBoardCount(): Flow<List<ServiceWithBoardCount>>

    @Query("SELECT * FROM services WHERE serviceId = :id")
    suspend fun getById(id: Long): BbsServiceEntity?

    @Query("DELETE FROM services WHERE serviceId = :id")
    suspend fun deleteById(id: Long)

    /** ドメイン名からサービスを取得 */
    @Query("SELECT * FROM services WHERE domain = :domain LIMIT 1")
    suspend fun findByDomain(domain: String): BbsServiceEntity?
}

/**
 * サービス一覧取得時に板数を含めるためのデータクラス
 */
data class ServiceWithBoardCount(
    @Embedded val service: BbsServiceEntity,
    val boardCount: Int
)
