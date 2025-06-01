package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ThreadBookmarkWithGroup(
    @Embedded val bookmark: BookmarkThreadEntity,
    @Relation(
        parentColumn = "groupId",
        entityColumn = "groupId",
        entity = ThreadBookmarkGroupEntity::class
    )
    val group: ThreadBookmarkGroupEntity?
)
