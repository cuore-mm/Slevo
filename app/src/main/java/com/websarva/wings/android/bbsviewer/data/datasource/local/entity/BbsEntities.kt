package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * サービス (1:N カテゴリ, 1:N 板)
 */
@Entity(
    tableName = "services",
    indices = [Index(value = ["domain"], unique = true)]
)
data class BbsServiceEntity(
    @PrimaryKey(autoGenerate = true) val serviceId: Long = 0,
    val domain: String,
    val displayName: String? = null,
    val menuUrl: String? = null
)

/**
 * カテゴリ (N:1 サービス)
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = BbsServiceEntity::class,
            parentColumns = ["serviceId"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("serviceId"),
        Index(value = ["serviceId", "name"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val categoryId: Long = 0,
    val serviceId: Long,
    val name: String
)

/**
 * 板 (N:1 サービス)。カテゴリとは多対多。
 */
@Entity(
    tableName = "boards",
    foreignKeys = [
        ForeignKey(
            entity = BbsServiceEntity::class,
            parentColumns = ["serviceId"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("serviceId"),
        Index(value = ["serviceId", "url"], unique = true)
    ]
)
data class BoardEntity(
    @PrimaryKey(autoGenerate = true) val boardId: Long = 0,
    val serviceId: Long,
    val url: String,
    val name: String
)

/**
 * カテゴリと板の多対多リレーションを表す中間テーブル
 */
@Entity(
    tableName = "board_category_cross_ref",
    primaryKeys = ["boardId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("boardId"), Index("categoryId")]
)
data class BoardCategoryCrossRef(
    val boardId: Long,
    val categoryId: Long
)

/**
 * Category -> Boards リレーション
 */
data class CategoryWithBoards(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "boardId",
        associateBy = Junction(BoardCategoryCrossRef::class)
    )
    val boards: List<BoardEntity>
)

/**
 * Board -> Categories リレーション
 */
data class BoardWithCategories(
    @Embedded val board: BoardEntity,
    @Relation(
        parentColumn = "boardId",
        entityColumn = "categoryId",
        associateBy = Junction(BoardCategoryCrossRef::class)
    )
    val categories: List<CategoryEntity>
)
