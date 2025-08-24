package com.websarva.wings.android.bbsviewer.data.datasource.local.entity.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardEntity

@Entity(
    tableName = "board_visits",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BoardVisitEntity(
    @PrimaryKey val boardId: Long,
    val baselineAt: Long
)
