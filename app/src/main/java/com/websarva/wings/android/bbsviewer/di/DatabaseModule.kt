package com.websarva.wings.android.bbsviewer.di

import android.content.Context
import androidx.room.Room
import com.websarva.wings.android.bbsviewer.data.local.AppDatabase
import com.websarva.wings.android.bbsviewer.data.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.local.dao.CategoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder<AppDatabase>(
            context = context,
            klass = AppDatabase::class.java,
            name = "bbsviewer_database"
        )
            .fallbackToDestructiveMigration(false) // 必要に応じてマイグレーション戦略を指定
            .build()
    }

    @Provides
    fun provideBookmarkThreadDao(
        db: AppDatabase
    ): BookmarkThreadDao = db.bookmarkThreadDao()

    @Provides
    fun provideBbsServiceDao(
        db: AppDatabase
    ): BbsServiceDao = db.bbsServiceDao()

    @Provides
    fun provideCategoryDao(
        db: AppDatabase
    ): CategoryDao = db.categoryDao()
}
