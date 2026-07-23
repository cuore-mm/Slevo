## Why

rollback snapshotが存在しないmigration recovery失敗時、`PendingRestoreApplier`はlive databaseを`pendingDir/quarantine`へ退避した直後に`cleanupPending()`で`pendingDir`全体を削除している。そのため結果にはquarantine pathが記録される一方、唯一の手動復旧artifactは消失しており、quarantineという安全策が機能していない。

## What Changes

- quarantineの保存先を一時的な`pending-restore` staging treeから分離し、application files配下の専用directoryで管理する。
- migration validation失敗、DB読取不能、unexpected schema version、rollback snapshot欠損の各failure pathで、DB本体と存在するsidecar filesをdurable quarantine artifactとして保持する。
- `cleanupPending()`、FAILED marker処理、cold start、次回restore準備によってquarantine artifactが暗黙に削除されないようlifecycleを定義する。
- failure resultが報告するquarantine pathと実在するartifactを一致させる。
- real filesystemを使った回帰testを追加し、pending cleanup後と再実行後にもartifactが残ることを検証する。

## Capabilities

### New Capabilities

- `quarantined-database-recovery-artifact`: rollback不能なrestore recovery失敗時に、live databaseをpending stagingから独立した手動復旧artifactとして保持し、その実在pathを報告する契約。

### Modified Capabilities

- なし。

## Impact

- `PendingRestoreFileStore.kt`: quarantine専用directoryのownershipとpathを追加する。
- `PendingRestoreApplier.kt`: `quarantineAndFail()`の保存先とfailure reportingを変更する。
- `PendingRestoreManager.kt`: FAILED pending cleanupおよび次回restore準備がquarantineを削除しないことを維持・検証する。
- `PendingRestoreApplierTest.kt`、`PendingRestoreFileStoreTest.kt`および必要なfilesystem integration test: artifact survival、sidecar preservation、reported path整合性を追加検証する。
- Room schema、backup archive format、marker/result JSON schema、UI、外部dependencyには変更を加えない。
