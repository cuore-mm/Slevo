package com.websarva.wings.android.slevo.data.datasource.local.entity.bbs

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

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
