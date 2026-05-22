package com.websarva.wings.android.slevo.di

import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.core.log.KermitAppLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt モジュール：ログ出力の実装を提供する。
 *
 * [AppLogger] の実装として [KermitAppLogger] を singleton として binding する。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {

    @Binds
    @Singleton
    abstract fun bindAppLogger(impl: KermitAppLogger): AppLogger
}
