package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "open_board_tabs")
data class OpenBoardTabEntity(
    @PrimaryKey val boardUrl: String,
    val boardId: Long,
    val boardName: String,
    val serviceName: String,
    val sortOrder: Int,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)

@Entity(
    tableName = "open_thread_tabs",
    primaryKeys = ["threadKey", "boardUrl"]
)
data class OpenThreadTabEntity(
    val threadKey: String,
    val boardUrl: String,
    val boardId: Long,
    val boardName: String,
    val title: String,
    val resCount: Int = 0,
    val sortOrder: Int,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
)
