## Why

pending restore の marker は復元state machineのsource of truthだが、現在は`File.writeText()`で既存fileを直接truncateしている。書き込み途中でprocessが終了するとmarkerが空または不完全なJSONになり、次回起動でmarkerなしとして扱われてDB置換後のrollback/recoveryを実行できない。

## What Changes

- pending restore markerを一時的な書き込み状態からatomicにpublishし、更新失敗またはprocess終了時に直前の有効markerを保持する。
- markerの準備・状態遷移を行う全production pathを同じatomic read/write処理へ統一する。
- backup markerが残った状態でも読み取り時に直前の有効markerを回復する。
- atomic write失敗をcallerへ伝播し、成功していないstate transitionを成功扱いしない。
- crash相当の中断、通常更新、初回作成、cleanupをreal filesystem testsで検証する。
- result file、DataStore snapshot、rollback manifestのatomic化はこのchangeの対象外とする。

## Capabilities

### New Capabilities
- `pending-restore-marker-durability`: pending restore markerをatomicにpublishし、書き込み中断後も最後に確定した復元状態を読み取れることを定義する。

### Modified Capabilities

なし。

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreFileStore.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreManager.kt`
- pending restore markerを直接読み書きするproduction call site
- `PendingRestoreFileStoreTest.kt`、`PendingRestoreManagerPrepareTest.kt`などのmarker filesystem tests
- Android frameworkの`android.util.AtomicFile`を使用し、外部dependencyは追加しない。
