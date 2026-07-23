package com.websarva.wings.android.slevo.data.backup.restore

import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import java.io.File

/**
 * 検証済みバックアップの preview 表示データ。
 *
 * [BackupReader] が ZIP の読み取りと検証を完了した後に生成され、
 * UI での確認ダイアログ表示と復元準備に使う。
 *
 * @property createdAt バックアップ作成日時 (ISO 8601)。
 * @property appVersionCode バックアップ作成元アプリの versionCode。
 * @property appVersionName バックアップ作成元アプリの versionName。
 * @property databaseVersion バックアップの Room DB version。
 * @property containsCookies バックアップに Cookie データが含まれているか。
 * @property dbFile 検証済みバックアップ DB を保持する一時ファイル。
 *   所有権は [BackupReader] から呼び出し側へ移り、呼び出し側が cleanup responsibility を持つ。
 * @property settingsJson 検証済みの DataStore settings JSON。
 * @property tabsJson 検証済みの DataStore tabs JSON。
 * @property cookiesJson 検証済みの DataStore cookies JSON。Cookie が含まれない場合は null。
 */
data class BackupPreview(
    val createdAt: String,
    val appVersionCode: Long,
    val appVersionName: String,
    val databaseVersion: Int,
    val containsCookies: Boolean,
    val dbFile: File,
    val settingsJson: BackupSettingsJson,
    val tabsJson: BackupTabsJson,
    val cookiesJson: BackupCookiesJson?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupPreview) return false
        return createdAt == other.createdAt &&
            appVersionCode == other.appVersionCode &&
            appVersionName == other.appVersionName &&
            databaseVersion == other.databaseVersion &&
            containsCookies == other.containsCookies &&
            dbFile == other.dbFile &&
            settingsJson == other.settingsJson &&
            tabsJson == other.tabsJson &&
            cookiesJson == other.cookiesJson
    }

    override fun hashCode(): Int {
        var result = createdAt.hashCode()
        result = 31 * result + appVersionCode.hashCode()
        result = 31 * result + appVersionName.hashCode()
        result = 31 * result + databaseVersion
        result = 31 * result + containsCookies.hashCode()
        result = 31 * result + dbFile.hashCode()
        result = 31 * result + settingsJson.hashCode()
        result = 31 * result + tabsJson.hashCode()
        result = 31 * result + (cookiesJson?.hashCode() ?: 0)
        return result
    }
}
