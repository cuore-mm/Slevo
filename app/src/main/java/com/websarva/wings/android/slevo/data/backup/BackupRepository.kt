package com.websarva.wings.android.slevo.data.backup

import android.net.Uri
import com.websarva.wings.android.slevo.data.backup.export.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult

/**
 * バックアップ作成と復元の repository インターフェース。
 *
 * ViewModel は `exportBackup` / `previewBackup` / `restoreBackup` を呼び出すだけで
 * SAF の URI を介してバックアップ操作を実行できる。
 * repository 層で export と restore を共有 mutex で直列化する。
 */
interface BackupRepository {
    /**
     * SAF の保存先 [uri] へバックアップ ZIP を出力する。
     *
     * @param uri SAF の `CreateDocument` が返した保存先 URI。
     * @param includeCookies クッキー JSON を含めるか。
     * @return [com.websarva.wings.android.slevo.data.backup.export.BackupExportResult.Success] または [com.websarva.wings.android.slevo.data.backup.export.BackupExportResult.Failure]。
     */
    suspend fun exportBackup(uri: Uri, includeCookies: Boolean): BackupExportResult

    /**
     * SAF の [uri] からバックアップ ZIP を読み取り、検証して preview を返す。
     *
     * DB/DataStore へ書き込まない。UI での確認ダイアログ表示に使う。
     *
     * @param uri SAF の `OpenDocument` が返したバックアップ URI。
     * @return 検証成功時は [com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult.Success]、失敗時は [com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult.Invalid] または
     *   [com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult.Failure]。
     */
    suspend fun previewBackup(uri: Uri): BackupRestoreResult

    /**
     * SAF の [uri] からバックアップ ZIP を再読み込み・再検証し、pending restore を作成する。
     *
     * live DB と DataStore はこの時点で変更しない。次回起動時に [com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreApplier] が適用する。
     *
     * @param uri SAF の `OpenDocument` が返したバックアップ URI。
     * @param includeCookies Cookie を復元対象に含めるか。
     * @return [BackupRestoreResult.Success]、[BackupRestoreResult.Invalid]、または [BackupRestoreResult.Failure]。
     */
    suspend fun restoreBackup(uri: Uri, includeCookies: Boolean): BackupRestoreResult
}
