## 1. 事前確認

- [ ] 1.1 `PendingRestoreApplier.kt` の `RealPendingRestoreDataStoreReflector.reflect()` を読み、settings/tabs/cookies の現在の parse/write 順を確認する。
- [ ] 1.2 `PendingRestoreDataStoreWriter.kt` の `writeSettings`、`writeTabs`、`writeCookies` を読み、Cookie conversion / serialization / DataStore edit の責務を確認する。
- [ ] 1.3 `PendingRestoreDataStoreWriterTest.kt` と `PendingRestoreApplierTest.kt` を読み、既存 test が使う fake / helper の構造を確認する。

## 2. Cookie pre-validation API の追加

- [ ] 2.1 `PendingRestoreDataStoreWriter.kt` に Cookie pre-validation の result type を追加する。例: `sealed class PreparedCookies` または equivalent。KDoc を付ける。
- [ ] 2.2 `PendingRestoreDataStoreWriter.kt` に `prepareCookies(cookies: BackupCookiesJson)` 相当の helper を追加し、DataStore に触れず `Set<String>` または error message を返す。
- [ ] 2.3 `prepareCookies` 内で `BackupRestoreMapper.toCookie(item)` が `null` を返した場合に failure count を増やす。
- [ ] 2.4 `prepareCookies` 内で Moshi `Cookie` adapter の `toJson(cookie)` が例外を投げた場合に failure count を増やす。
- [ ] 2.5 failure count が 1 件以上ある場合は、既存 `writeCookies` と同等の error message (`failed to serialize restored cookies: failed=N total=M`) を返す。
- [ ] 2.6 Cookie list が空の場合は `Success(emptySet())` を返す。
- [ ] 2.7 新規 result type / helper / non-trivial function に AGENTS.md の KDoc/doc comment rules に従ったコメントを追加する。

## 3. Prepared cookie commit API の追加

- [ ] 3.1 `PendingRestoreDataStoreWriter.kt` に `writePreparedCookies(cookieJsonSet: Set<String>)` 相当の suspend function を追加し、cookies DataStore へ `COOKIE_KEY` を書き込む。
- [ ] 3.2 `writePreparedCookies` は conversion / serialization を行わず、受け取った set をそのまま DataStore に書く。
- [ ] 3.3 `writePreparedCookies` の DataStore I/O exception は catch せず caller へ伝播させる。
- [ ] 3.4 既存 `writeCookies(cookies: BackupCookiesJson): String?` は `prepareCookies` + `writePreparedCookies` の wrapper として残す。
- [ ] 3.5 `writeCookies` の既存 caller に対する戻り値 semantics (`null` 成功、string failure) を維持する。

## 4. Reflector の parse/pre-validation/write 順序を変更

- [ ] 4.1 `RealPendingRestoreDataStoreReflector.reflect()` で settings と tabs を parse しても、すぐには `writeSettings` / `writeTabs` を呼ばないようにする。
- [ ] 4.2 `includeCookies && cookiesFile.exists()` の場合は、DataStore write 前に `cookies.json` を parse する。parse 失敗時は settings/tabs/cookies DataStore を変更せず error return する。
- [ ] 4.3 Cookie parse 成功時は DataStore write 前に `writer.prepareCookies(cookies)` 相当を呼び、failure の場合は settings/tabs/cookies DataStore を変更せず error return する。
- [ ] 4.4 Cookie がない場合 (`includeCookies=false` または file absent) は prepared cookie set を `null` として扱う。
- [ ] 4.5 parse/pre-validation がすべて成功した後に `writer.writeSettings(settings)`、`writer.writeTabs(tabs)` の順で書く。
- [ ] 4.6 prepared cookie set が存在する場合のみ `writer.writePreparedCookies(set)` を呼ぶ。
- [ ] 4.7 `reflect()` の top-level exception handling (`DataStore reflection failed: ...`) は維持する。

## 5. Cookie pre-validation unit tests

- [ ] 5.1 `PendingRestoreDataStoreWriterTest.kt` に valid cookies で `prepareCookies` が success と non-empty set を返す test を追加する。
- [ ] 5.2 `PendingRestoreDataStoreWriterTest.kt` に empty cookie list で `prepareCookies` が success + empty set を返す test を追加する。
- [ ] 5.3 `PendingRestoreDataStoreWriterTest.kt` に invalid cookie 1 件で `prepareCookies` が failure を返す test を追加する。
- [ ] 5.4 `PendingRestoreDataStoreWriterTest.kt` に valid/invalid mixed で `prepareCookies` が failure を返し、partial success としない test を追加する。
- [ ] 5.5 `PendingRestoreDataStoreWriterTest.kt` に all invalid cookies で failure count と total count が error message に含まれる test を追加する。

## 6. Reflector ordering / no partial write tests

- [ ] 6.1 `PendingRestoreApplierTest.kt` または新規 pending restore test に fake writer/reflector を用意し、cookie parse failure で settings/tabs/cookies write が呼ばれないことを検証する。
- [ ] 6.2 Cookie pre-validation failure で settings/tabs/cookies write が呼ばれないことを検証する。
- [ ] 6.3 Cookie pre-validation success で write order が settings → tabs → prepared cookies になることを検証する。
- [ ] 6.4 `includeCookies=false` では Cookie parse/pre-validation/write を行わず settings → tabs のみ書くことを検証する。
- [ ] 6.5 `includeCookies=true` かつ cookies file absent では Cookie parse/pre-validation/write を行わず settings → tabs のみ書くことを検証する。

## 7. Mapper invalid cookie tests

- [ ] 7.1 `BackupRestoreMapperTest.kt` に empty name の `BackupCookieItem` で `toCookie()` が `null` を返す test を追加する。
- [ ] 7.2 `BackupRestoreMapperTest.kt` に invalid path (例: `/` で始まらない path) の `BackupCookieItem` で `toCookie()` が `null` を返す test を追加する。
- [ ] 7.3 `BackupRestoreMapperTest.kt` に invalid domain の `BackupCookieItem` で `toCookie()` が `null` を返す test を追加する。OkHttp の挙動に依存しすぎる場合は安定して reject される値を選ぶ。

## 8. 検証

- [ ] 8.1 `openspec validate prevent-partial-datastore-restore --strict` を実行し、validation が成功することを確認する。
- [ ] 8.2 GitHub Actions Android CI で unit test と build を実行し、追加した pending restore / mapper tests が成功することを確認する。
- [ ] 8.3 CI failure が出た場合は failure log を確認し、pre-validation ordering または test fixture を修正する。
