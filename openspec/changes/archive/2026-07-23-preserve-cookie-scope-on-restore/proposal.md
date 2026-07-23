## Why

バックアップから Cookie を復元すると、OkHttp `Cookie` の scope 属性である `hostOnly` が失われ、host-only Cookie が domain-scoped Cookie として扱われる可能性がある。Cookie の送信対象 host が広がると、ログイン状態や投稿関連 Cookie が本来送信されない subdomain に送信されるため、復元後の挙動と安全性がバックアップ前と一致しない。

## What Changes

- `BackupRestoreMapper.toCookie()` で `BackupCookieItem.hostOnly` を反映し、host-only Cookie は `Cookie.Builder.hostOnlyDomain()`、domain-scoped Cookie は `Cookie.Builder.domain()` で復元する。
- DataStore に保存する OkHttp `Cookie` の pipe-delimited 形式を拡張し、`hostOnly` を保存・復元する。
- 既存ユーザーの DataStore に残る旧 7 field Cookie 形式は引き続き読み込めるようにし、旧形式では従来どおり domain-scoped Cookie として復元する。
- `persistent` は OkHttp の `expiresAt` 由来属性として扱い、`expiresAt` の round-trip により session/persistent の復元結果を保つ。
- Cookie scope/persistence の mapper、Moshi adapter、pending restore 経路に対する unit test を追加・更新する。

## Capabilities

### New Capabilities
- `cookie-scope-preservation`: バックアップ export/restore と Cookie DataStore persistence において、OkHttp Cookie の host-only/domain-scoped scope と session/persistent 状態を保つ要件を定義する。

### Modified Capabilities
- なし

## Impact

- 影響範囲:
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupRestoreMapper.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupMoshiFactory.kt`
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupDataMapper.kt`（仕様確認と round-trip test 対象）
  - `app/src/main/java/com/websarva/wings/android/slevo/data/backup/PendingRestoreDataStoreWriter.kt`（復元後 DataStore 書き込み経路の test 対象）
- 互換性:
  - 既存バックアップ JSON の `BackupCookieItem.hostOnly` field は維持する。
  - 既存 DataStore の旧 7 field Cookie 文字列は読み込み可能にする。
  - 新 DataStore 形式は末尾に `hostOnly` を追加するのみで、既存の `name|value|expiresAt|domain|path|secure|httpOnly` の順序は変えない。
- 外部 API / dependency 追加は不要。
