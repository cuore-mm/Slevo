package com.websarva.wings.android.slevo

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressInterceptor
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import com.websarva.wings.android.slevo.core.log.FileLogWriter
import com.websarva.wings.android.slevo.core.log.LogFileManager

/**
 * アプリ全体の初期化を担う Application 実装。
 *
 * Hilt 初期化、ログ設定、Coil のシングルトン ImageLoader 設定を行う。
 */
@HiltAndroidApp
class SlevoApplication : Application() {
    /**
     * アプリ起動時の基盤初期化を実行する。
     */
    override fun onCreate() {
        super.onCreate()

        // --- Image loader setup ---
        val imageProgressClient = OkHttpClient.Builder()
            .addNetworkInterceptor(ImageLoadProgressInterceptor())
            .build()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = { imageProgressClient }
                        )
                    )
                }
                .build()
        }

        // --- Logging setup ---
        val logFileManager = LogFileManager(this)
        val fileLogWriter = FileLogWriter(
            logFileManager = logFileManager,
            minSeverity = if (BuildConfig.DEBUG) Severity.Debug else Severity.Error
        )

        if (BuildConfig.DEBUG) {
            Logger.setLogWriters(platformLogWriter(), fileLogWriter)
        } else {
            Logger.setLogWriters(fileLogWriter)
        }

        // --- Crash handler setup ---
        val existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                fileLogWriter.log(
                    severity = Severity.Error,
                    message = "Uncaught exception on thread ${thread.name}",
                    tag = "CrashHandler",
                    throwable = throwable
                )
            } catch (_: Exception) {
                // クラッシュ記録失敗は握りつぶし、既存 handler 委譲を優先する
            }
            existingHandler?.uncaughtException(thread, throwable)
        }
    }
}
