package com.websarva.wings.android.slevo.data.backup

import com.websarva.wings.android.slevo.data.backup.export.AppDatabaseConnection
import com.websarva.wings.android.slevo.data.backup.export.ContextDatabasePathResolver
import com.websarva.wings.android.slevo.data.backup.export.DatabaseConnection
import com.websarva.wings.android.slevo.data.backup.export.DatabasePathResolver
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.restore.CurrentDatabaseVersion
import com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * バックアップ export/restore 関連の Hilt bindings。
 *
 * `BackupRepositoryImpl`、`DatabaseBackupExporter`、`BackupOutputWriter`、
 * `RealBackupDatabaseValidator` は `@Singleton @Inject constructor()` により自動検出されるため
 * 個別の bind は不要。
 *
 * `Moshi` は [com.websarva.wings.android.slevo.di.NetworkModule] で提供されるため
 * ここでは重複定義しない。
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
     * [com.websarva.wings.android.slevo.data.backup.export.DatabaseConnection] を Room の [com.websarva.wings.android.slevo.data.datasource.local.AppDatabase] 経由で提供する。
     */
    @Provides
    @Singleton
    fun provideDatabaseConnection(
        impl: AppDatabaseConnection,
    ): DatabaseConnection = impl

    /**
     * [com.websarva.wings.android.slevo.data.backup.export.DatabasePathResolver] を Context 経由で提供する。
     */
    @Provides
    @Singleton
    fun provideDatabasePathResolver(
        impl: ContextDatabasePathResolver,
    ): DatabasePathResolver = impl

    /**
     * [com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator] を [com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator] で提供する。
     */
    @Provides
    @Singleton
    fun provideBackupDatabaseValidator(
        impl: RealBackupDatabaseValidator,
    ): BackupDatabaseValidator = impl

    /**
     * 現在の Room DB version を提供する。
     *
     * [com.websarva.wings.android.slevo.data.backup.restore.BackupReader] が manifest の databaseVersion と比較するために使う。
     */
    @Provides
    @CurrentDatabaseVersion
    fun provideCurrentDatabaseVersion(): Int = AppDatabase.CURRENT_DATABASE_VERSION
}
