package com.websarva.wings.android.bbsviewer.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.websarva.wings.android.bbsviewer.data.model.Groupable

@Entity(tableName = "thread_bookmark_groups")
data class ThreadBookmarkGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    override val name: String,
    override val colorHex: String,
    override val sortOrder: Int
) : Groupable {
    override val id: Long
        get() = groupId // Groupableのidプロパティを実装
}
