## Context

### 現在の挙動

`ThreadTabsCoordinator` は `_openThreadTabs = emptyList()` と `_threadLoaded = false` で開始し、`bind()` が `TabsRepository.observeOpenThreadTabs()` の結果を受けるたびに `_openThreadTabs` を無条件に置換する。一方、`ensureThreadTab`、`closeThreadTab`、`togglePinThreadTab`、`updateThreadResolvedBoardInfo` は同じ `_openThreadTabs` を先に更新し、`scope.launch` した `saveOpenThreadTabs` で一覧全体を保存する。したがってメモリ mutation と Room Flow が同じ一覧の順不同な書き手になっている。

`TabsRepository.saveOpenThreadTabs` は `DatabaseWriteGate.withWritePermit` と `AppDatabase.withTransaction` の内側で `upsertAll` と `deleteNotIn` を行う。これは個々の DB transaction を保護するが、coordinator のメモリ mutation、複数の fire-and-forget save、Flow reconciliation の順序は保護しない。初回 Flow が遅い状態で 1,252 件の保存済みタブへ新規タブを追加すると、空または古い一覧から作った full save が既存行を削除し得る。

### 制約

- `open_thread_tabs` と関連 `thread_states` / `thread_histories` の既存 schema および保存済みデータを維持する。
- DB を永続スレッドタブ一覧の正本とする。
- `DatabaseWriteGate` の全 Room write 保護、backup export の `withWritesSuspended`、Room transaction を維持し、二重 gate を導入しない。
- UI の見た目、文言、アイコン、theme、accessibility semantics は変更しない。
- 後続変更 `fix-thread-deep-link-selection-consistency` が readiness と mutation completion を待機できる API を先に提供する。

## Goals / Non-Goals

**Goals:**

- 初回 Room Flow 前の未初期化状態と、初回 emission 後の空一覧を型または一つの明示的 state で区別する。
- 通常の add/delete/pin/thread-info mutation を対象行単位の DB operation にする。
- coordinator が mutation intent を受付順に直列実行し、呼出元へ canonical confirmation 後の成功または失敗を返す。
- Room Flow の snapshot と未完了 operation の projection を分離し、古い emission が pending operation の表示結果を消さないようにする。
- 大量データと emission/write 順序を制御する決定的テストを可能にする。

**Non-Goals:**

- 板タブの persistence architecture の同時変更。
- DB schema version、entity column、既存データ形式の変更。
- backup/restore の DB file swap や DataStore restore の変更。
- deep-link の選択、pager、navigation の変更。これらは後続変更で行う。
- 新しい UI、エラー文言、retry button、dialog、snackbar の追加。

## Decisions

### 1. canonical snapshot と loading state を一つの状態モデルにする

`ui/tabs/coordinator/ThreadTabsCoordinator.kt` が公開するスレッドタブ状態を、少なくとも `Loading` と `Loaded(tabs)` を持つ明示的 state にする。`Loaded(emptyList())` は正規の読込済み空状態であり、`Loading` と同一視しない。既存の `openThreadTabs` / `threadLoaded` 利用箇所を一度に移行できない場合も、内部正本は一つの state とし、互換 accessor はそこから導出する。

Room collector だけが canonical snapshot を更新する。`bind()` の初回 emission で `Loaded` へ遷移し、それ以前に届いた mutation intent は DB を触らず readiness を待つ。

**代替案:** `emptyList + Boolean` を別々に維持すると、不可能な組合せと更新順の差を残すため採用しない。

### 2. 通常操作は対象タブ単位の repository/DAO API にする

`data/datasource/local/dao/OpenThreadTabDao.kt` と `data/repository/TabsRepository.kt` に、実装時に次の責務を持つ suspend API を設ける。

- ensure/add: 対象 `threadId` の既存 tab 固有値を壊さず、未登録時だけ `MAX(sortOrder) + 1` で 1 行を追加する。同じ transaction で必要な共通 `ThreadState` を保存する。
- delete: 対象 `threadId` の `open_thread_tabs` 1 行だけを削除する。共通 ThreadState の既存遅延 GC 契約は維持する。
- pin: 対象 `threadId` の pin 列だけを transaction 内で更新し、確定した値を返す。UI snapshot を基準に全件保存しない。
- thread info: `ThreadTabCoordinator.updateThreadTabInfo` と `ThreadTabsCoordinator.updateThreadResolvedBoardInfo` は open-tab 一覧を再保存せず、対象の共通 ThreadState 更新 API を使う。
- scroll: 既存 `updateThreadTabScrollPosition` の対象行 update を維持する。

