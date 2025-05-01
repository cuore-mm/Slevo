package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1-1. グループ定義テーブル
@Entity(tableName = "groups")
data class BoardGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val name: String,
    val colorHex: String    // 例: "#FF4081"
)
