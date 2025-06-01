package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "bookmark_boards",
    foreignKeys = [
        ForeignKey(
            entity        = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns  = ["boardId"],
            onDelete      = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity        = BoardBookmarkGroupEntity::class,
            parentColumns = ["groupId"],
            childColumns  = ["groupId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [ Index("groupId") ]
)
data class BookmarkBoardEntity(
    @PrimaryKey val boardId: Long,
    val groupId: Long
)

data class BoardWithBookmarkAndGroup(
    @Embedded val board: BoardEntity,
    @Relation(
        parentColumn  = "boardId",        // BoardEntity 側の boardId
        entityColumn  = "boardId",        // BookmarkBoardEntity 側の boardId
        entity        = BookmarkBoardEntity::class
    )
    val bookmarkWithGroup: BookmarkWithGroup?
)

data class BookmarkWithGroup(
    @Embedded val bookmark: BookmarkBoardEntity,
    @Relation(
        parentColumn  = "groupId",        // BookmarkBoardEntity 側の groupId
        entityColumn  = "groupId"         // BoardGroupEntity 側の groupId
    )
    val group: BoardBookmarkGroupEntity?
)
