package com.websarva.wings.android.slevo

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressInterceptor
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import timber.log.Timber

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
        // デバッグビルドの場合にのみ、ログを出力するDebugTreeを植える
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // リリースビルドの場合は何もplantしないので、ログは出力されない
        // ※クラッシュレポートツールと連携する際は、ここに設定を追加します
    }
}
