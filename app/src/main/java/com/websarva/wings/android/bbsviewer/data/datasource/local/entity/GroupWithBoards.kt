package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * グループと、そのグループに紐づく板一覧をまとめて取得するためのデータクラス
 */
data class GroupWithBoards(
    @Embedded val group: BoardGroupEntity,
    @Relation(
        parentColumn = "groupId",
        entityColumn = "boardId",
        associateBy = Junction(
            value = BookmarkBoardEntity::class,
            parentColumn = "groupId",
            entityColumn = "boardId"
        )
    )
    val boards: List<BoardEntity>
)
