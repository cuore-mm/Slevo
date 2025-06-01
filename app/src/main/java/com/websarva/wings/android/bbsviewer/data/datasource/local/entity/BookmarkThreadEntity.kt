package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "bookmark_threads",
    primaryKeys = ["threadKey", "boardUrl"],
    foreignKeys = [
        ForeignKey(
            entity = ThreadBookmarkGroupEntity::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE // グループ削除時に該当スレッドブックマークも削除
        )
    ],
    indices = [Index("groupId")]
)
data class BookmarkThreadEntity(
    val threadKey: String,
    val boardUrl: String,
    val boardId: Long,
    var groupId: Long, // グループID (お気に入りグループ)
    val title: String,
    val boardName: String,
    val resCount: Int
)
