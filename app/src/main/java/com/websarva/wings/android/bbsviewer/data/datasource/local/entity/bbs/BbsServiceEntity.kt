package com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * サービス (1:N カテゴリ, 1:N 板)
 */
@Entity(
    tableName = "services",
    indices = [Index(value = ["domain"], unique = true)]
)
data class BbsServiceEntity(
    @PrimaryKey(autoGenerate = true) val serviceId: Long = 0,
    val domain: String,
    val displayName: String? = null,
    val menuUrl: String? = null
)
