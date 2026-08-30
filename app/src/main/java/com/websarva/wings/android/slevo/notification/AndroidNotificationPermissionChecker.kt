package com.websarva.wings.android.slevo.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.websarva.wings.android.slevo.data.notification.NotificationPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Androidのruntime permissionとアプリ通知設定から通知可否を判定する実装。
 *
 * Android 13以上ではPOST_NOTIFICATIONSを追加確認し、全APIでアプリ通知全体の設定も確認する。
 */
@Singleton
class AndroidNotificationPermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPermissionChecker {
    /** 現在の端末状態で通知を表示できる場合だけtrueを返す。 */
    override fun isNotificationAllowed(): Boolean {
        // アプリ全体の通知設定は全APIで確認する。
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        // Android 13以降だけruntime permissionを追加確認する。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return true
    }
}
