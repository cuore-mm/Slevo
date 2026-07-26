## Context

### 現在の挙動

`ui/tabs/BoardTabsCoordinator.kt` は `_openBoardTabs` を先に更新し、`TabsRepository.saveOpenBoardTabs` を retained scope から fire-and-forget で全件保存する。Room Flow は後から同じ一覧を上書きし、失敗を command caller へ返さない。`ui/tabs/store/TabSessionStore.kt` の `registerAndConfirmBoardRoute` は ensure 後に `boardPresentationState.first { Selected(target) }` を待つため、保存失敗時に terminal result がなく待ち続け得る。

`ui/tabs/ThreadTabsCoordinator.kt` は `canonicalTabs`、`pendingOperations`、`mutationIntents`、revision Flow、intent ごとの completion と cancellation phase を別々に保持する。FIFO worker は各 write 後に matching Room Flow を待ってから次 intent を処理するため、DB write 自体が独立でも一つの confirmation 停止が後続 command 全体を塞ぐ。pin path には commit と caller cancellation の競合を扱う専用 phase machinery がある。

共通 UI は既に `TabPresentationState` で Loading、Selected、PendingMissing、Empty を原子的に受け取る。DB schema、既存表示、retained close、metadata merge、selection repair、Room を canonical state とする契約は維持対象である。

### active change との関係

| Change | 関係 |
|---|---|
| `refactor-thread-tab-persistence-consistency` | loading、DB canonical、targeted mutation、pending projection、metadata、テスト要件を継承する。本変更が FIFO confirmation blocking と caller-owned cancellation phase を含む実装設計を supersede する。 |
| `fix-thread-deep-link-selection-consistency` | failure 時非遷移、registration 重複禁止、既存選択保持を継承する。本変更が presentation/canonical 観測による confirmation 設計を明示 command result に置換する。 |
| `fix-last-thread-tab-close-persistence` | retained close ownership と store teardown 境界をそのまま継承する。 |
| `fix-tab-list-close-persistence` | tab-list close が retained API へ委譲する要件をそのまま継承する。 |
| `unify-tab-missing-selection-behavior` | atomic presentation と selection resolution を継承する。presentation producer の所有者を各 domain Controller の単一 state reducer として明確化する。 |

上記 change は削除・archive せず、既存 spec と tests を履歴／回帰資産として残す。実装順の前提は本変更へ集約し、未完 task を先に実装する依存関係は設けない。

## Goals / Non-Goals

**Goals:**

- Board／Thread ごとに単一 logical state と reducer を持つ Controller を構築する。
- command の受理、targeted DB write、pending projection、canonical reconciliation、明示 result を一つの lifecycle contract にする。
- caller cancellation と accepted mutation ownership を分離し、Controller teardown だけを mutation cancellation 境界にする。
- matching canonical snapshot が遅延しても、独立な後続 DB command を effective state から処理できるようにする。
- Board Deep Link の永続化失敗を terminal failure にし、無期限の presentation 観測を除去する。
- 1,252 件以上でも通常 command が全件 DB replacement を行わず、projection／reconciliation が決定的に完了する構造にする。

**Non-Goals:**

- Board と Thread を一つの generic Controller class に統合しない。
- DB schema、entity column、backup/restore、route format を変更しない。
- 新しい UI text、icon、layout、theme、semantics、interaction、retry UI を追加しない。
- pending command を永続化して process death 後に再実行しない。
- Room Flow 自体の delivery timeout や強制 refresh を導入しない。

## Decisions

### 1. domain Controller と shared pure contract を分離する

既存 `BoardTabsCoordinator` と `ThreadTabsCoordinator` は移行中の互換名として維持できるが、最終責務は Board／Thread domain Controller とする。各 Controller は domain 固有 repository command、key、metadata merge、close 後 cleanup を所有する。共有するのは次だけとする。

- `Loading` / `Loaded` load phase。
- command id、accepted/committed/confirmed/failed の lifecycle 語彙。
- canonical tabs と pending operation から effective tabs を作る pure reducer primitive。
- `TabPresentationState` と selection repair primitive。
- `TabCommandResult` 相当の `Success`、`NoOp`、`Failure` terminal contract。

共有 primitive は `ui/tabs/` の既存 model／projection 定義を確認して重複型を作らず、domain metadata や repository を type parameter で巨大抽象化しない。

**代替案:** 一つの generic Controller は Board/Thread の metadata、session cleanup、repository transaction の差を callback 群へ隠し、状態遷移を読みにくくするため採用しない。

