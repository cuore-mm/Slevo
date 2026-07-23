## Context

バックアップ機能は Cookie を `datastore/cookies.json` として export し、pending restore 経路で DataStore に反映する。現在の主な data flow は以下である。

```text
export:
CookieLocalDataSource.getCookies()
  -> BackupDataMapper.toBackupCookiesJson()
  -> datastore/cookies.json

restore:
BackupReader.readBackup()
  -> RealPendingRestoreDataStoreReflector.reflect()
  -> PendingRestoreDataStoreWriter.writeCookies()
  -> BackupRestoreMapper.toCookie()
  -> CookieJsonAdapter.toJson()
  -> DataStore

runtime persistence:
PersistentCookieJar.saveFromResponse()
  -> CookieLocalDataSourceImpl.saveCookies()
  -> CookieJsonAdapter.toJson()
  -> DataStore
```

現状の問題は 2 箇所にある。

1. `BackupRestoreMapper.toCookie()` は `BackupCookieItem.hostOnly` を読まず、常に `Cookie.Builder.domain(item.domain)` を使う。OkHttp では `domain()` が domain-scoped Cookie を生成するため、host-only Cookie が `hostOnly=false` に変わる。
2. `BackupMoshiFactory.CookieJsonAdapter` の pipe-delimited 形式は `"name|value|expiresAt|domain|path|secure|httpOnly"` の 7 field で、`hostOnly` を保存しない。`fromJson()` も常に `domain(parts[3])` を使うため、DataStore round-trip 後に `hostOnly=false` へ変わる。

`persistent` は OkHttp `Cookie` では独立 setter がなく、`expiresAt != Long.MAX_VALUE` から導出される。したがって復元時は `persistent` field を直接設定するのではなく、`expiresAt` を維持することで session/persistent 状態を維持する。

## Goals / Non-Goals

**Goals:**

- バックアップ restore 時に `BackupCookieItem.hostOnly` を OkHttp `Cookie` に正しく反映する。
- DataStore に保存される Cookie 文字列に `hostOnly` を含め、runtime persistence と restore 後 persistence の両方で Cookie scope を保つ。
- 既存 DataStore の 7 field 形式を後方互換で読み込めるようにする。
- `expiresAt` round-trip により session Cookie と persistent Cookie の状態を保つ。
- mapper、Moshi adapter、pending restore/DataStore 書き込み経路の unit test で再発を防ぐ。

**Non-Goals:**

- 既存 DataStore に保存済みの 7 field Cookie から本来の `hostOnly=true` を復元すること。旧形式には情報がないため、読み込み時は従来互換の `hostOnly=false` とする。
- backup JSON schema version を変更すること。`BackupCookieItem` には既に `hostOnly` と `persistent` が存在するため、JSON model は維持する。
- Cookie の暗号化、圧縮、domain 正規化仕様の変更。
- Cookie 保存 race condition（DataStore async save 前に backup が走る問題）の解決。

## Decisions

### Decision 1: `BackupRestoreMapper.toCookie()` で `hostOnlyDomain()` と `domain()` を使い分ける

- 対象: `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupRestoreMapper.kt`
- 関数: `BackupRestoreMapper.toCookie(item: BackupCookieItem)`
- 方針:
  - `Cookie.Builder()` に `name`、`value`、`path`、`expiresAt` を設定する。
  - `item.hostOnly == true` の場合は `builder.hostOnlyDomain(item.domain)` を呼ぶ。
  - `item.hostOnly == false` の場合は `builder.domain(item.domain)` を呼ぶ。
  - `secure` と `httpOnly` は現状どおり true の場合のみ setter を呼ぶ。
  - invalid domain/path/name 等で builder が例外を投げる場合は現状どおり `null` を返す。

代替案として `domain()` のみを使い続ける案は、host-only Cookie の scope を広げるため採用しない。`Cookie.Builder` には `hostOnly(Boolean)` のような汎用 setter がないため、OkHttp が提供する 2 つの domain setter を明示的に使い分ける。

### Decision 2: `CookieJsonAdapter` の format を末尾追加で 8 field に拡張する

- 対象: `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupMoshiFactory.kt`
- class: `CookieJsonAdapter`
- 新形式:

```text
name|value|expiresAt|domain|path|secure|httpOnly|hostOnly
```

- `toJson(cookie)` は末尾に `cookie.hostOnly` を追加する。
- `fromJson(json)` は以下の分岐にする。
  - `parts.size >= 8`: `parts[7].toBoolean()` を `hostOnly` として使う。
  - `parts.size == 7`: legacy 形式として `hostOnly=false` とする。
  - それ以外、または数値変換/Builder 例外時は `null` を返す。
- domain 設定は `hostOnly` に応じて `hostOnlyDomain(parts[3])` / `domain(parts[3])` を使い分ける。

