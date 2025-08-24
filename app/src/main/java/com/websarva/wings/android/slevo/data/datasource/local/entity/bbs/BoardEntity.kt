package com.websarva.wings.android.slevo.data.datasource.local.entity.bbs

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 板 (N:1 サービス)。カテゴリとは多対多。
 */
@Entity(
    tableName = "boards",
    foreignKeys = [
        ForeignKey(
            entity = BbsServiceEntity::class,
            parentColumns = ["serviceId"],
            childColumns = ["serviceId"],
            onDelete = ForeignKey.Companion.CASCADE
        )
    ],
    indices = [
        Index("serviceId"),
        Index(value = ["serviceId", "url"], unique = true)
    ]
)
data class BoardEntity(
    @PrimaryKey(autoGenerate = true) val boardId: Long = 0,
    val serviceId: Long,
    val url: String,
    val name: String
)