### 2. Controller は単一 immutable state を reducer event で更新する

各 Controller の mutable source は一つとし、少なくとも次を同じ immutable snapshot に含める。

```text
loadPhase: Loading | Loaded
canonicalTabs: ordered unique tabs
pendingCommands: acceptance order + lifecycle + operation payload
selectedKey: nullable stable key
presentation: TabPresentationState
commandResults: command id ごとの未配信／terminal result
```

Room snapshot、command accepted、repository returned、canonical matched、command failed、selection requested、controller closed を event とし、pure reducer が次 state と effect 指示を返す。repository I/O は effect runner が行い、I/O callback は event として Controller へ戻す。`open*Tabs`、selected key、presentation の互換 Flow はこの一つの state から派生させ、別の mutable source にしない。

Invariant は、Loaded の canonical key が一意、pending command id が一意、`Selected.key` が effective tabs に一意に存在、`Empty` は Loaded かつ effective tabs 0 件、`PendingMissing` は対応 pending cause が存在、terminal result は一度だけ、である。

### 3. command acceptance と result を明示する

public suspend command は Controller の scope が active で、load readiness と入力 validation を満たして queue/state に登録された時点で accepted となる。受理前の caller cancellation は command を登録しない。受理後は caller の await continuation と command execution を分離する。

Result は最低限次を区別する。

- `Success`: mutation が repository で成功し、必要な canonical snapshot と reconcile 済み。Deep Link はこの result のみで select/navigation を続ける。
- `NoOp`: 対象なし、既に同値など、repository が明示した非失敗の無変更。command ごとに navigation 可否を決め、ensure の既存対象は成功相当、欠落 target の select/delete は非成功相当とする。
- `Failure`: repository exception／明示 failure／Controller teardown。原因は既存 error path へ渡すが新規 UI 文言を追加しない。

caller cancellation は await と caller-owned navigation を停止するが、accepted command、pending projection、repository call、reconciliation を cancel しない。`TabSessionStore.close()` による retained scope teardown は未完 command を cancellation failure で終端し、effect runner と Room collector を止める。

### 4. DB write scheduling と canonical confirmation を分離する

accepted command は acceptance order で effective state (`canonicalTabs` に committed/accepted pending operation を順に適用した状態) から payload を導出する。各 domain Controller の DB effect は repository transaction の順序を決定的にするが、command N の matching Flow confirmation を command N+1 の DB write 開始条件にしない。

Repository success 後は pending lifecycle を `CommittedAwaitingCanonical` とし、matching Room snapshot まで projection に残す。後続 command はその projection を入力にするため、rapid pin toggle、ensure→pin、close→ensure が古い canonical 値から導出されない。Room snapshot ごとに全 pending を acceptance order で照合し、matching operation だけを confirmed にして terminal result を一度配信する。unrelated/stale snapshot は pending を除去しない。

同一 key の競合 command は acceptance order の projected predecessor を保持する。repository transaction が serial execution を必要とする既存 `DatabaseWriteGate` を迂回しない。Flow が停止しても後続 DB command は処理できるが、その result は各自の canonical match まで pending である。

**代替案:** 各 command を完全並列にすると同一 key の toggle/close 順が不定になる。全 command を confirmation まで直列化すると一つの Flow 停止が無関係な write を塞ぐ。いずれも採用しない。

### 5. repository は targeted suspend result を返す

`data/repository/TabsRepository.kt` と `data/datasource/local/dao/OpenBoardTabDao.kt` に Board の single-row ensure/upsert、delete、pin、metadata/scroll 対象更新を追加する。Thread の既存 targeted API も成功／no-op／failure を呼び出し元が判別できる結果へ揃える。通常操作は `saveOpenBoardTabs`、`replaceOpenThreadTabsForBulkOperation`、`upsertAll + deleteNotIn` を呼ばない。明示 bulk／restore 経路だけは既存全件 API を維持する。

各 repository command は `DatabaseWriteGate.withWritePermit` と必要な `AppDatabase.withTransaction` を一度通り、例外を fire-and-forget coroutine 内で失わない。schema migration は不要である。

### 6. metadata merge と selection repair を共有 pure rule にする

Thread の placeholder metadata、identity、単調増加 count、tab 固有 sort/pin/scroll 保持規則を repository、pending projection、canonical matcher で同じ pure functionから使う。Board も既存 scroll/pin/resolved metadata を targeted ensure で上書きしない。canonical snapshot 到着時に古い metadata へ一時的に戻さず pending merge を再適用する。