各 repository mutation は `DatabaseWriteGate.withWritePermit { db.withTransaction { ... } }` を一度だけ使用し、成功、対象なし/no-op、失敗を呼出元が判定できる結果を返す。DAO の SQL 追加だけで実現し、schema migration は行わない。

既存タブの ensure は、同じ transaction で既存の DB canonical `ThreadState` を取得してから、次のフィールド単位規則で incoming `ThreadTabInfo` をマージする。pending projection と scope 未 bind の test seam も同じ規則を使用し、DB confirmation 前後で placeholder 値を表示しない。

- `threadId` / `threadKey`: 対象行の identity として変更しない。
- `boardId`: incoming が `0L` なら未解決 placeholder として既存値を保持し、非 `0L` の場合だけ incoming を採用する。
- `title`: 空文字、または対象 `threadId` の host/board/threadKey から `buildInitialThreadTitle` と同じ形式で生成される thread URL は初期 placeholder とする。incoming が placeholder かつ既存 title が非 placeholder の場合だけ既存値を保持し、それ以外は incoming を採用する。
- `boardName`: 空文字、または `boardUrl` と同値の表示名は placeholder とする。incoming が placeholder かつ既存 boardName が非 placeholder の場合だけ既存値を保持し、それ以外は incoming を採用する。
- `boardUrl`: `threadId` を構成する host/board と一致する既存の非空値を canonical として保持する。既存値が空の場合だけ、同じ identity を表す incoming 値で補完する。
- `latestResCount`: 既存 DAO の単調増加契約を維持し、`max(existing, incoming)` とする。incoming `0` で減少させない。
- `sortOrder`、`isPinned`、`firstVisibleItemIndex`、`firstVisibleItemScrollOffset`: `open_thread_tabs` の既存行を更新せず、そのまま保持する。
- 履歴由来の `prevResCount`、`lastReadResNo`、`firstNewResNo`、`newResCount` と bookmark 色は ensure の永続化対象へ追加しない。

新規タブでは既存 canonical metadata がないため incoming metadata を保存し、`latestResCount` の単調増加契約だけを通常どおり適用する。この補正は ensure 専用であり、明示的な解決済み板情報更新 API や thread info 更新 API の契約を狭めない。

**代替案:** repository に `Mutex` を一つ追加して `saveOpenThreadTabs` を直列化しても、初回未読込の一覧、メモリ先行更新、Flow 上書き、選択との順序を解決しないため採用しない。

### 3. mutation intent は coordinator の単一キューで直列化する

`ThreadTabsCoordinator` は add/delete/pin/update の intent と completion (`CompletableDeferred` 相当) を単一 FIFO `Channel` へ送り、一つの worker coroutine だけが処理する。公開 mutation API は suspend し、次の順序で結果を返す。

1. 初回 canonical snapshot が `Loaded` になるまで待つ。
2. intent を pending operation として登録し、canonical snapshot へ順序どおり投影した表示一覧を更新する。
3. 対応する対象行 repository mutation を await する。
4. 成功時は Room Flow が operation 固有の確認条件（add は対象存在、delete は対象不存在、pin/info は期待値一致）を満たすまで待つ。
5. 確認後に pending operation を除去し、canonical snapshot の投影結果を公開する。
6. DB 失敗または cancellation 時は pending operation を除去し、canonical snapshot へ戻して失敗を completion へ返す。worker は次 intent を処理可能な状態を保つ。

このキューが UI intent から DB completion と Flow confirmation までを直列化する。`DatabaseWriteGate` はアプリ全体の DB write/suspension coordination を引き続き担い、両者の責務を混同しない。

各 intent は completion に加えて caller cancellation の ownership を保持する。worker は dequeue 時だけでなく `awaitLoadedState()` の直後にも cancellation を確認し、各 intent の処理を worker 自体とは分離した operation coroutine で実行する。completion の cancellation はその operation coroutine だけへ伝播し、long-lived worker は cancel しない。operation coroutine は pending 登録、repository 呼出し、Flow confirmation を所有するため、`DatabaseWriteGate` や repository 内の suspend 待機にも同じ cancellation context が届く。worker は operation の終了と cleanup を await してから次 intent へ進み、FIFO を維持する。caller の `Job` を `withContext` へ直接渡して structured concurrency を壊す方式は使用しない。

