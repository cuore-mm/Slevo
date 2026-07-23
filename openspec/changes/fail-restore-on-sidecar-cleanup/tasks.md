## 1. Sidecar cleanup failure の伝播

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDbSwapper.kt` の `RealPendingRestoreDbSwapper` に KDoc 付き `internal` sidecar delete seam を追加し、production default が `File.delete()` の Boolean を返すことを確認する。
- [x] 1.2 同ファイルの `cleanWalShm()` を、存在する WAL/SHM の delete `false` と例外を識別可能な error として返す実装へ変更し、WAL 成功後の SHM 失敗も全体 failure になることを確認する。
- [x] 1.3 `replaceDbFile()` で cleanup error を temp copy/live DB delete/rename より前に返し、cleanup failure 後にこれらの操作へ到達しないことをコード順序で確認する。
- [x] 1.4 `restoreRollbackBackup()` で cleanup error を `false` として返し、cleanup failure 後に live main DB delete、`mainDbRestore`、`walRestore` へ到達しないことをコード順序で確認する。
- [x] 1.5 `cleanupCorruptFreshInstallDb()` の公開契約と既存 best-effort behavior を維持し、restore marker/state machine、sidecar 以外の delete 判定、schema、format、UI に変更がないことを diff で確認する。

## 2. Swapper unit test

- [x] 2.1 `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDbSwapperTest.kt` に `WAL/SHM × false/exception × replaceDbFile` の 4 組を追加し、各組で error、live/staged DB 内容不変、replacement temp 不在を検証する。SHM failure では先行 WAL 削除後も live DB が切り替わらないことを確認する。
- [x] 2.2 同 test file に `WAL/SHM × false/exception × restoreRollbackBackup` の 4 組を追加し、各組で戻り値 `false`、live DB 内容不変、rollback main/WAL/manifest 不変、`mainDbRestore` / `walRestore` 未呼び出しを検証する。
- [x] 2.3 上記 8 組で共通 assertion helper を使う場合も `replaceDbFile()` と `restoreRollbackBackup()` を各組から直接呼び、helper 単体 test だけで call-site gate を代替していないことを確認する。
- [x] 2.4 既存の置換・rollback 成功 test を必要最小限補強し、sidecar がない場合と削除成功時の現行 behavior が維持されることを検証する。

## 3. Recovery integration test

- [x] 3.1 `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplierTest.kt` で `replaceDbFileResult` を cleanup error に設定し、`replaceDbFile()` error branch が `rollbackAndFail()` を呼び、安全な rollback が完了しない場合に `ROLLBACK_REQUIRED` が書かれることを検証する。
- [x] 3.2 同 test file の rollback failure coverage を補強し、sidecar cleanup に相当する `restoreRollbackBackupResult = false` で `cleanupPending` が呼ばれず pending/rollback recovery state が残ることを検証する。
- [x] 3.3 同 test file で次回起動時の retry を検証し、cleanup/rollback が後に成功した場合だけ既存 terminal failure cleanup へ進むことを確認する。
- [x] 3.4 `PendingRestoreApplierDependencyTest.kt` と `PendingRestoreApplierExceptionTest.kt` を維持または必要最小限補強し、`PendingRestoreApplier` の Room 非依存と `SlevoApplication.onCreate()` の同期 cold-start ordering が崩れていないことを検証する。

## 4. Verification and audit

- [x] 4.1 変更 file の KDoc、非自明関数 comment、長い関数の section header が repository rule を満たすことを確認する。
- [x] 4.2 `ci-build` workflow に従って Android build と unit test を GitHub Actions で実行し、実装 commit と同一 HEAD の run が成功することを記録する。確認結果: Android CI Run ID `29640950728`、SHA `d23536fe92bda5e7606b7fef1108903820901a11` が成功。
- [x] 4.3 OpenSpec verification/conditional audit を実施し、failure invariant、artifact 保持、対象外の state/schema/format/UI とキュー済み finding に accidental scope がないことを確認する。確認結果: `openspec validate fail-restore-on-sidecar-cleanup --strict` 成功、差分は swapper/test/tasks の 4 file に限定。