末尾追加にする理由は、既存 7 field の field order を壊さず、旧形式判定を単純にするためである。旧形式から `hostOnly=true` は復元できないが、これは保存時に情報が存在しないため許容する。

### Decision 3: `persistent` は `expiresAt` の検証対象として扱う

- 対象:
  - `BackupDataMapper.toBackupCookiesJson()`
  - `BackupRestoreMapper.toCookie()`
  - `CookieJsonAdapter`
- 方針:
  - `BackupCookieItem.persistent` は backup JSON の表示/検証 metadata として維持する。
  - restore 時に `persistent` を直接設定しようとしない。
  - test では `expiresAt == Long.MAX_VALUE` の Cookie が session Cookie として戻ること、通常 expiry の Cookie が persistent Cookie として戻ることを確認する。

OkHttp の `Cookie.persistent` は `expiresAt` 由来であるため、`persistent` field と `expiresAt` が矛盾する手書き backup があっても、復元結果は `expiresAt` を source of truth とする。

## Risks / Trade-offs

- [Risk] 旧 DataStore 形式では本来の `hostOnly=true` を復元できない。  
  → [Mitigation] 旧形式は情報欠落済みのため `hostOnly=false` として後方互換を優先する。新形式で保存された Cookie からは正しく round-trip する。
- [Risk] `CookieJsonAdapter` の pipe-delimited 形式は cookie name/value に `|` が含まれる場合に壊れる既存制約がある。  
  → [Mitigation] 本変更では format の既存制約を拡大しない。scope 属性の保存に集中し、区切り文字問題は別 change の対象とする。
- [Risk] `hostOnlyDomain()` と `domain()` は invalid host を例外で拒否する。  
  → [Mitigation] 現状どおり mapper/adapter は例外を捕捉して `null` を返し、不正 Cookie を除外する。
- [Risk] 新形式 DataStore を旧アプリで読むと 8 field 文字列になる。  
  → [Mitigation] このブランチではアプリ内 downgrade はサポート対象外。field 追加は current app の forward path と既存 data の読み込み互換を優先する。

## Migration Plan

1. `BackupRestoreMapper.toCookie()` を `hostOnly` 分岐に変更する。
2. `CookieJsonAdapter.toJson()` を 8 field 出力に変更する。
3. `CookieJsonAdapter.fromJson()` を 7 field legacy と 8 field current の両方に対応させる。
4. 既存 unit test を更新し、legacy 7 field と new 8 field の両方を検証する。
5. CI で unit test と build を確認する。

Rollback は該当 commit の revert で可能。ただし revert 後に新形式 8 field の DataStore が残ると旧 adapter が想定しない field count になるため、実装時は `fromJson()` の 8 field 読み込み互換を残すことが望ましい。

## Implementation Contract

- application code の修正対象は以下に限定する。
  - `BackupRestoreMapper.kt`
  - `BackupMoshiFactory.kt`
  - 必要に応じて test files
- `BackupCookieItem` の field 名・JSON schema は変更しない。
- `BackupRestoreMapper.toCookie()` は `item.hostOnly` を必ず参照する。
- `CookieJsonAdapter.toJson()` は `cookie.hostOnly` を必ず保存する。
- `CookieJsonAdapter.fromJson()` は 7 field と 8 field の両方を受け付ける。
- 旧 7 field の `hostOnly` default は `false` とする。
- `persistent` を独自 boolean として保存先 Cookie に設定しようとしない。`expiresAt` を維持して OkHttp の `persistent` 派生値を使う。
- 実装後は少なくとも以下を実行する。
  - `openspec validate preserve-cookie-scope-on-restore --strict`
  - GitHub Actions Android CI（repo ルールに従い CI workflow を実行）

## Testing Strategy

- `BackupRestoreMapperTest`
  - `hostOnly=true` の `BackupCookieItem` から生成した Cookie が `hostOnly == true` であること。
  - `hostOnly=false` の `BackupCookieItem` から生成した Cookie が `hostOnly == false` であること。
  - `expiresAt=Long.MAX_VALUE` と通常 expiry の round-trip で `persistent` が期待どおりになること。
- `BackupMoshiFactoryTest`
  - `CookieJsonAdapter` が `hostOnly=true` Cookie を 8 field で保存し、復元後も `hostOnly == true` であること。
  - legacy 7 field 文字列を読み込み、`hostOnly == false` として復元すること。
  - invalid/短すぎる field count は `null` になること。
- `PendingRestoreApplierTest` または `PendingRestoreDataStoreWriter` 周辺 test
  - backup の `cookies.json` に `hostOnly=true` を含む Cookie を入れ、DataStore 書き込み後に adapter で読み戻した Cookie が `hostOnly == true` であること。

## Open Questions

- なし。`|` 区切り format の escape 問題は本変更の対象外とする。
