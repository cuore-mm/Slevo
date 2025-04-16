package com.websarva.wings.android.bbsviewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.websarva.wings.android.bbsviewer.data.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.local.entity.BookmarkThreadEntity

@Database(
    entities = [BookmarkThreadEntity::class],
    version = 1,
    exportSchema = false // スキーマのバージョン管理を自前で行う場合は false にする
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkThreadDao(): BookmarkThreadDao

    // 必要に応じて他のDaoもここで定義可能
}
