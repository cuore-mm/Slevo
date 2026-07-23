## Context

pending restore の起動時適用は `PendingRestoreApplier` が state machine を orchestrate し、DataStore 反映は同ファイル内の `RealPendingRestoreDataStoreReflector.reflect()` と `PendingRestoreDataStoreWriter` が担当する。`prevent-partial-datastore-restore` では、settings/tabs/cookies JSON parse と Cookie pre-validation を DataStore write 前に完了するよう順序を変更したため、parse / validation failure による部分反映は防いでいる。

残っている問題は DataStore write phase の I/O failure である。現在の flow では `writer.writeSettings(settings)` が成功した後に `writer.writeTabs(tabs)` または `writer.writePreparedCookies(set)` が例外を投げると、`RealPendingRestoreDataStoreReflector.reflect()` は error string を返し、`PendingRestoreApplier.applyRestore()` は DB rollback / failure result へ進む。一方で先に成功した settings DataStore は backup 内容のまま残るため、restore 失敗後に DB と DataStore が異なる復元状態になる。

関連ファイル:

- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`
  - `RealPendingRestoreDataStoreReflector.reflect()`
  - `PendingRestoreApplier.applyRestore()`
  - `rollbackAndFail(...)`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreDataStoreWriter.kt`
  - `writeSettings(...)`
  - `writeTabs(...)`
  - `prepareCookies(...)`
  - `writePreparedCookies(...)`
- `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/impl/SlevoPreferenceDataStores.kt`
  - `settings(context)` / `tabs(context)` / `cookies(context)`
  - DataStore key 定義

現在の DataStore write 順序:

1. settings/tabs/cookies JSON を parse する。
2. Cookie を `prepareCookies()` で pre-validation する。
3. `writer.writeSettings(settings)` を呼ぶ。
4. `writer.writeTabs(tabs)` を呼ぶ。
5. Cookie が対象なら `writer.writePreparedCookies(preparedCookieSet)` を呼ぶ。

## Goals / Non-Goals

**Goals:**

- DataStore write phase で後続 write が失敗した場合、先行して write 済みの DataStore を restore 開始前の snapshot へ best-effort で戻す。
- settings / tabs / cookies のうち、実際に write 対象かつ write 済みの store のみ rollback 対象にする。
- `includeCookies=false` または `cookies.json` 不在の場合、cookies DataStore を snapshot / write / rollback 対象から除外する。
- rollback 失敗時も元の DataStore write failure を失わず、既存の DB rollback / failure marker flow に委譲する。
- DataStore file を直接操作せず、既存の `SlevoPreferenceDataStores` と AndroidX DataStore API のみを使う。
- unit test で「settings 成功後 tabs failure」「settings/tabs 成功後 cookies failure」「rollback failure は元 failure を維持」を検証する。

**Non-Goals:**

- AndroidX DataStore 間の完全な distributed transaction は実装しない。
- DataStore rollback が失敗した場合の完全復旧を保証しない。rollback は best-effort とし、復旧不能時は既存 failure flow と log に委ねる。
- DB swap / rollback、Room migration、marker/result file の state machine は変更しない。
- backup ZIP schema、DataStore JSON schema、`.preferences_pb` file format は変更しない。
- DataStore write を post-migration completion phase へ移動しない。

## Decisions

### Decision 1: DataStore write 前に対象 store の snapshot を取得する

`RealPendingRestoreDataStoreReflector.reflect()` は、parse と Cookie pre-validation が成功した後、DataStore write を開始する直前に snapshot を取得する。

推奨 API は `PendingRestoreDataStoreWriter` に追加する。

```kotlin
data class DataStoreSnapshot(
    val settings: Preferences,
    val tabs: Preferences,
    val cookies: Preferences?,
)

suspend fun snapshotDataStores(includeCookies: Boolean): DataStoreSnapshot
```

`includeCookies` は「restore marker で cookie restore が選択され、かつ staged `cookies.json` が存在して prepared cookie set がある」場合のみ `true` とする。`includeCookies=false` の場合は cookies DataStore を読まない。

