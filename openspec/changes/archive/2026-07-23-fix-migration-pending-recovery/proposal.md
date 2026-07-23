## Why

古い databaseVersion のバックアップ復元では、`MIGRATION_PENDING` marker 書き込み後に Room migration が完了する前の crash/kill window がある。現在の復旧処理は次回起動時に migration 後を前提に strict validation を実行するため、有効な旧版 DB を誤って rollback/quarantine し、復元済みデータを失う可能性がある。

## What Changes

- `MIGRATION_PENDING` 復旧時に live DB の `PRAGMA user_version` を読み、Room migration 前後を判定する。
- live DB が `marker.databaseVersion` のままなら「Room migration 待ち」として扱い、軽量な pre-validation が成功した場合は marker を維持して Room と completion checker に処理を委ねる。
- live DB が `currentDbVersion` 以上なら「Room migration 済み」として扱い、既存の strict validation 後に `COMPLETED` へ進める。
- live DB の user_version が読めない、または marker / current のどちらとも一致しない場合は既存の rollback/quarantine 方針を維持する。
- **BREAKING** なし。バックアップ ZIP format、Room schema、ユーザー向け UI、DataStore format は変更しない。

## Capabilities

### New Capabilities

- `pending-restore-migration-recovery`: `MIGRATION_PENDING` の起動時復旧で、Room migration 前の有効な旧版 DB を誤 rollback せず安全に待機継続する動作を定義する。

### Modified Capabilities

なし。

## Impact

- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/restore/BackupDatabaseValidator.kt`
  - validator fake / test helper implementations
- Affected tests:
  - `PendingRestoreApplierTest`
  - `BackupDatabaseValidatorTest` または validator の既存 unit tests
  - dependency/source inspection tests if present
- APIs:
  - Internal interface `BackupDatabaseValidator` に `getUserVersion(dbFile: File): Int?` 相当の読み取り専用 helper を追加する想定。
  - Public backup/restore API は変更しない。
- Dependencies / systems:
  - No new runtime dependency.
  - No Room schema migration change.
