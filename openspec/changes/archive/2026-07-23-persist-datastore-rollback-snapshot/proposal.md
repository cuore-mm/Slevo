## Why

pending restoreはDB置換後にsettings、tabs、cookiesのDataStoreを順番に更新するが、rollback sourceはprocess memoryにしか保持されない。`DB_SWAPPED`以降でprocessが終了すると、次回起動ではDBだけがrollbackされ、部分反映されたDataStoreが残ってrestore前の一貫した状態へ戻れない。

## What Changes

- DB置換前にsettings、tabs、restore対象cookiesの現在値を型情報付きrollback snapshotとしてapp-private pending領域へ保存する。
- DataStore rollback snapshotをatomicにpublishし、未完成snapshotをrollback sourceとして使用しない。
- `ROLLBACK_READY`をDBとDataStoreの必要なrollback sourceが両方確定した状態として扱う。
- stale `DB_SWAPPED`、DataStore write failure、migration validation failure、`ROLLBACK_REQUIRED`など、DBをrollbackする全経路でDataStoreもrestore前snapshotへ戻す。
- DBまたはDataStoreのrollbackが未完了ならartifactを保持し、次回起動でrollbackを再試行する。
- fresh-install branchでも既存DataStoreを空と仮定せず、DB cleanupとDataStore rollbackを組み合わせる。
- rollback完了またはrestore成功が確定した後だけsnapshotをpending cleanupで削除する。
- marker/result JSON schema、backup archive format、DataStore `.preferences_pb` file形式は変更しない。

## Capabilities

### New Capabilities
- `durable-datastore-restore-rollback`: pending restoreのDataStore rollback sourceをprocess death後も保持し、DBとDataStoreを一体でrestore前状態へ戻すcontractを定義する。

### Modified Capabilities

なし。

## Impact

- `PendingRestoreApplier.kt`のstate transition、stale recovery、rollback orchestration
- `PendingRestoreDataStoreWriter.kt`と`PendingRestoreDataStoreReflector`のsnapshot/restore contract
- `PendingRestoreFileStore.kt`または専用snapshot storeによるatomic snapshot I/O
- pending restore filesystem layoutへDataStore rollback snapshotを追加
- applier、DataStore writer、file store、completion checker関連tests
- app-private storageとrestore時I/Oは増えるが、外部dependency、Room schema、backup archive schemaは変更しない。
