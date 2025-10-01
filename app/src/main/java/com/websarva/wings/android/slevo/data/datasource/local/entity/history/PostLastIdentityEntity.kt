package com.websarva.wings.android.slevo.data.datasource.local.entity.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity

@Entity(
    tableName = "post_last_identities",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["boardId"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("boardId")
    ]
)
data class PostLastIdentityEntity(
    @PrimaryKey val boardId: Long,
    val name: String,
    val email: String,
    val updatedAt: Long,
)
