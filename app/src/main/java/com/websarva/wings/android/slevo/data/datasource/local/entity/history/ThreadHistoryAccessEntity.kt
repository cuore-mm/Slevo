package com.websarva.wings.android.slevo.data.datasource.local.entity.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "thread_history_accesses",
    foreignKeys = [
        ForeignKey(
            entity = ThreadHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadHistoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadHistoryId")]
)
data class ThreadHistoryAccessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadHistoryId: Long,
    val accessedAt: Long
)
