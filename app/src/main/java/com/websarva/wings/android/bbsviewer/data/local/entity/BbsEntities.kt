package com.websarva.wings.android.bbsviewer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ユーザーが登録した掲示板サービスを表すEntity
 * カテゴリごとのメニューを持つ場合はmenuUrlを、
 * 単一板の場合はboardUrlを使用する
 */
@Entity(tableName = "bbs_services")
data class BbsServiceEntity(
    @PrimaryKey val serviceId: String,
    val displayName: String,
    val menuUrl: String? = null,
    val boardUrl: String? = null
)

/**
 * サービスごとのカテゴリを永続化するEntity
 * BbsServiceEntity と外部キーで紐付け
 * 複合主キー: serviceId + name
 */
@Entity(
    tableName = "categories",
    primaryKeys = ["serviceId", "name"],
    foreignKeys = [ForeignKey(
        entity = BbsServiceEntity::class,
        parentColumns = ["serviceId"],
        childColumns = ["serviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("serviceId")]
)
data class CategoryEntity(
    val serviceId: String,
    val name: String
)

/**
 * カテゴリに属する板(ボード)を永続化するEntity
 * CategoryEntity と外部キーで紐付け
 */
@Entity(
    tableName = "boards",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["serviceId", "name"],
        childColumns = ["serviceId", "categoryName"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["serviceId", "categoryName"])]
)
data class BoardEntity(
    @PrimaryKey val url: String,
    val name: String,
    val serviceId: String,
    val categoryName: String
)
