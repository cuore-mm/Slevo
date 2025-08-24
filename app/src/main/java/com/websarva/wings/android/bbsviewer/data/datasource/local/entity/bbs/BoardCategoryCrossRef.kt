package com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

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
            onDelete = ForeignKey.Companion.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.Companion.CASCADE
        )
    ],
    indices = [Index("boardId"), Index("categoryId")]
)
data class BoardCategoryCrossRef(
    val boardId: Long,
    val categoryId: Long
)
