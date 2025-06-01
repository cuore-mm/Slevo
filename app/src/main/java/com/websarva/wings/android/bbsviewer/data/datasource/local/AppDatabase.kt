package com.websarva.wings.android.bbsviewer.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardCategoryCrossRefDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardCategoryCrossRef
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.CategoryEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadBookmarkGroupEntity

@Database(
    entities = [
        BbsServiceEntity::class,
        CategoryEntity::class,
        BoardEntity::class,
        BoardCategoryCrossRef::class,
        BoardBookmarkGroupEntity::class,
        BookmarkBoardEntity::class,
        BookmarkThreadEntity::class,
        ThreadBookmarkGroupEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bbsServiceDao(): BbsServiceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun boardDao(): BoardDao
    abstract fun boardCategoryCrossRefDao(): BoardCategoryCrossRefDao
    abstract fun bookmarkBoardDao(): BookmarkBoardDao
    abstract fun bookmarkThreadDao(): BookmarkThreadDao
    abstract fun boardGroupDao(): BoardBookmarkGroupDao
    abstract fun threadBookmarkGroupDao(): ThreadBookmarkGroupDao
}
