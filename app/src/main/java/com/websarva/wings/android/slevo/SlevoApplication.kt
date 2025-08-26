package com.websarva.wings.android.slevo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SlevoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // デバッグビルドの場合にのみ、ログを出力するDebugTreeを植える
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // リリースビルドの場合は何もplantしないので、ログは出力されない
        // ※クラッシュレポートツールと連携する際は、ここに設定を追加します
    }
}

