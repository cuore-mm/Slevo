package com.websarva.wings.android.bbsviewer.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardCategoryCrossRefDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadBookmarkGroupDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.OpenBoardTabDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.OpenThreadTabDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadHistoryDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.NgDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadSummaryDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardVisitDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardFetchMetaDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.PostHistoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardCategoryCrossRef
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.CategoryEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.ThreadBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.OpenBoardTabEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.history.ThreadHistoryEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.history.ThreadHistoryAccessEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.cache.ThreadSummaryEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.cache.BoardVisitEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.cache.BoardFetchMetaEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.history.PostHistoryEntity

@TypeConverters(NgTypeConverter::class)
@Database(
    entities = [
        BbsServiceEntity::class,
        CategoryEntity::class,
        BoardEntity::class,
        BoardCategoryCrossRef::class,
        BoardBookmarkGroupEntity::class,
        BookmarkBoardEntity::class,
        BookmarkThreadEntity::class,
        ThreadBookmarkGroupEntity::class,
        OpenBoardTabEntity::class,
        OpenThreadTabEntity::class,
        ThreadHistoryEntity::class,
        ThreadHistoryAccessEntity::class,
        NgEntity::class,
        ThreadSummaryEntity::class,
        BoardVisitEntity::class,
        BoardFetchMetaEntity::class,
        PostHistoryEntity::class
    ],
    version = 9,
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
    abstract fun openBoardTabDao(): OpenBoardTabDao
    abstract fun openThreadTabDao(): OpenThreadTabDao
    abstract fun threadHistoryDao(): ThreadHistoryDao
    abstract fun ngDao(): NgDao
    abstract fun threadSummaryDao(): ThreadSummaryDao
    abstract fun boardVisitDao(): BoardVisitDao
    abstract fun boardFetchMetaDao(): BoardFetchMetaDao
    abstract fun postHistoryDao(): PostHistoryDao
}
