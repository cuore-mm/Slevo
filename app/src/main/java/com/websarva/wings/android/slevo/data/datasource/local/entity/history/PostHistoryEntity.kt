package com.websarva.wings.android.slevo.data.datasource.local.entity.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity

@Entity(
    tableName = "post_histories",
    foreignKeys = [
        ForeignKey(
            entity = ThreadHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadHistoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("threadHistoryId"),
        Index("boardId")
    ]
)
data class PostHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val date: Long,
    val threadHistoryId: Long,
    val boardId: Long,
    val resNum: Int,
    val name: String,
    val email: String,
    val postId: String
)