cancellation と DB write の境界は repository の `DatabaseWriteGate.withWritePermit` 内で Room transaction block を開始した時点とする。境界前の cancellation は transaction を開始させない。境界後も operation cancellation を Room へ伝播し、cancellation が transaction の成功完了より先に観測された場合は `withTransaction` の契約どおり rollback する。transaction の成功完了が cancellation より先なら、その commit は既完了 mutation として扱う。どちらの順序でも部分 write、補償 write、自動再試行は行わない。caller には cancellation を維持し、selection/navigation など caller 固有の後続 side effect を実行しない。coordinator は repository invocation が終了するまで次 intent を開始せず、最終 Room Flow snapshot を canonical state として pending projection を除去する。

**代替案:** 各 mutation を独立 `scope.launch` する方式は完了を報告できず、受付順も保証しないため廃止する。単純な coordinator `Mutex` より、FIFO と completion ownership が明示される intent queue を採用する。

### 4. 公開一覧は canonical snapshot に pending operation を再適用して導出する

coordinator は `canonicalTabs` と `pendingOperations` を別に保持し、公開 `openThreadTabs` は純粋な projection 関数で導出する。Room Flow が古い 1,252 件を emit しても pending add を再適用して 1,253 件相当を維持し、新しい 1,253 件 snapshot が確認された時点で pending add を外す。delete と pin も同じ規則を使い、古い emission による resurrection や pin の巻き戻りを防ぐ。

projection は `threadId` の一意性、canonical/pending の順序、既存 tab の sort/pin/scroll 保持を明示して単体テスト可能な pure function とする。selection は本変更では変更せず、後続変更が completion と canonical state を使って確定する。

### 5. full replacement は名前と可視性で bulk use case に限定する

現在の `saveOpenThreadTabs` を通常操作から削除する。全件置換が将来必要な場合は `replaceOpenThreadTabsForBulkOperation` のように bulk 専用であることが分かる API とし、次を契約化する。

- 初回 canonical load 完了前には呼ばない。
- mutation intent queue と競合しない明示的な exclusive orchestration からだけ呼ぶ。
- `DatabaseWriteGate` と Room transaction の内側で `upsertAll` / `deleteNotIn` を行う。
- 通常の add/delete/pin/info/scroll からは呼ばない。

現行 restore は `PendingRestoreApplier` が `AppDatabase` 作成前に DB file 自体を交換するため、この API へ移行せず既存挙動を維持する。

### 6. 後続変更へ readiness/completion 契約だけを公開する

`ui/tabs/store/TabSessionStore.kt` はスレッドタブ readiness を await でき、thread route の register/ensure が canonical confirmation 後に成功/失敗を返す suspend API を公開する。本変更ではその API を deep-link からまだ使用せず、`fix-thread-deep-link-selection-consistency` の明示的依存先とする。

## Data Flow

```text
UI / later DeepLinkHandler
        │ suspend mutation intent
        ▼
ThreadTabsCoordinator FIFO worker
        │ wait Loaded
        ├─ pending operation ──▶ projection ──▶ visible tab state
        │
        ▼
TabsRepository targeted mutation
        │ DatabaseWriteGate.withWritePermit
        │ AppDatabase.withTransaction
        ▼
OpenThreadTabDao / ThreadStateRepository
        │
        ▼
Room Flow ──▶ canonical snapshot ──▶ operation confirmation
                                  └─▶ projection with pending operations
```

## Affected Areas

- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt`: loading/canonical/pending state、intent queue、suspend mutation、Flow reconciliation。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt`: readiness と completion を公開し、既存 caller を suspend 契約へ接続。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/viewmodel/ThreadTabCoordinator.kt`: read-map-full-save を対象 ThreadState update へ変更。
- `app/src/main/java/com/websarva/wings/android/slevo/data/repository/TabsRepository.kt`: targeted mutation と guarded bulk replacement。
- `app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/dao/OpenThreadTabDao.kt`: single-row insert/delete/pin と max sort order の query/update。
- 必要に応じて `ui/tabs/model/` または `ui/tabs/coordinator/` に loading state、mutation result、pure projection を分離する。
- `BoardTabsCoordinator.kt` は対象外。ただし共有 repository API の rename により compile adjustment が必要な場合も board behavior は変更しない。

## Error Cases and Recovery

- 初回 Flow 停止中: intent は queue で待機し、空一覧を保存しない。caller cancellation は DB mutation 開始前なら intent を取り消す。
- readiness 完了後または `DatabaseWriteGate` 待機中の caller cancellation: intent operation だけを cancel して transaction 開始を防ぎ、long-lived worker と後続 FIFO intent は継続する。
- Room transaction 開始後の caller cancellation: cancellation が成功完了より先なら rollback、成功完了が先なら既完了 commit とし、原子的な確定を待つ。補償 write、retry、selection/navigation を行わず、Room Flow の最終 snapshot へ収束する。
- DB write failure: pending projection を除去し、最後の canonical snapshot を表示し、失敗を caller へ返す。後続 intent worker は継続する。
- 対象なし delete/pin: repository の no-op 結果を completion へ返し、一覧全体を生成または削除しない。
- stale Flow: operation 確認前の snapshot には pending operation を再適用する。
- worker cancellation/application teardown: 未完了 completion を cancellation で完了し、fire-and-forget save を残さない。
- backup suspension: repository write は既存 gate で待機し、gate の外で DB write を行わない。

## Compatibility and Migration

- DB schema と migration version は変更しない。既存 1,252 件を含む `open_thread_tabs` 行、`sortOrder`、`isPinned`、scroll columns をそのまま読む。
- API は同期/fire-and-forget から suspend completion へ変わるため source compatibility はない。すべての production/test call site を同じ変更で更新する。
- restore/export の file-level contract、`DatabaseWriteGate` の public contract、thread state の 30 日遅延 GC を維持する。
- rollback は application code を旧版へ戻すだけでよく、DB downgrade/migration は不要。ただし旧版の full-save race 自体は復活する。

## Testing Strategy

- `ThreadTabsCoordinatorTest.kt` に制御可能な Room-like Flow、`StandardTestDispatcher`、`CompletableDeferred` barrier を導入する。
  - 1,252 件を Flow に準備して初回 emission を停止し、add intent が DB mutation を開始せず待つこと。
  - 初回 1,252 件、pending add、古い 1,252 件、確定 1,253 件を順に emit し、既存行削除と表示巻き戻りがないこと。
  - 初回 empty emission 後は loaded-empty として add/delete が進むこと。
  - rapid add/delete/pin を enqueue し、repository call と completion が FIFO、最終 projection が一意で正しいこと。
  - repository failure/cancellation 後に canonical state を維持し、次 intent が進むこと。
  - caller を `awaitLoadedState()` 中に cancel してから初回 snapshot を emit し、repository mutation が一度も呼ばれず、pending projection が残らず、後続 intent が進むこと。
  - repository の cancellable barrier で `DatabaseWriteGate` 待機相当を再現し、caller cancellation が in-flight operation へ届いて write body を開始せず、worker 自体は継続すること。
  - transaction 開始済み相当の cancellable barrier では cancellation が repository invocation へ届き、rollback 相当の終了と cleanup を worker が待つこと。成功完了が cancellation より先の fixture では既完了結果を二重処理せず、どちらも最終 canonical emission へ収束してから後続 intent が進むこと。
- `TabsRepositoryThreadStateTest.kt` の Room in-memory DB で single-row add/delete/pin/info/scroll が他の 1,252 行を変更せず、sort/pin/scroll/thread state invariant を維持することを確認する。
  - 解決済み metadata を持つ既存タブへ `boardId = 0L`、初期 thread URL title、`boardUrl` 表示名を再 ensure し、DB Flow の canonical 結果が既存 `boardId`、title、boardName、boardUrl、sort、pin、scroll を保持し、resCount を減少させないこと。
  - 同じ既存タブへ非 placeholder の boardId、title、boardName を ensure し、有効な incoming field だけが更新され、対象外タブが byte-for-field 不変であること。
- `ThreadTabCoordinatorTest.kt` で thread info update が `saveOpenThreadTabs` / `deleteNotIn` を呼ばず対象 ThreadState だけを更新することを確認する。
- `TabSessionStoreTest.kt` で readiness と mutation completion/result の delegation を確認する。
- 既存 `DatabaseWriteGateTest.kt` を維持し、新しい repository write が gate と transaction を一度だけ通ることを repository test で確認する。
- 実装時の必須 command: `./gradlew testDebugUnitTest` と `./gradlew assembleDebug`。Room instrumented test は接続済み emulator/device で対象 test を実行する。

## Implementation Contract

1. application code を変更する前に、本変更の spec と tasks を読み、`fix-thread-deep-link-selection-consistency` の application code を先行実装しない。
2. `ThreadTabsCoordinator.bind` の Room collector 以外から canonical snapshot を直接更新しない。
3. 通常 mutation から `saveOpenThreadTabs`、`upsertAll`、`deleteNotIn` を呼ばない。対象行 DAO と対象 ThreadState update を使用する。
4. coordinator の public mutation は completion を返し、FIFO worker が readiness、pending projection、DB await、Flow confirmation、cleanup を所有する。
5. `DatabaseWriteGate` を迂回せず、repository の outer write API と既存 ungated helper を同じ transaction で正しく組み合わせる。coordinator queue を gate の代替にしない。
6. failure/cancellation の全経路で pending operation と completion を cleanup し、canonical state を破壊しない。
7. 新規 class/interface と非自明関数には repository の KDoc/comment rules を適用し、長い関数は section comment で分割する。
8. 1,252/1,253 件、blocked initial Flow、loaded-empty、rapid mutation の決定的テストを先に失敗させ、実装後に通す。
9. dequeue 後の caller cancellation は readiness 後に再確認し、intent ごとの operation coroutine へ伝播する。caller cancellation で long-lived worker を cancel せず、operation cleanup 完了前に後続 intent を開始しない。
10. Room transaction 開始前の cancellation は write を防ぐ。開始後は cancellation を Room へ伝播し、成功完了との順序に応じた rollback または既完了 commit の原子性を維持して、補償 write、retry、caller 固有の selection/navigation を追加しない。
11. 既存タブ ensure の ThreadState 保存前に DB canonical metadata とフィールド単位でマージし、repository、pending projection、scope 未 bind seam の placeholder 判定を一致させる。pin toggle と cancellation の処理経路は変更しない。

## Risks / Trade-offs

- [Flow confirmation が来ないと suspend が長期化する] → cancellation を伝播し、テストでは barrier で確認する。DB success だけで completion を返さず canonical confirmation を契約とする。
- [caller と独立した worker が cancellation 後も write を開始する] → readiness 後の再確認と intent 単位 operation coroutine への cancellation link を用い、gate/repository の suspend 待機まで伝播する。worker は operation cleanup を await して FIFO を維持する。
- [transaction 開始後の cancellation 結果を coordinator が推測する] → cancellation を Room へ伝播し、成功完了との順序に応じた rollback または既完了 commit を transaction の原子性へ委ねる。補償 write を禁止し、最終 Flow snapshot だけを canonical とする。
- [pending projection と canonical model の二層化で複雑になる] → operation ごとの pure projection と確認 predicate を分離し、1 intent ずつ直列化する。
- [1,252 件で projection cost が増える] → correctness を優先し、threadId index/map を使って不要な全件 copy を抑える。性能最適化で DB 正本契約を崩さない。
- [source API 変更が広い] → compile error を利用して全 call site を列挙し、fire-and-forget wrapper を残さない。
- [bulk replacement の誤用] → API 名、可視性、call-site test で通常経路から隔離する。
- [repository と pending projection の metadata merge が不一致になる] → placeholder 判定を一つの純粋な規則へ集約し、同一入力に対する repository canonical Flow と projection の field-level assertion を追加する。

## Migration Plan

1. test fixture と failing deterministic tests を追加する。
2. DAO/repository の targeted API を追加し、既存 full-save caller を移行する。
3. coordinator state、FIFO worker、pending projection、completion API を導入する。
4. `TabSessionStore` と全 call site/test を suspend contract へ移行する。
5. 通常経路から full replacement call が消えたことを検索と test で確認する。
6. unit build/test と Room instrumented test を確認してから、後続変更を開始する。

Rollback 時は schema 変更がないため code revert のみとする。新 API と後続変更を同時に部分 rollback せず、依存順の逆順で戻す。

## Open Questions

なし。具体的な型名を既存ファイルへ内包するか専用 model file に分けるかは、上記 state/queue/result 契約を変えない範囲の実装判断とする。
