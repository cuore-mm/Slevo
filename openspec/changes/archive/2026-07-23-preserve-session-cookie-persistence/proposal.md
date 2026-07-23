## Why

OkHttp `Cookie.Builder.expiresAt()` は呼び出した時点で Cookie を persistent 扱いにするため、`BackupCookieItem.persistent == false` の session cookie が復元時に persistent cookie へ変化している。Cookie の有効期限値だけでは session/persistent 状態を完全には表せないため、backup restore と DataStore cookie persistence の両方で `persistent` を明示的に扱う必要がある。

## What Changes

- `BackupRestoreMapper.toCookie()` は `BackupCookieItem.persistent` を参照し、`persistent == true` の場合のみ OkHttp `Cookie.Builder.expiresAt()` を呼ぶ。
- `persistent == false` の backup item は session cookie として復元し、OkHttp `Cookie.persistent == false` を維持する。
- Cookie DataStore 保存形式を `persistent` field 付きの新形式へ拡張し、新規 serialize では session/persistent 状態を明示的に保存する。
- Cookie DataStore 読み込みは既存の 7 field / 8 field 形式を引き続き読み込めるようにする。
- session cookie と persistent cookie の backup restore / DataStore round-trip test を追加する。
- **BREAKING**: なし。既存 DataStore record は読み込み互換を維持する。

## Capabilities

### New Capabilities
- `session-cookie-persistence`: backup restore と DataStore cookie persistence において、OkHttp Cookie の session/persistent 状態を保持する振る舞いを定義する。

### Modified Capabilities

## Impact

- 対象コード:
  - `BackupRestoreMapper.toCookie()`
  - `BackupMoshiFactory.CookieJsonAdapter`
  - pending restore の cookie pre-validation / DataStore 書き込み経路
- 対象テスト:
  - `BackupRestoreMapperTest`
  - `BackupMoshiFactoryTest` または Cookie adapter を検証する既存 test
  - `PendingRestoreDataStoreWriterTest`
- 依存関係追加なし。
- DataStore cookie record format は後方互換読み込みを維持しつつ、今後の書き込み形式のみ拡張する。
