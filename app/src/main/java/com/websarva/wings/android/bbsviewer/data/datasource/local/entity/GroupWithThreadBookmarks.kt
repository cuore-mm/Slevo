package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class GroupWithThreadBookmarks(
    @Embedded val group: ThreadBookmarkGroupEntity,
    @Relation(
        parentColumn = "groupId", // ThreadBookmarkGroupEntityの主キー
        entityColumn = "groupId", // BookmarkThreadEntityの外部キー
        entity = BookmarkThreadEntity::class // 関連エンティティ
    )
    val threads: List<BookmarkThreadEntity>
)
