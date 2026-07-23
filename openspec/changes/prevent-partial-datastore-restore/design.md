## Context

pending restore の起動時適用は `PendingRestoreApplier` が orchestrate し、DataStore 反映は `RealPendingRestoreDataStoreReflector.reflect()` と `PendingRestoreDataStoreWriter` が担当する。

現在の DataStore 反映順は以下。

1. `settings.json` を parse する。
2. `tabs.json` を parse する。
3. `writer.writeSettings(settings)` で settings DataStore を更新する。
4. `writer.writeTabs(tabs)` で tabs DataStore を更新する。
5. `includeCookies && cookies.json exists` の場合に `cookies.json` を parse する。
6. `writer.writeCookies(cookies)` で Cookie を OkHttp `Cookie` に変換し、Moshi adapter で pipe-delimited 文字列へ serialize し、cookies DataStore を更新する。

この順序では Cookie parse / conversion / serialization failure が発生した時点で settings/tabs はすでに永続化済みである。`PendingRestoreApplier.applyRestore()` は DB rollback を行うが、settings/tabs DataStore は rollback しないため、restore 失敗にもかかわらず DataStore の一部だけ backup 内容へ変わる。

関連ファイル:

- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`
  - `RealPendingRestoreDataStoreReflector.reflect()`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDataStoreWriter.kt`
  - `writeSettings`
  - `writeTabs`
  - `writeCookies`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/restore/BackupRestoreMapper.kt`
  - `toCookie`
  - `toCookies`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/BackupMoshiFactory.kt`
  - `CookieJsonAdapter`

## Goals / Non-Goals

**Goals:**

- Cookie parse / conversion / serialization failure の場合、settings/tabs/cookies DataStore を変更しない。
- Cookie 復元データの conversion / serialization を DataStore 書き込み前に全件検証する。
- 検証済み Cookie JSON set を cookies DataStore に書く commit path を明確に分離する。
- 既存の pending restore state machine、DB swap / rollback、marker/result file の flow は変更しない。
- JVM unit test で Cookie pre-validation failure と write order を検証できるようにする。

**Non-Goals:**

- settings/tabs/cookies の cross-DataStore transaction を実装しない。
- DataStore I/O failure 発生時の完全 rollback は対象外とする。
- Cookie の `persistent` semantics 修正は別 issue とし、本 change では扱わない。
- backup ZIP schema や JSON field name は変更しない。

## Decisions

### Decision 1: Cookie conversion/serialization を pure pre-validation として分離する

`PendingRestoreDataStoreWriter.writeCookies()` の内部にある以下の処理を DataStore write から分離する。

- `BackupRestoreMapper.toCookie(item)` による `BackupCookieItem -> Cookie` 変換。
- `moshi.adapter(Cookie::class.java).toJson(cookie)` による DataStore 保存文字列への serialize。
- conversion/serialization failure count の集計。

推奨 API:

```kotlin
sealed class PreparedCookies {
    data class Success(val cookieJsonSet: Set<String>) : PreparedCookies()
    data class Failure(val message: String) : PreparedCookies()
}

fun prepareCookies(cookies: BackupCookiesJson): PreparedCookies
```

実装時の命名は変更してよいが、DataStore に触れない pure/helper function とすること。1 件でも Cookie conversion/serialization に失敗した場合は `Failure("failed to serialize restored cookies: failed=N total=M")` と同等の error を返し、成功時のみ `Set<String>` を返す。

### Decision 2: 検証済み Cookie set を commit する API を追加する

Cookie DataStore への実際の書き込みは、pre-validation 済みの `Set<String>` を受け取る API にする。

推奨 API:

```kotlin
suspend fun writePreparedCookies(cookieJsonSet: Set<String>)
```

既存 `writeCookies(cookies: BackupCookiesJson): String?` は、互換 wrapper として以下の flow にしてよい。

1. `prepareCookies(cookies)`
2. failure なら error string を返す。
3. success なら `writePreparedCookies(set)` を呼ぶ。
4. DataStore I/O exception は従来どおり caller へ伝播させる。

### Decision 3: Reflector は全 JSON parse と Cookie pre-validation を DataStore write 前に行う

`RealPendingRestoreDataStoreReflector.reflect()` の順序を以下に変更する。

1. `settings.json` を parse。失敗なら error return。
2. `tabs.json` を parse。失敗なら error return。
3. `includeCookies && cookiesFile.exists()` の場合、`cookies.json` を parse。失敗なら error return。
4. Cookie がある場合、`writer.prepareCookies(cookies)` を実行。失敗なら error return。
5. ここまで成功して初めて `writer.writeSettings(settings)` を呼ぶ。
6. `writer.writeTabs(tabs)` を呼ぶ。
7. Cookie がある場合、pre-validation 済み set を `writer.writePreparedCookies(set)` で書く。

