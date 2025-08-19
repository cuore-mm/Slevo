package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "board_fetch_meta",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BoardFetchMetaEntity(
    @PrimaryKey val boardId: Long,
    val etag: String?,
    val lastModified: String?,
    val lastFetchedAt: Long?
)
