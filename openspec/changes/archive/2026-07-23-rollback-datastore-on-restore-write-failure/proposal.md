## Why

Codex review により、pending restore の DataStore 反映で `writeSettings()` 成功後に `writeTabs()` または cookie write が I/O failure になると、DB は rollback される一方で先行 DataStore write が残る問題が指摘された。restore 失敗時に DB と DataStore の片方だけが backup 内容へ変わる状態を避けるため、DataStore write phase の失敗に対して best-effort rollback を計画する。

## What Changes

- pending restore の DataStore write 開始前に、対象 DataStore の現在値を snapshot する。
- settings / tabs / cookies の write 中に例外が発生した場合、write 済み DataStore を snapshot へ best-effort で戻す。
- rollback 失敗時は元の restore failure を維持し、rollback failure は log / diagnostic として扱う。
- `includeCookies=false` または `cookies.json` 不在の場合、cookies DataStore は snapshot / write / rollback 対象から除外する。
- 既存の DB rollback、marker/result、post-migration validation flow は維持する。
- **BREAKING**: なし。

## Capabilities

### New Capabilities
- `pending-restore-datastore-write-rollback`: pending restore の DataStore write phase における snapshot / rollback と failure handling を定義する。

### Modified Capabilities

## Impact

- 対象コード:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`
    - `RealPendingRestoreDataStoreReflector.reflect()`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDataStoreWriter.kt`
    - snapshot / restore helper の追加候補
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/impl/SlevoPreferenceDataStores.kt`
    - 既存 DataStore provider を利用し、直接 `.preferences_pb` を操作しない
- 対象テスト:
  - `PendingRestoreApplierTest.kt` または fake reflector/writer を用いた restore failure test
  - `PendingRestoreDataStoreWriterTest.kt` または dedicated unit test
- 依存関係追加なし。
- DataStore file format、backup ZIP schema、backup JSON schema は変更しない。
