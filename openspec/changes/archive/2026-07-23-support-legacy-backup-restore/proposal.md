## Why

`add-backup-restore` の初期復元機能は現在の Room DB version と一致するバックアップだけを復元対象にするため、過去のアプリ version で作成したバックアップを新しいアプリへ戻せない。機種変更や長期間未更新端末からの移行では古い DB version のバックアップが現実的に発生するため、既存 Room migration を利用して現在 schema へ更新しながら復元できるようにする。

## What Changes

- 復元対象バックアップの `manifest.databaseVersion` を現在 version 完全一致ではなく、「対応最小 DB version 以上、現在 DB version 以下、かつ現在 version までの migration path が存在する」場合に許可する。
- 未来 version のバックアップは downgrade になるため引き続き拒否する。
- DB validation を復元前の pre-migration validation と、Room migration 後の post-migration validation に分ける。
- pre-migration validation では SQLite integrity、`PRAGMA user_version` の対応範囲、migration path を確認し、現在 schema の Room identity hash や全必須 table 一致は要求しない。
- 起動時 pending restore の state machine を、current version のバックアップも含めて DB 差し替え後から Room open 後の成功確認まで rollback 可能な状態を保持する設計へ拡張する。
- Room が通常起動時に既存 migration chain を実行し、成功後に pending restore を完了扱いとして cleanup できるようにする。
- Room open 後の post-migration validation 失敗時は live DB file を即時置換せず、次回 cold start で rollback するための `rollback-required` 状態として記録する。
- UI 表示、確認ダイアログ、成功通知の文言は変更せず、古い DB からの migration 有無は内部ログ/result file の詳細情報として扱う。
- 古い DB version、未来 DB version、migration path 不足、破損 DB、migration 失敗 recovery を検証するテストを追加する。

## Capabilities

### New Capabilities

- `legacy-backup-restore`: 古い Room DB version を含むバックアップ ZIP を、対応 migration path がある場合に現在 schema へ移行しながら復元する機能。

### Modified Capabilities

- なし

## Impact

- 影響範囲:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupReader.kt` の manifest/databaseVersion validation
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupDatabaseValidator.kt` の validation mode 分離
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/PendingRestoreManager.kt` の staging validation
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/PendingRestoreApplier.kt` の state machine と rollback/cleanup timing
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/PendingRestoreMarker.kt` の status/state 表現
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/AppDatabase.kt` と `app/src/main/java/com/websarva/wings/android/slevo/di/DatabaseModule.kt` の migration chain 定義・登録確認
  - `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/DatabaseCallback.kt` の post-DB-open startup hook への completion checker 統合
  - バックアップ復元 repository の validation/result mapping と内部ログ/result file の詳細情報
  - backup/restore 関連 unit tests と migration/recovery tests
- 既存 ZIP format version は `1` のまま扱う。バックアップ ZIP の entry 構造は変更しない。
- Room schema 自体はこの変更では追加・変更しない。
- 既存 UI、確認ダイアログ、Snackbar/成功通知文言は変更しない。
