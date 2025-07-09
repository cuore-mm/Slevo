package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.websarva.wings.android.bbsviewer.data.model.Groupable

@Entity(tableName = "groups")
data class BoardBookmarkGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    override val name: String,
    override val colorName: String,
    override val sortOrder: Int
) : Groupable {
    override val id: Long
        get() = groupId // Groupableのidプロパティを実装
}

/**
 * グループと、そのグループに紐づく板一覧をまとめて取得するためのデータクラス
 */
data class GroupWithBoards(
    @Embedded val group: BoardBookmarkGroupEntity,
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
