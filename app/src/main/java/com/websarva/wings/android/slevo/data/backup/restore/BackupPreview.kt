package com.websarva.wings.android.slevo.data.backup.restore

import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson

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
 * @property dbBytes バックアップ内の DB ファイルのバイト列。pending restore 作成に使う。
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
    val dbBytes: ByteArray,
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
            dbBytes.contentEquals(other.dbBytes) &&
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
        result = 31 * result + dbBytes.contentHashCode()
        result = 31 * result + settingsJson.hashCode()
        result = 31 * result + tabsJson.hashCode()
        result = 31 * result + (cookiesJson?.hashCode() ?: 0)
        return result
    }
}
