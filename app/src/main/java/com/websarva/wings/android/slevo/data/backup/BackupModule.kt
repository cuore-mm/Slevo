package com.websarva.wings.android.slevo.data.backup

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * バックアップ export 関連の Hilt bindings。
 *
 * `BackupRepositoryImpl`、`DatabaseBackupExporter`、`BackupOutputWriter` は
 * `@Singleton @Inject constructor()` により自動検出されるため個別の bind は不要。
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    /**
     * [BackupRepository] を [BackupRepositoryImpl] で提供する。
     */
    @Provides
    @Singleton
    fun provideBackupRepository(
        impl: BackupRepositoryImpl,
    ): BackupRepository = impl

    /**
     * [DatabaseConnection] を Room の [com.websarva.wings.android.slevo.data.datasource.local.AppDatabase] 経由で提供する。
     */
    @Provides
    @Singleton
    fun provideDatabaseConnection(
        impl: AppDatabaseConnection,
    ): DatabaseConnection = impl

    /**
     * [DatabasePathResolver] を Context 経由で提供する。
     */
    @Provides
    @Singleton
    fun provideDatabasePathResolver(
        impl: ContextDatabasePathResolver,
    ): DatabasePathResolver = impl
}
