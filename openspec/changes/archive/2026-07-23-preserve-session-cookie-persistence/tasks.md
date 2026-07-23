## 1. 事前確認

- [x] 1.1 `BackupRestoreMapper.kt` の `BackupRestoreMapper.toCookie()` を確認し、現在 `Cookie.Builder.expiresAt(item.expiresAt)` が無条件に呼ばれていることを把握する。
- [x] 1.2 `BackupMoshiFactory.kt` の `CookieJsonAdapter.toJson()` / `fromJson()` を確認し、現在の DataStore Cookie record が 8 field 形式で `persistent` を保存していないことを把握する。
- [x] 1.3 `PendingRestoreDataStoreWriter.kt` の `prepareCookies()` が `BackupRestoreMapper.toCookie()` と Cookie adapter serialize を通ることを確認し、追加の state machine 変更が不要であることを確認する。

## 2. BackupRestoreMapper の session cookie 復元

- [x] 2.1 `BackupRestoreMapper.toCookie()` で OkHttp `Cookie.Builder.expiresAt(item.expiresAt)` を `if (item.persistent) { ... }` の中だけで呼ぶように変更する。
- [x] 2.2 `item.persistent == false` の場合は `expiresAt()` を呼ばず、`name`、`value`、`domain`、`path`、`secure`、`httpOnly`、`hostOnly` の既存復元を維持する。
- [x] 2.3 `BackupRestoreMapper.toCookie()` の invalid Cookie handling が従来どおり `null` を返すことを確認する。

## 3. CookieJsonAdapter の 9 field format 対応

- [x] 3.1 `BackupMoshiFactory.CookieJsonAdapter.toJson(cookie)` の出力を `name|value|expiresAt|domain|path|secure|httpOnly|hostOnly|persistent` の 9 field に拡張する。
- [x] 3.2 `CookieJsonAdapter.fromJson(value)` で 9 field record の `parts[8]` を `persistent` として読み取り、`persistent == true` の場合のみ `expiresAt()` を呼ぶようにする。
- [x] 3.3 `CookieJsonAdapter.fromJson(value)` で legacy 8 field record を `hostOnly=parts[7].toBoolean()`、`persistent=true` として読み込む。
- [x] 3.4 `CookieJsonAdapter.fromJson(value)` で legacy 7 field record を `hostOnly=false`、`persistent=true` として読み込む。
- [x] 3.5 `field 数 < 7`、`expiresAt` parse 失敗、OkHttp builder reject の場合に従来どおり `null` を返すことを確認する。

## 4. Mapper unit test

- [x] 4.1 `BackupRestoreMapperTest.kt` に `BackupCookieItem(persistent=false)` を `toCookie()` した結果が `cookie.persistent == false` になる test を追加する。
- [x] 4.2 `BackupRestoreMapperTest.kt` に `BackupCookieItem(persistent=true, expiresAt=<finite>)` を `toCookie()` した結果が `cookie.persistent == true` かつ `expiresAt` 維持になる test を追加する。
- [x] 4.3 `BackupRestoreMapperTest.kt` に `persistent=false` かつ finite `expiresAt` の矛盾 item でも `cookie.persistent == false` を優先する test を追加する。
- [x] 4.4 既存の hostOnly/domain-scoped test が `persistent` 変更後も通ることを確認する。

## 5. CookieJsonAdapter unit test

- [x] 5.1 `BackupMoshiFactoryTest.kt` または既存 Cookie adapter test に、9 field session cookie round-trip で `cookie.persistent == false` を維持する test を追加する。
- [x] 5.2 9 field persistent cookie round-trip で `cookie.persistent == true` と finite `expiresAt` を維持する test を追加する。
- [x] 5.3 serialize 結果が 9 field を持ち、9 番目の field が `cookie.persistent` と一致する test を追加する。
- [x] 5.4 legacy 7 field record を deserialize すると `hostOnly == false` かつ `persistent == true` になる test を追加する。
- [x] 5.5 legacy 8 field record を deserialize すると `hostOnly` を維持し、`persistent == true` になる test を追加する。
- [x] 5.6 invalid record が `null` になる既存 test が維持されることを確認する。

## 6. Pending restore unit test

- [x] 6.1 `PendingRestoreDataStoreWriterTest.kt` に `persistent=false` の valid `BackupCookieItem` を `prepareCookies()` へ渡す test を追加し、result が success になることを確認する。
- [x] 6.2 6.1 の prepared Cookie record を Cookie adapter で deserialize し、`cookie.persistent == false` になることを確認する。
- [x] 6.3 `persistent=true` かつ finite `expiresAt` の valid `BackupCookieItem` を `prepareCookies()` へ渡し、prepared record を deserialize すると `persistent == true` と `expiresAt` 維持になることを確認する。
- [x] 6.4 invalid cookie pre-validation failure test が session cookie 対応後も失敗条件として維持されることを確認する。

## 7. ドキュメント・コメント確認

- [x] 7.1 新規 helper class/function を追加した場合は、repository の comment rule に従って KDoc を付ける。
- [x] 7.2 既存 function が 30 行を超える、または複雑化した場合は section comment を追加する。
- [x] 7.3 コメントは「何をするか / どう構成されるか」を説明し、行単位の繰り返し説明になっていないことを確認する。

## 8. 検証

- [x] 8.1 `openspec validate preserve-session-cookie-persistence --strict` を実行し、change spec が valid であることを確認する。
- [x] 8.2 変更後、GitHub Actions `Android CI` を branch `feature/add-backup` で実行し、unit test と build が成功することを確認する。
- [x] 8.3 CI failure が出た場合は failure log の test/file を確認し、実装または test を修正して再実行する。
