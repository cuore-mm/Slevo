package com.websarva.wings.android.bbsviewer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmark_threads")
data class BookmarkThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val threadUrl: String,
    val title: String,
    val boardName: String,
    val resCount: Int,
)