selection reducer は既存契約を維持する。有効 key は target を表示し、既知 pending absence は現在 page を保持し、confirmed invalid は selected close なら削除前 index の同位置／末尾、その他は先頭へ repair し、0 tabs は Empty とする。初回／restore の non-empty は同じ state emission で有効な Selected を出す。

### 7. `TabSessionStore` は facade と retained owner に限定する

`ui/tabs/store/TabSessionStore.kt` は Controller state Flow を公開し、command を委譲し、retained close を store scope から起動し、session holder を dispose する。list mutation、selection repair、command success の presentation 観測、repository 呼出し、独自 pending state を持たない。

`registerAndConfirmBoardRoute` と thread 相当 API は Controller の explicit result を await する。成功後の selection も Controller command とし、`Selected` Flow の `first` を confirmation として使わない。`DeepLinkHandler.kt` は failure/caller cancellation 時に navigation せず、既存選択を保持する。

### 8. 大量タブの計算量を制約する

通常 repository command は対象行 SQL と必要最小限の関連 state だけを読み書きし、tab count に比例する DB delete/upsert を禁止する。reducer は canonical snapshot 受信時の O(n + p) と command acceptance 時の O(n) 以下を上限とし、pending command ごとの nested full-list replay による O(n×p) を避ける。key index/map と一回の ordered fold を使い、安定順序と immutable snapshot を維持する。1,252 canonical tabs と少なくとも連続 100 command の deterministic test を timeout 依存なしで完走させる。

## Data Flow

```text
UI / DeepLinkHandler
        │ await command result（caller cancellation は await のみ）
        ▼
TabSessionStore（delegate / retained owner）
        ▼
BoardController または ThreadController
        │ command accepted event
        ├─ reducer ─> pending projection ─> atomic presentation
        ├─ effect runner ─> targeted repository command ─> explicit DB result
        │                                      │
        │                                      ▼
        └──────── Room Flow ─> canonical event ─> reconcile ─> terminal result
```

## Affected Areas

- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/BoardTabsCoordinator.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinator.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsProjection.kt` と既存 selection/presentation model（実装前に定義位置と call site を再確認する）
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/DeepLinkHandler.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/data/repository/TabsRepository.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/dao/OpenBoardTabDao.kt`
- 必要な Thread DAO／ThreadState update call site。schema／resource／manifest は変更しない。

## Error Cases and Recovery

- Board/Thread repository failure: pending の未commit operation を除去し、canonical + 残存 pending へ再計算し、`Failure` を一度返す。後続 command runner は継続する。
- commit 成功後の caller cancellation: result waiter/navigation は停止するが pending は canonical match まで残り、後続 command は committed projection から導出する。
- Room Flow 停止: committed command は pending のまま表示を保持する。後続 DB command は実行可能だが各 terminal success は matching canonical snapshot まで返さない。
- stale/unrelated Flow: operation-specific matcher に一致しない pending を除去しない。
- target no-op/missing: repository の明示結果を command-specific result に写し、全件保存や暗黙成功を行わない。
- Controller teardown: collector/effect runner を cancelし、未完 waiter を cancellation failure で完了し、追加 write を開始しない。既に commit 済み DB state は次回 Room load で復元する。
- reducer invariant violation: UI fallback で隠さず test/debug failure とし、production state は最後の整合 snapshot を破壊しない。

## Compatibility and Migration

DB migration はなく、旧版 rollback でも同じ schema を読める。internal source API は suspend result と state model の変更で非互換になるため、production/test call site を stage ごとに adapter 経由で切り替える。compatibility adapter は単一 Controller state から派生し、旧 fire-and-forget save や presentation confirmation を再導入しない。

## Testing Strategy

1. 既存テストを characterization として保持し、Board failure、accepted-command caller cancellation、non-blocking confirmation の不足ケースを先に追加する。
2. pure reducer test で Loading/empty、event ordering、pending fold、metadata merge、selection repair、terminal result 一回性を table-driven に検証する。
3. Board／Thread Controller test で controlled Room `MutableSharedFlow`、`StandardTestDispatcher`、`CompletableDeferred` repository barrier を使い、write success/failure/cancellation/teardown と stale/matching emission を決定的に進める。
4. 1,252 tabs + 100 rapid command で targeted repository call、key uniqueness、stable order、O(n×p) full replay 不在を call count／state assertion で確認する。wall-clock threshold は使わない。
5. `DeepLinkHandlerTest.kt` と real Controller orchestration test で Board failure が terminal、Thread/Board success の result→select→navigate 順、caller cancellation 非遷移を検証する。
6. Room instrumented test で Board/Thread single-row ensure/delete/pin/info/scroll が他の 1,252 rows と metadata/sort/pin/scroll を変えず、Flow が matching snapshot を emit することを確認する。
7. 既存 `BbsRouteScaffoldSelectionTest.kt`、`BbsRouteScaffoldTest.kt`、close ownership、rapid toggle、metadata、loaded-empty、restore tests を削除・弱化せず回帰確認する。

