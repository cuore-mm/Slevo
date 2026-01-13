package com.websarva.wings.android.slevo.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Custom Tabs を使ってURLを開く処理をまとめる。
 *
 * 外部アプリが見つからない場合は失敗扱いとする。
 */
object CustomTabsUtil {
    /**
     * 指定したURLをCustom Tabsで開く。
     *
     * @return 起動に成功した場合はtrue、失敗時はfalse。
     */
    fun openCustomTab(context: Context, url: String): Boolean {
        if (url.isBlank()) {
            // 空URLは処理しない。
            return false
        }
        return try {
            val intent = CustomTabsIntent.Builder().build()
            intent.launchUrl(context, url.toUri())
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
