package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "thread_summaries",
    primaryKeys = ["boardId", "threadId"],
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("boardId"),
        Index(value = ["boardId", "isArchived", "subjectRank"])
    ]
)
data class ThreadSummaryEntity(
    val boardId: Long,
    val threadId: String,
    val title: String,
    val resCount: Int,
    val firstSeenAt: Long,
    val isArchived: Boolean,
    val subjectRank: Int
)
