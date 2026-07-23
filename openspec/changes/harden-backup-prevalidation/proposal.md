## Why

バックアップ復元の pre-migration DB validation は現在、SQLite integrity、`PRAGMA user_version`、manifest version、migration path のみを確認しており、Slevo の DB schema であることを staging 前に確認していない。これにより、別アプリの SQLite file が `user_version` だけ一致する場合に pending restore として受け入れられ、Room open 前の schema validation crash や rollback 遅延を起こす可能性がある。

また、v1 DB は exported Room schema が残っておらず、事前 schema sanity check の source of truth を用意できない。ユーザー判断により v1 バックアップは復元対象外とし、検証可能な v2 以降だけを legacy restore 対象にする。

## What Changes

- **BREAKING**: backup restore の対応最小 DB version を v1 から v2 に引き上げる。
- `manifest.databaseVersion` または `database/slevo.db` の `PRAGMA user_version` が v2 未満のバックアップを、復元準備前に拒否する。
- `BackupDatabaseValidator.preValidate()` に version-aware な Slevo schema sanity check を追加し、対象 version に必要な application table が存在しない SQLite file を拒否する。
- 現在 version の DB は preValidate 段階で strict validation（現在 identity hash と現在 table set）を通す方針にする。
- 古い version の DB では現在 identity hash/current full table set は要求せず、historical version に対応する table set の存在確認に留める。
- v2-v9 の expected table set は exported Room schema を source of truth として管理する。

## Capabilities

### New Capabilities

- `backup-database-prevalidation`: バックアップ復元前に SQLite file が Slevo DB schema として妥当かを version-aware に検証する契約。

### Modified Capabilities

- なし

## Impact

- `AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION`
- `AppDatabase.hasMigrationPathForRestore()` と migration path consistency tests
- `BackupReader.validateManifest()` の too-old 判定（定数変更に追従）
- `RealBackupDatabaseValidator.preValidate()` の schema sanity check
- `BackupDatabaseValidatorTest`、`BackupReaderTest`、`PendingRestoreManagerTest`、`PendingRestoreApplierTest`、`AppDatabaseMigrationTest`
- OpenSpec `support-legacy-backup-restore` の requirement と設計方針を上書きする delta spec