## Implementation Contract

1. application code より先に characterization tests を追加し、既存五 change の regression test 名と要件を削除しない。
2. shared pure reducer primitive と result/lifecycle contract を追加し、Board/Thread domain metadata と repository を一つの generic Controller へ入れない。
3. Board を先に移行する。targeted DAO/repository result、single state、pending projection、explicit Deep Link result を接続し、`saveOpenBoardTabs` の通常 call site と `boardPresentationState.first` confirmation を除去する。
4. Board failure test が有限に完了して navigation しないことを確認してから Thread state を同じ contract へ統合する。
5. Thread は effective state から command payload を導出し、write runner と canonical reconciliation を分離する。matching Flow 待ちを後続 DB command の barrier にしない。
6. retained close は accepted command を Controller が所有し、`TabSessionStore.requestCloseThreadTab` と tab-list callback の lifetime contract を維持する。
7. parity test 完了後にだけ `mutationIntents` の confirmation-blocking worker、`canonicalRevisionFlow` の command barrier、`PinWritePhase`、caller completion から operation を cancel する callback、重複 mutable tabs/selection source を削除する。
8. `TabSessionStore` から list mutation、repository call、presentation/canonical observation による command success 推論がゼロであることを検索と delegation test で確認する。
9. 通常 Board/Thread command から full replacement API が呼ばれないことを repository interaction test で固定する。
10. 新規 KDoc/comment は日本語とし、type／非自明関数／長い関数の section header について repository rule を満たす。
11. 実装後に `./gradlew testDebugUnitTest`、`./gradlew assembleDebug`、接続済み device で対象 Room/Compose instrumented tests を実行する。

## Risks / Trade-offs

- [confirmation 非blocking 化で複数 pending の照合が複雑になる] → command id、acceptance order、operation-specific matcher、pure ordered fold を一つの state/reducer で検証する。
- [caller cancellation 後も mutation が継続して意外に見える] → accepted 境界を API/test で固定し、navigation だけを caller ownership とする。teardown は明示的な唯一の cancellation 境界にする。
- [Board targeted mutation 追加で repository API が増える] → normal operation と bulk operation を名前／visibility／interaction test で分離する。
- [Flow が永続的に停止すると success waiter が残る] → pending presentation と後続 write を維持し、Activity-retained teardown で必ず終端する。製品 timeout/UI は承認範囲外のため追加しない。
- [大量 pending で projection cost が増える] → index/map と一回 fold、1,252+100 deterministic test で nested replay を防ぐ。
- [段階移行中に二重 writer が生じる] → domain ごとに切替境界を設け、同一 domain で旧 full-save と新 Controller mutation を併用しない。

## Migration Plan

1. Characterization: 既存 regression tests を固定し、Board persistence failure terminal、accepted cancellation、confirmation 非blocking、large-tab test を追加する。
2. Shared primitives: lifecycle/result、pure pending fold、selection/presentation reducer を導入する。
3. Board migration: targeted DAO/repository、Board Controller single state、store delegation、Deep Link explicit result を切り替える。
4. Thread consolidation: canonical/pending/selection/result を単一 state へ移し、effective state command derivation と非blocking reconciliation を切り替える。
5. Ownership parity: retained close、caller cancellation、teardown、metadata、restore、UI behavior の全 matrix を通す。
6. Deletion: parity 後に旧 full-save normal path、confirmation-blocking worker／revision barrier、`PinWritePhase`、重複 state／success inference を削除する。
7. Verification: unit/build/instrumented tests と通常経路の full-replacement call-site 検索を完了する。

Rollback は schema 変更がないため domain stage 単位の code revert とする。ただし同一 domain の新 Controller と旧 writer を混在させず、Board または Thread の切替を一体で戻す。既存 active OpenSpec change は rollback／archive 対象にしない。

## Open Questions

なし。新しい timeout、error text、retry interaction、pending 中の操作制限が必要になった場合は本変更を拡張せず、別途 product/UI 承認を得る。
