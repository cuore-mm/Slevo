package com.websarva.wings.android.bbsviewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.websarva.wings.android.bbsviewer.data.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.local.dao.CategoryDao
import com.websarva.wings.android.bbsviewer.data.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.local.entity.CategoryEntity

@Database(
    entities = [
        BookmarkThreadEntity::class,
        BbsServiceEntity::class,
        CategoryEntity::class,
        BoardEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkThreadDao(): BookmarkThreadDao
    abstract fun bbsServiceDao(): BbsServiceDao
    abstract fun categoryDao(): CategoryDao
}
