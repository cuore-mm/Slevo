## Context

現在の backup restore では、`BackupCookieItem` が `expiresAt` と `persistent` の両方を保持している。しかし復元処理の `BackupRestoreMapper.toCookie()` は `persistent` を参照せず、OkHttp `Cookie.Builder.expiresAt(item.expiresAt)` を常に呼び出している。OkHttp 4.12.0 の `Cookie.Builder.expiresAt()` は呼び出し時に内部の `persistent` を `true` にするため、`BackupCookieItem.persistent == false` の session cookie も復元後は persistent cookie になる。

DataStore cookie persistence の `BackupMoshiFactory.CookieJsonAdapter` も同じ問題を持つ。現在の DataStore 保存形式は `name|value|expiresAt|domain|path|secure|httpOnly|hostOnly` の 8 field 形式で、`persistent` を保存していない。そのため新規に保存する Cookie record では session/persistent 状態を明示できず、deserialize 時に `expiresAt()` を呼ぶと session cookie が persistent cookie へ変化する。

関連する既存ファイル:

- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupRestoreMapper.kt`
  - `BackupRestoreMapper.toCookie(item: BackupCookieItem)`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupMoshiFactory.kt`
  - private `CookieJsonAdapter.toJson(cookie: Cookie)`
  - private `CookieJsonAdapter.fromJson(value: String)`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDataStoreWriter.kt`
  - `prepareCookies()` が `BackupRestoreMapper.toCookie()` と Cookie adapter serialize を通る
- 関連テスト:
  - `BackupRestoreMapperTest.kt`
  - `BackupMoshiFactoryTest.kt` または Cookie adapter round-trip test を置いている既存 test file
  - `PendingRestoreDataStoreWriterTest.kt`

## Goals / Non-Goals

**Goals:**

- `BackupCookieItem.persistent == false` の Cookie を OkHttp session cookie として復元する。
- `BackupCookieItem.persistent == true` の Cookie は `expiresAt` を維持した persistent cookie として復元する。
- DataStore cookie 保存形式に `persistent` field を追加し、新規書き込みでは session/persistent 状態を round-trip できるようにする。
- 既存の 7 field legacy format と 8 field hostOnly format は引き続き読み込めるようにする。
- pending restore の cookie pre-validation は、新しい session/persistent 復元ルールを通した上で成功/失敗を判定する。
- CI 上の unit test で session cookie と persistent cookie の両方を検証する。

**Non-Goals:**

- backup ZIP format version の変更は行わない。
- UI 文言、復元確認ダイアログ、Cookie 復元 checkbox の挙動は変更しない。
- OkHttp `Cookie` の内部仕様を置き換える独自 Cookie model は導入しない。
- 既存 DataStore record を起動時に一括 migration して書き換える処理は追加しない。
- legacy 7/8 field record から失われた session/persistent 情報を完全復元することは対象外とする。

## Decisions

### 1. `persistent` を復元時の正とする

`BackupRestoreMapper.toCookie()` は `BackupCookieItem.persistent` を正として扱う。`persistent == true` の場合のみ `Cookie.Builder.expiresAt(item.expiresAt)` を呼び、`persistent == false` の場合は `expiresAt()` を呼ばない。

理由:

- OkHttp `Cookie.Builder` は `expiresAt()` を呼ばない場合にのみ `persistent == false` の Cookie を作れる。
- `BackupCookieItem` には既に `persistent` field があり、backup JSON では session/persistent の意味情報が保持されている。
- `expiresAt` だけで session cookie を判定すると、OkHttp の default expiry 値や上限丸めに依存した fragile な実装になる。

実装 contract:

1. `BackupRestoreMapper.toCookie()` で builder を作る。
2. `name`、`value`、`path`、`secure`、`httpOnly`、domain scope は従来どおり設定する。
3. `if (item.persistent) builder.expiresAt(item.expiresAt)` とする。
4. `item.persistent == false` の場合、`item.expiresAt` がどの値でも `expiresAt()` は呼ばない。
5. OkHttp builder が reject した場合は従来どおり `null` を返す。

代替案:

- `item.expiresAt == Long.MAX_VALUE` の場合だけ session cookie とみなす案。これは `persistent` field を無視し、OkHttp の default 値や format 差異に依存するため採用しない。
- `persistent == false` かつ `expiresAt` が session default 以外の場合を validation error にする案。既存 backup data に矛盾がある場合の復元互換性を落とすため採用しない。復元では `persistent` を優先する。

### 2. DataStore cookie format を 9 field に拡張する

新規 serialize 形式は次の 9 field とする。

```text
name|value|expiresAt|domain|path|secure|httpOnly|hostOnly|persistent
```

`CookieJsonAdapter.toJson(cookie)` は `cookie.persistent` を 9 番目の field として出力する。`CookieJsonAdapter.fromJson(value)` は field 数で形式を判定する。

- 9 field 以上: `parts[8].toBoolean()` を `persistent` として使う。
- 8 field: `hostOnly = parts[7].toBoolean()`、`persistent` は legacy として既存挙動互換の値を使う。
- 7 field: `hostOnly = false`、`persistent` は legacy として既存挙動互換の値を使う。
- 7 field 未満、または `expiresAt` parse 失敗、OkHttp builder reject は `null` を返す。

legacy 7/8 field の `persistent` は既存挙動を維持するため `true` として扱い、`expiresAt()` を呼ぶ。legacy record には `persistent` が保存されていないため session cookie の完全復元はできない。新形式の record から正しく保持する。

代替案:

- legacy 7/8 field で `expiresAt` が OkHttp default maximum と一致する場合に `persistent=false` と推定する案。遠い将来期限の persistent cookie を session cookie と誤判定する可能性があるため採用しない。
- DataStore record の version prefix を導入する案。既存 pipe format の最小変更で済む 9 field 拡張の方が既存実装と test への影響が小さいため採用しない。

### 3. Cookie builder 生成ロジックは mapper と adapter で同じルールにする

`BackupRestoreMapper.toCookie()` と `CookieJsonAdapter.fromJson()` はどちらも以下の rule を守る。

- `persistent == true`: `expiresAt(expiresAt)` を呼ぶ。
- `persistent == false`: `expiresAt()` を呼ばない。
- `hostOnly == true`: `hostOnlyDomain(domain)` を呼ぶ。
- `hostOnly == false`: `domain(domain)` を呼ぶ。

実装時に共通 private helper を導入してもよいが、公開 API や package 構造を変更しない。helper を追加する場合は KDoc を付け、既存 comment/documentation rules に従う。

### 4. pending restore は既存 pre-validation flow に載せる

`PendingRestoreDataStoreWriter.prepareCookies()` は `BackupRestoreMapper.toCookie()` と Cookie adapter serialize を使っているため、mapper/adapter 修正後に session cookie も pre-validation 対象になる。新規の failure flow は追加しない。

検証観点:

- `persistent=false` の Cookie item を含む staged `cookies.json` が valid として prepare される。
- prepare 済み cookie set を adapter で読み戻すと `persistent=false` である。
- invalid path/domain など既存の invalid cookie failure は維持される。

## Risks / Trade-offs

- [Risk] 既存 7/8 field DataStore record の session/persistent 状態は完全復元できない。  
  → Mitigation: legacy format は従来互換として読み込み、9 field format 以降で明示保存する。spec と test に legacy 互換の期待値を明記する。

- [Risk] `persistent=false` かつ `expiresAt` が有限値の矛盾した backup item がある場合、`expiresAt` は OkHttp Cookie に反映されない。  
  → Mitigation: session/persistent の意味情報を優先する。test では `persistent=false` の場合 `cookie.persistent == false` を主張し、`expiresAt` は OkHttp default として扱う。

- [Risk] Cookie adapter の field 数拡張で既存 parsing test が壊れる。  
  → Mitigation: 7 field、8 field、9 field のそれぞれの test を追加し、既存 legacy expectation を明示する。

- [Risk] `toBoolean()` は `"true"` 以外を false とするため、不正 boolean string を明示 reject しない。  
  → Mitigation: 今回は既存 `hostOnly` parsing と同じ互換ルールを維持する。boolean strict validation は別 change とする。

## Migration Plan

1. `BackupRestoreMapper.toCookie()` を `persistent` 条件付き `expiresAt()` に変更する。
2. `CookieJsonAdapter.toJson()` を 9 field 出力へ変更する。
3. `CookieJsonAdapter.fromJson()` を 7/8/9 field 互換読み込みに変更する。
4. session/persistent round-trip test を追加する。
5. pending restore の prepared cookie test で session cookie が persistent 化しないことを確認する。
6. `openspec validate preserve-session-cookie-persistence --strict` と Android CI を実行する。

Rollback は code revert で可能。DataStore に 9 field record が書かれた後に古い app へ戻すと古い adapter が余剰 field を無視できる実装であれば読み込み可能だが、古い実装は `persistent` を保持できない。今回の実装では backward read compatibility を保証し、forward compatibility は保証対象外とする。

## Implementation Contract

- application code 変更時は `app/src/main/.../data/backup/BackupRestoreMapper.kt` と `app/src/main/.../data/backup/BackupMoshiFactory.kt` を最初に確認する。
- `persistent == false` の復元で `Cookie.Builder.expiresAt()` を呼ばないこと。
- `persistent == true` の復元で `Cookie.Builder.expiresAt(item.expiresAt)` を呼ぶこと。
- DataStore 新形式は 9 field で、field order は `name,value,expiresAt,domain,path,secure,httpOnly,hostOnly,persistent` とすること。
- legacy 7 field は `hostOnly=false` として読み込むこと。
- legacy 8 field は `hostOnly=parts[7].toBoolean()` として読み込むこと。
- legacy 7/8 field は `persistent=true` として従来挙動を維持すること。
- new 9 field は `persistent=parts[8].toBoolean()` を使い、`false` の場合は `expiresAt()` を呼ばないこと。
- 既存の invalid Cookie handling、`null` 返却、pending restore rollback/failure flow を変更しないこと。

## Testing Strategy

- `BackupRestoreMapperTest.kt`
  - `BackupCookieItem(persistent=false)` を `toCookie()` した結果が `cookie.persistent == false` になること。
  - `BackupCookieItem(persistent=true, expiresAt=<finite>)` を `toCookie()` した結果が `cookie.persistent == true` かつ `expiresAt` 維持になること。
  - `persistent=false` かつ finite `expiresAt` の item でも `cookie.persistent == false` を優先すること。
- `BackupMoshiFactoryTest.kt` または既存 Cookie adapter test
  - 9 field session cookie round-trip で `persistent == false` を維持すること。
  - 9 field persistent cookie round-trip で `persistent == true` と `expiresAt` を維持すること。
  - legacy 7 field は `hostOnly=false` / `persistent=true` として読めること。
  - legacy 8 field は `hostOnly` を維持し、`persistent=true` として読めること。
- `PendingRestoreDataStoreWriterTest.kt`
  - `prepareCookies()` が session cookie item を success として返し、prepared cookie record を adapter で戻すと `persistent == false` であること。
- CI:
  - repository policy に従い GitHub Actions `Android CI` を実行し、unit test と build が成功すること。

## Open Questions

- なし。legacy 7/8 field の session/persistent 完全復元は不可能なため、従来互換として `persistent=true` を採用する。
