package com.websarva.wings.android.slevo.data.backup

import android.net.Uri

/**
 * バックアップ作成の repository インターフェース。
 *
 * ViewModel は `exportBackup` を呼び出すだけでバックアップ ZIP を SAF の保存先へ出力できる。
 * repository 層で同時実行を直列化する。
 */
interface BackupRepository {
    /**
     * SAF の保存先 [uri] へバックアップ ZIP を出力する。
     *
     * @param uri SAF の `CreateDocument` が返した保存先 URI。
     * @param includeCookies クッキー JSON を含めるか。
     * @return [BackupExportResult.Success] または [BackupExportResult.Failure]。
     */
    suspend fun exportBackup(uri: Uri, includeCookies: Boolean): BackupExportResult
}
