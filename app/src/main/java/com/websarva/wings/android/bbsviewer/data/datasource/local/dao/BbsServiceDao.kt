package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Delete
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
     * 指定サービスを削除（外部キーのCASCADEにより関連カテゴリ・ボードも一括削除）
     * @param service 削除対象のBbsServiceEntity
     */
    @Delete
    suspend fun deleteService(service: BbsServiceEntity)

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
