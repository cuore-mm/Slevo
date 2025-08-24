package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.bbsviewer.data.model.NgType

@Entity(
    tableName = "ng_entries",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("boardId")]
)
data class NgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val isRegex: Boolean,
    val boardId: Long?,
    val type: NgType,
)