この順序により、Cookie parse / conversion / serialization failure では DataStore mutation が一切発生しない。

### Decision 4: I/O failure は既存 behavior を維持する

`DataStore.edit {}` の I/O failure は引き続き top-level catch で `DataStore reflection failed: ...` へ変換され、`PendingRestoreApplier` が DB rollback / failure result を行う。cross-DataStore rollback は追加しない。

理由:

- DataStore は store 単位の atomic edit は提供するが、settings/tabs/cookies 間の transaction は提供しない。
- snapshot/rollback を追加すると既存値の read / restore / failure handling が増え、起動時 restore path が複雑化する。
- Codex 指摘の主な failure は事前検出可能な Cookie parse / serialization failure であり、本 change はそこを低リスクに塞ぐ。

### Decision 5: テストは pure pre-validation と orchestration ordering を分ける

`PendingRestoreDataStoreWriterTest` では DataStore I/O を避け、`prepareCookies` の pure behavior を検証する。

`PendingRestoreApplierTest` または `PendingRestoreDataStoreWriterTest` に fake writer / fake reflector を使った ordering test を追加し、Cookie pre-validation failure 時に `writeSettings` / `writeTabs` / `writePreparedCookies` が呼ばれないことを確認する。

## Implementation Contract

実装担当者は以下を満たすこと。

1. `PendingRestoreApplier` の public class / constructor signature を変更しない。
2. `PendingRestoreDataStoreWriter` に Cookie pre-validation helper と prepared-cookie write API を追加する。
3. `RealPendingRestoreDataStoreReflector.reflect()` は DataStore write 前に settings/tabs/cookies parse と Cookie pre-validation をすべて完了する。
4. Cookie parse failure と Cookie pre-validation failure は settings/tabs/cookies DataStore を変更しない。
5. `writeSettings`、`writeTabs`、prepared cookie write の DataStore I/O exception は従来どおり caller へ伝播させる。
6. `writeCookies` を残す場合は wrapper として実装し、既存呼び出し元の behavior を壊さない。
7. 新規 type / helper / non-trivial function には AGENTS.md の KDoc/doc comment rules に従ってコメントを付ける。
8. backup JSON schema、Cookie JSON adapter format、`BackupCookieItem` field は変更しない。

## Risks / Trade-offs

- [Risk] Cookie pre-validation と writePreparedCookies の二段階化で既存 `writeCookies` test / call site とずれる。  
  → `writeCookies` を wrapper として残し、既存 behavior と error message を維持する。
- [Risk] DataStore I/O failure では依然として cross-store partial mutation が起こりうる。  
  → Non-Goal として明記し、今回の scope は事前検出可能な Cookie failure に限定する。
- [Risk] Cookie failure を早期 return することで result timing が変わる。  
  → 既存 restore state machine は維持し、reflector が error string を返す点は変えない。
- [Risk] invalid Cookie の作り方が OkHttp version に依存する。  
  → unit test では empty name / invalid path / bad domain など、OkHttp が安定して reject する値を使う。

## Migration Plan

1. `PendingRestoreDataStoreWriter` に Cookie pre-validation と prepared write API を追加する。
2. `writeCookies` を wrapper 化し、既存 call site 互換を維持する。
3. `RealPendingRestoreDataStoreReflector.reflect()` の処理順を parse/pre-validate first に変更する。
4. Cookie pre-validation tests と reflector ordering tests を追加する。
5. GitHub Actions Android CI で unit test と build を確認する。

Rollback は該当変更を戻すだけで可能。ただし rollback すると Cookie failure 時の settings/tabs partial restore 問題が再発する。

## Testing Strategy

- `PendingRestoreDataStoreWriterTest`
  - valid cookies は `Success(cookieJsonSet)` を返す。
  - empty list は empty set success を返す。
  - invalid cookie が 1 件でもある場合は `Failure` を返す。
  - valid/invalid mixed でも `Failure` を返し、partial success としない。
- `PendingRestoreApplierTest` または dedicated fake test
  - Cookie pre-validation failure 時に settings/tabs/cookies write が呼ばれない。
  - Cookie parse failure 時に settings/tabs/cookies write が呼ばれない。
  - Cookie pre-validation success 時は settings → tabs → prepared cookies の順で write される。
- `BackupRestoreMapperTest`
  - invalid cookie item で `toCookie()` が `null` を返すことを確認する。
- Verification
  - `openspec validate prevent-partial-datastore-restore --strict`
  - GitHub Actions Android CI。

## Open Questions

- なし。DataStore I/O failure の完全 rollback は本 change の scope 外とする。