実装では `SlevoPreferenceDataStores.settings(context).data.first()`、`tabs(context).data.first()`、必要時のみ `cookies(context).data.first()` を使う。`Preferences` は immutable snapshot として扱い、直接変更しない。

代替案:

- snapshot を `RealPendingRestoreDataStoreReflector` に直接実装する案。reflector の責務が DataStore provider access まで広がるため、既に DataStore write を持つ `PendingRestoreDataStoreWriter` 側に寄せる。
- cookies を常に snapshot する案。cookie restore 対象外でも cookies DataStore を読む必要がなく、I/O と test complexity を増やすため採用しない。

### Decision 2: 書き込み済み store を tracking し、失敗時にその store だけ戻す

`RealPendingRestoreDataStoreReflector.reflect()` は DataStore write phase で以下の boolean flag を管理する。

```kotlin
var settingsWritten = false
var tabsWritten = false
var cookiesWritten = false
```

各 write 成功後に対応 flag を `true` にする。例外が発生した場合は、snapshot と flag を使って write 済み store のみ rollback する。

推奨 API:

```kotlin
suspend fun restoreDataStores(
    snapshot: DataStoreSnapshot,
    restoreSettings: Boolean,
    restoreTabs: Boolean,
    restoreCookies: Boolean,
)
```

rollback は DataStore の `edit { prefs -> prefs.clear(); snapshot.forEach { prefs[it.key] = it.value } }` 相当で行う。実装時には `Preferences.asMap()` を使って key/value を戻す。型安全性のため unchecked cast が必要になる場合は private helper に閉じ込め、KDoc とコメントで「snapshot の key/value を同じ store に戻す」処理であることを説明する。

代替案:

- 失敗時に全 store を snapshot へ戻す案。write していない store にも不要な write が発生し、rollback failure の surface area が広がるため採用しない。
- rollback tracking を `PendingRestoreDataStoreWriter` 内に隠す案。settings/tabs/cookies の write 順序は reflector が持つため、reflector が flag を持つ方が実際の orchestration と一致する。

### Decision 3: rollback failure は元 failure を維持する

DataStore rollback 自体が失敗しても、元の write failure を隠してはならない。`RealPendingRestoreDataStoreReflector.reflect()` は以下の順序で処理する。

1. DataStore write phase の例外を catch する。
2. `writer.restoreDataStores(...)` を `try/catch` で best-effort 実行する。
3. rollback 失敗時は `Log.w` などで記録する。
4. return value は元の write failure に基づく `DataStore reflection failed: ...` を維持する。

`restoreDataStores(...)` は rollback failure を caller に投げてもよいが、reflector 側で必ず catch して元 failure を保持する。実装者は rollback failure を元 failure の `message` に連結しない。UI には詳細 error を出さない既存方針を維持する。

### Decision 4: pre-validation の順序は維持する

この change は `prevent-partial-datastore-restore` の成果を置き換えない。`RealPendingRestoreDataStoreReflector.reflect()` は引き続き以下を DataStore write 前に完了する。

1. settings JSON parse
2. tabs JSON parse
3. cookies JSON parse（対象時のみ）
4. `writer.prepareCookies(cookies)`（対象時のみ）
5. snapshot
6. write phase

snapshot は parse/pre-validation 成功後に取得する。parse/pre-validation failure では DataStore に触れないため、snapshot も rollback も不要である。

### Decision 5: DataStore provider と通常 DataSource の多重 instance 回避を維持する

snapshot / rollback は `SlevoPreferenceDataStores.settings(context)`、`tabs(context)`、`cookies(context)` を使う。`PreferenceDataStoreFactory.create(...)` を直接呼ばない。これにより、通常実行時 DataSource と pending restore writer が同じ process 内で同一 `.preferences_pb` 用 DataStore instance を共有する既存制約を維持する。

## Risks / Trade-offs

