package com.websarva.wings.android.bbsviewer.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bookmark.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.BoardCategoryCrossRefDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bookmark.BoardBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bookmark.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bbs.CategoryDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.bookmark.ThreadBookmarkGroupDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.OpenBoardTabDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.OpenThreadTabDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.history.ThreadHistoryDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.NgDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.cache.ThreadSummaryDao
    import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.cache.BoardVisitDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.cache.BoardFetchMetaDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.history.PostHistoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardCategoryCrossRef
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.CategoryEntity
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
