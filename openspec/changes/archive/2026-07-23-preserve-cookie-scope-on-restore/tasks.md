## 1. 事前確認

- [x] 1.1 `BackupRestoreMapper.kt` の `toCookie(item: BackupCookieItem)` を確認する。完了条件: 現状が `item.hostOnly` を参照せず `Cookie.Builder.domain(item.domain)` のみを使っていることを確認する。
- [x] 1.2 `BackupMoshiFactory.kt` の `CookieJsonAdapter` を確認する。完了条件: 現状の pipe-delimited 形式が `name|value|expiresAt|domain|path|secure|httpOnly` の 7 field であることを確認する。
- [x] 1.3 既存 test file を確認する。完了条件: `BackupRestoreMapperTest.kt`、`BackupMoshiFactoryTest.kt`、pending restore/DataStore writer 周辺 test の追加先を決める。

## 2. Backup restore mapper 修正

- [x] 2.1 `BackupRestoreMapper.toCookie()` の builder 構築順を整理する。完了条件: `name`、`value`、`path`、`expiresAt` を設定した後、domain 設定を `hostOnly` 分岐で行える構造になっている。
- [x] 2.2 `item.hostOnly == true` の場合に `Cookie.Builder.hostOnlyDomain(item.domain)` を使う。完了条件: `BackupCookieItem.hostOnly=true` から生成した Cookie が `hostOnly == true` になる。
- [x] 2.3 `item.hostOnly == false` の場合に `Cookie.Builder.domain(item.domain)` を使う。完了条件: `BackupCookieItem.hostOnly=false` から生成した Cookie が `hostOnly == false` になる。
- [x] 2.4 invalid Cookie 値の扱いを維持する。完了条件: builder 例外時に `null` を返す既存挙動が変わらない。

## 3. CookieJsonAdapter format 修正

- [x] 3.1 `CookieJsonAdapter.toJson(cookie)` の出力を 8 field に拡張する。完了条件: 出力形式が `name|value|expiresAt|domain|path|secure|httpOnly|hostOnly` になる。
- [x] 3.2 `CookieJsonAdapter.fromJson(json)` で 8 field 形式を読み込む。完了条件: `parts[7].toBoolean()` を使い、`true` なら `hostOnlyDomain(parts[3])`、`false` なら `domain(parts[3])` で Cookie を復元する。
- [x] 3.3 `CookieJsonAdapter.fromJson(json)` で legacy 7 field 形式を読み込む。完了条件: `parts.size == 7` の場合は `hostOnly=false` として `domain(parts[3])` で復元する。
- [x] 3.4 不正形式の handling を明確化する。完了条件: field 数不足、`expiresAt` 数値変換失敗、builder 例外時に `null` を返す。
- [x] 3.5 `CookieJsonAdapter` の KDoc を更新する。完了条件: 新 8 field 形式と legacy 7 field 読み込み互換がコメントに記載されている。

## 4. Mapper / adapter unit test 更新

- [x] 4.1 `BackupRestoreMapperTest.kt` に host-only restore test を追加する。完了条件: `BackupCookieItem(hostOnly=true)` から復元した Cookie の `hostOnly`、`domain`、`path`、`expiresAt`、`secure`、`httpOnly` が期待値と一致する。
- [x] 4.2 `BackupRestoreMapperTest.kt` に domain-scoped restore test を追加する。完了条件: `BackupCookieItem(hostOnly=false)` から復元した Cookie の `hostOnly == false` を確認する。
- [x] 4.3 `BackupRestoreMapperTest.kt` または既存 mapper test に session/persistent test を追加する。完了条件: `expiresAt=Long.MAX_VALUE` は `persistent=false`、通常 expiry は `persistent=true` として復元される。
- [x] 4.4 `BackupMoshiFactoryTest.kt` に `hostOnly=true` round-trip test を追加する。完了条件: adapter serialize/deserialize 後も `hostOnly == true` である。
- [x] 4.5 `BackupMoshiFactoryTest.kt` に `hostOnly=false` round-trip test を追加する。完了条件: adapter serialize/deserialize 後も `hostOnly == false` である。
- [x] 4.6 `BackupMoshiFactoryTest.kt` に legacy 7 field 読み込み test を追加する。完了条件: 7 field 文字列から復元した Cookie が `hostOnly == false` になる。
- [x] 4.7 `BackupMoshiFactoryTest.kt` に不正形式 test を追加する。完了条件: field 数不足または `expiresAt` 不正の文字列で `fromJson()` が `null` を返す。

## 5. Pending restore / DataStore 経路 test 更新

- [x] 5.1 `PendingRestoreDataStoreWriter` または `PendingRestoreApplierTest` 周辺に host-only Cookie restore test を追加する。完了条件: backup の `cookies.json` に `hostOnly=true` を含め、DataStore 書き込み後に adapter で読み戻した Cookie が `hostOnly == true` になる。
- [x] 5.2 同じ経路で domain-scoped Cookie restore test を追加する。完了条件: backup の `cookies.json` に `hostOnly=false` を含め、読み戻した Cookie が `hostOnly == false` になる。
- [x] 5.3 backup export/import mapper の round-trip test を追加または更新する。完了条件: `Cookie -> BackupCookieItem -> Cookie` で `hostOnly` と `expiresAt` 由来の `persistent` が保持される。

## 6. 検証

- [x] 6.1 `openspec validate preserve-cookie-scope-on-restore --strict` を実行する。完了条件: OpenSpec strict validation が成功する。
- [x] 6.2 GitHub Actions Android CI を実行する。完了条件: unit test と build を含む Android CI が pass する。
- [x] 6.3 変更差分を確認する。完了条件: application code の変更範囲が `BackupRestoreMapper.kt`、`BackupMoshiFactory.kt`、関連 test に限定され、`BackupCookieItem` の JSON field 名が変わっていないことを確認する。
