package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import kotlinx.coroutines.flow.Flow

/**
 * BBSサービス情報と関連ボード件数を管理するDAOインターフェース
 */
@Dao
interface BbsServiceDao {
    /**
     * サービスを新規登録または更新（同一domainなら置換）
     * @param service 保存または更新するBbsServiceEntity
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(service: BbsServiceEntity)

    /**
     * 指定したドメインに対応する BBS サービスを削除します。（外部キーのCASCADEにより関連カテゴリ・ボードも一括削除）
     * @param domain 削除対象のサービスのドメイン名
     */
    @Query("DELETE FROM bbs_services WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    /**
     * 指定した複数のドメインに対応する BBS サービスをまとめて削除します。（外部キーのCASCADEにより関連カテゴリ・ボードも一括削除）
     * @param domains 削除対象とするサービスのドメイン名リスト
     */
    @Query("DELETE FROM bbs_services WHERE domain IN (:domains)")
    suspend fun deleteByDomains(domains: List<String>)

    /**
     * サービスごとに所属ボード数を集計し、
     * ServiceWithBoardCount オブジェクトとして Flow で返す
     * NOTE: ボードが0件でもサービスは取得される (LEFT JOIN)
     */
    @Transaction
    @Query(
        """
    SELECT
      s.*,                             -- サービス情報全カラム
      COUNT(b.url) AS boardCount      -- ボードURLをカウントしboardCountとして返却
      FROM bbs_services AS s
 LEFT JOIN boards AS b
        ON s.domain = b.domain        -- 外部キーでJOIN
  GROUP BY s.domain                 -- domainごとに集計
  """
    )
    fun getServicesWithBoardCount(): Flow<List<ServiceWithBoardCount>>
}

/**
 * サービスとそのボード数をまとめて受け取るPOJOクラス
 * Roomのクエリ結果をマッピングするためのデータクラス
 */
data class ServiceWithBoardCount(
    /** サービス情報のエンベッド
     *  BbsServiceEntity の全フィールドを内包
     */
    @Embedded
    val service: BbsServiceEntity,

    /**
     * サービスに紐づく boards テーブルの件数
     */
    val boardCount: Int
)
