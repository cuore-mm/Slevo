## Why

復元 preview では ZIP 内の `database/slevo.db` を `zip.readBytes()` で丸ごと `ByteArray` に読み込み、その後に一時ファイルへ書き出して検証している。ユーザーの Room DB が大きい場合、復元前検証の時点で Android heap を圧迫し、`OutOfMemoryError` により有効なバックアップでも復元できない可能性がある。

## What Changes

- `BackupReader.readBackup()` は `database/slevo.db` entry を heap 上の `ByteArray` に保持せず、一時ファイルへ stream copy する。
- `manifest.json` と `datastore/*.json` は小さい metadata として引き続き memory 上で parse する。
- `BackupPreview` は DB 内容を `dbBytes: ByteArray` ではなく `dbFile: File` として表現する。
- `PendingRestoreManager.prepareRestore()` は `preview.dbFile` を pending restore directory の `database/slevo.db` へ move/copy する。
- `BackupRepositoryImpl.previewBackup()` / `restoreBackup()` は `BackupPreview.dbFile` の lifecycle を明確にし、処理完了後に一時ファイルを best-effort cleanup する。
- DB validation timing、manifest validation、DataStore JSON validation、commit 時再検証の requirement は維持する。
- **BREAKING** なし。公開ユーザー機能と ZIP format は変更しない。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `backup-restore`: 復元 preview / commit 時の `database/slevo.db` 読み込みは、DB 全体を heap に保持せず stream と一時ファイルで扱う requirement を追加する。

## Impact

- Affected code:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/restore/BackupReader.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/restore/BackupPreview.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreManager.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupRepositoryImpl.kt`
- Affected tests:
  - `BackupReaderTest`
  - `PendingRestoreManagerTest`
  - restore repository / ViewModel tests that construct or inspect `BackupPreview`
- APIs:
  - Internal model `BackupPreview` changes from `dbBytes: ByteArray` to `dbFile: File`.
  - `BackupRepository` public contract is unchanged.
- Dependencies / systems:
  - No new runtime dependency.
  - No ZIP format change.
  - No Room schema change.
