package com.websarva.wings.android.slevo.data.datasource.local.entity.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity

@Entity(
    tableName = "post_identity_histories",
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
        Index(value = ["boardId", "type", "value"], unique = true)
    ]
)
data class PostIdentityHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boardId: Long,
    val type: String,
    val value: String,
    @ColumnInfo(index = true)
    val lastUsedAt: Long
)

enum class PostIdentityType {
    NAME,
    EMAIL
}
