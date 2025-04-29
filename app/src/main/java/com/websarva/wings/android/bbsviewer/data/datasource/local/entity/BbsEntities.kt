package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ユーザーが登録した掲示板サービスを表すEntity
 * カテゴリごとのメニューを持つ場合はmenuUrlを、
 */
@Entity(tableName = "bbs_services")
data class BbsServiceEntity(
    /** サービスを一意に識別するドメイン名（例: "5ch.net"） */
    @PrimaryKey val domain: String,
    /** UI 表示用のサービス名（省略時は domain を表示） */
    val displayName: String? = null,
    /** BBSMenu 取得用 URL（カテゴリ一覧を取得） */
    val menuUrl: String? = null,
)

/**
 * 指定サービス下のカテゴリ情報を保存するエンティティ。
 * 複合主キー: (domain, name)
 * サービス削除時には Cascade で関連カテゴリも削除される
 */
@Entity(
    tableName = "categories",
    primaryKeys = ["domain", "name"],
    foreignKeys = [ForeignKey(
        entity = BbsServiceEntity::class,
        parentColumns = ["domain"],
        childColumns = ["domain"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("domain")]
)
data class CategoryEntity(
    /** BbsServiceEntity.domain と同じ値 */
    val domain: String,
    /** カテゴリ名（例: "ニュース" や "趣味"） */
    val name: String
)

/**
 * カテゴリに属する掲示板（ボード）を保存するエンティティ。
 * 外部キー: (domain, categoryName) → CategoryEntity
 * キャッシュクリア時は関連ボードも Cascade 削除
 */
@Entity(
    tableName = "boards",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["domain", "name"],
        childColumns = ["domain", "categoryName"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["domain", "categoryName"])]
)
data class BoardEntity(
    /** 自動採番ID を主キーにする */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** スレッド一覧取得用 URL（例: "https://.../dat/1234567890.dat"） */
    val url: String,
    /** ボード名（例: "news4vip"） */
    val name: String,
    /** 所属サービスのドメイン（BbsServiceEntity.domain） */
    val domain: String,
    /** 所属カテゴリ名（CategoryEntity.name） 単一ボードサービスの場合は null */
    val categoryName: String? = null
)

