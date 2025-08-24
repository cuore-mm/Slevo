package com.websarva.wings.android.slevo.data.datasource.local.entity.bbs

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * カテゴリ (N:1 サービス)
 */
@Entity(
    tableName = "categories",
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
        Index(value = ["serviceId", "name"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val categoryId: Long = 0,
    val serviceId: Long,
    val name: String
)
