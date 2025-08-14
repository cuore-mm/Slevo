package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ng_ids",
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
data class NgIdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val isRegex: Boolean,
    val boardId: Long?
)
