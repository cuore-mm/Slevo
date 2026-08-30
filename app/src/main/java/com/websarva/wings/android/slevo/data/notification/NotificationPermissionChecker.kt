package com.websarva.wings.android.slevo.data.notification

/**
 * 現在の端末状態でアプリ通知を表示できるかを判定する抽象。
 *
 * 設定画面の警告表示とAndroid通知Publisherが同じ通知可否判定を利用する。
 */
interface NotificationPermissionChecker {
    /** 現在のOS設定とruntime permissionから通知可否を返す。 */
    fun isNotificationAllowed(): Boolean
}