- [Risk] snapshot 取得後から rollback 完了までに別の runtime code が DataStore を更新すると、rollback がその更新を上書きする。  
  → Mitigation: pending restore は `SlevoApplication.onCreate()` の cold start 初期に実行され、Hilt/Repository/UI が通常利用を始める前のため競合 window は小さい。design と test では cold-start restore path を前提にする。

- [Risk] rollback write 自体が失敗すると DataStore は部分反映状態のまま残りうる。  
  → Mitigation: rollback は best-effort とし、失敗を log して元の failure を維持する。既存 DB rollback / failed marker flow は必ず進める。

- [Risk] `Preferences.asMap()` の key/value 復元で generic cast が必要になり、実装ミスが起きやすい。  
  → Mitigation: snapshot restore を private helper に閉じ込め、同一 store の snapshot を同一 store へ戻す用途に限定する。unit test で複数 key type（String/Boolean/Float/Int/StringSet）を含む rollback を検証する。

- [Risk] cookies の StringSet snapshot が大きい場合、read/write cost が増える。  
  → Mitigation: cookies は restore 対象かつ staged cookies が存在する場合のみ snapshot する。通常は cold start の一度きりであり許容範囲とする。

## Migration Plan

1. `PendingRestoreDataStoreWriter` に DataStore snapshot model と snapshot/restore helper を追加する。
2. `RealPendingRestoreDataStoreReflector.reflect()` の write phase を try/catch で囲み、write 済み flag を管理する。
3. write phase failure 時に write 済み DataStore を snapshot へ best-effort rollback する。
4. rollback failure は log し、元の `DataStore reflection failed: ...` を返す。
5. parse/pre-validation failure では snapshot/rollback が実行されないことを test で確認する。
6. GitHub Actions Android CI で unit test と build を確認する。

Rollback はこの change の code を戻すだけで可能。ただし rollback すると DataStore I/O failure 時に先行 write が残る Codex 指摘の atomicity issue が再発する。

## Implementation Contract

- `PendingRestoreApplier` の public constructor / interface は変更しない。
- `PendingRestoreDataStoreWriter` は `PreferenceDataStoreFactory.create(...)` を直接呼ばず、既存の `SlevoPreferenceDataStores` を使う。
- `RealPendingRestoreDataStoreReflector.reflect()` は parse/pre-validation 成功後、DataStore write 開始前に snapshot を取得する。
- `includeCookies=false` または staged `cookies.json` 不在の場合、cookies DataStore を snapshot/rollback しない。
- write 済み flag は write 成功後にのみ `true` にする。
- DataStore write failure 時は write 済み store のみ rollback する。
- rollback failure は元の write failure を置き換えない。
- `rollbackAndFail(...)` による DB rollback / marker/result flow は既存どおり維持する。
- 新規 class / non-trivial function には AGENTS.md の KDoc/doc comment rules に従ってコメントを付ける。

## Testing Strategy

- `PendingRestoreDataStoreWriterTest` または dedicated unit test
  - snapshot した Preferences を restore helper で戻すと、既存値が削除され snapshot の key/value だけになる。
  - String / Boolean / Float / Int / StringSet の key/value が戻せる。
- `PendingRestoreApplierTest`
  - settings write 成功後に tabs write が失敗した場合、settings rollback が呼ばれ、reflector は元 failure を返す。
  - settings/tabs write 成功後に cookies write が失敗した場合、settings/tabs rollback が呼ばれ、cookies rollback は cookies write 成功前なら呼ばれない。
  - cookies write 成功後に後続処理がない通常 success では rollback が呼ばれない。
  - rollback failure が発生しても reflector は元の write failure を返す。
  - parse/pre-validation failure では snapshot/rollback/write が呼ばれない。
- Verification
  - `openspec validate rollback-datastore-on-restore-write-failure --strict`
  - GitHub Actions Android CI。

## Open Questions

- なし。rollback は best-effort とし、完全 transaction は非目標とする。
