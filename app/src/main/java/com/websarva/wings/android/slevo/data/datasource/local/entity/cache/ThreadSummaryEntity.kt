package com.websarva.wings.android.slevo.data.datasource.local.entity.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity

/**
 * 板一覧表示用の subject.txt キャッシュを保持する Room Entity。
 *
 * `thread_summaries` は最新 subject.txt に存在するスレッドのみを保持し、
 * `threadId` は板内キーとして `boardId` と組で一意になる。
 */
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
        Index(value = ["boardId", "subjectRank"])
    ]
)
data class ThreadSummaryEntity(
    val boardId: Long,
    val threadId: String,
    val title: String,
    val resCount: Int,
    val firstSeenAt: Long,
    val subjectRank: Int
)
