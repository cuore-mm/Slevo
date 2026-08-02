## Context

### Dependency and current behavior

本変更は `refactor-thread-tab-persistence-consistency` の実装とテスト完了後にのみ実装する。先行変更が提供する thread-tab `Loaded` readiness、targeted mutation completion、Room canonical confirmation を利用し、それらを本変更で再実装しない。

現在の `ui/navigation/DeepLinkHandler.kt` は thread URL を `AppRoute.Thread` へ解決すると、`TabSessionStore.registerAndSelectThreadRoute` を呼んだ直後に `NavController.navigateToThreadScreen` を実行する。現行 register は `ThreadTabsCoordinator.ensureThreadTab` のメモリ先行更新と非同期保存を待たずに `selectThreadTab` を呼ぶ。`selectThreadTab` は対象が一覧にないと selected key を `null` にし、`ui/bbsroute/BbsRouteScaffold.kt` の `deriveSelectedPageIndex` は null/不一致を page 0 に変換して `scrollToPage(0)` する。

`ui/thread/screen/ThreadScaffold.kt` は destination 側でも `threadLoaded` 後に ensure/select を行うため、Deep Link handler と destination の二箇所が登録・選択を調停している。失敗通知は `DeepLinkHandler` に既にある error Toast と `finally` の `onConsumed()`、thread/board resolution 失敗時の既存 Toast/navigation recovery が存在する。

### Approved UI boundary

- pending/一時不在の Deep Link target では現在 selection と Pager page を維持する。
- canonical target 確認後だけ target を選択し navigation する。
- 失敗時は既存 selection と現在画面を維持し、既存 Deep Link error notification/consume 経路を使う。
- 新しい UI component、文言、icon、theme、accessibility semantics は追加しない。

## Goals / Non-Goals

**Goals:**

- thread Deep Link の readiness、tab registration、canonical confirmation、selection、navigation を一つの順序付き suspend operation にする。
- pending target と selected key を分離し、未確認 target を selected key に昇格させない。
- selected key が一時的に不一致でも thread Pager を page 0 へ移動しない。
- failure/cancellation で既存 selection を保持し、navigation を実行しない。
- 順序と no-jump を coroutine/Flow/navigation の決定的テストで確認する。

**Non-Goals:**

- URL pattern、domain、5ch.net から 5ch.io への既存 normalization rule の変更。
- board Deep Link や board Pager の behavior 変更。
- tab persistence、DAO、DB schema の追加変更。
- retry button、dialog、snackbar、Toast 文言、accessibility announcement の追加。
- navigation back-stack policy の変更。

## Decisions

### 1. Deep Link を一つの suspend state machine として処理する

`DeepLinkHandler.kt` の thread 分岐を、テスト可能な suspend orchestration として次の順序に固定する。

```text
Received
  → Resolve/Normalize
  → AwaitThreadTabsReady
  → RegisterTargetAndAwaitCanonicalConfirmation
  → VerifyTargetInCanonicalTabs
  → SelectTarget
  → NavigateToThread
  → Consume
```

`TabSessionStore.registerAndSelectThreadRoute` は「ensure を要求してすぐ select」する API ではなく、先行変更の completion API を用いた順序付き result に変更する。責務を明確にするため、内部では registration completion と selection completion を分け、registration が成功し canonical 一覧に対象 `ThreadId` が存在することを再確認した後だけ selection を行う。`DeepLinkHandler` は成功 result の後だけ `navigateToThreadScreen` を呼ぶ。

**代替案:** navigation 後に `ThreadScaffold` で待つ方式は、遷移時点の route と Pager state を不整合にし、二重 ensure/select を残すため採用しない。

### 2. pending target は selected key に書かない

Deep Link target の登録待機中は、既存 `_selectedThreadTabKey` を変更しない。必要な pending target は operation-local state または `TabSessionStore`/coordinator の専用 pending key として保持し、Pager の selected key として公開しない。canonical confirmation 後に `selectThreadTab` を呼び、成功 result を得た場合だけ navigation する。

`ThreadTabsCoordinator.selectThreadTab` は、対象 key が canonical/公開一覧に存在しない場合に selected key を `null` へ変更せず、失敗 result を返す。明示的な `null` 選択が必要な last-tab deletion と、存在しない target の選択失敗を別 API/branch として扱う。実削除時の `updateSelectedThreadKeyAfterRemoval` による adjacent/last/null 補正は維持する。

**代替案:** target key を先に selected key へ設定して Pager 側だけ抑止すると、selection state 自体が canonical list と不整合になるため採用しない。

### 3. thread Pager の一時的不一致は「scroll 指示なし」にする

`BbsRouteScaffold.kt` の page 導出に thread 専用の missing-selection policy を渡せるようにする。board call site は既存 behavior を維持し、`ThreadScaffold` だけが preserve policy を指定する。

preserve policy では selected key に一致する tab がある場合だけ index を返し、null/不一致では「programmatic scroll 指示なし」を返す。`LaunchedEffect` は解決済み index に対してだけ `pagerState.scrollToPage` を呼ぶ。Pager 初回生成時は canonical target 選択後に Thread screen へ navigation するため、Deep Link target の正しい index を initial page に使える。実削除時は coordinator が selected key を adjacent/first/null に補正するため、Pager 自身が page 0 を選ぶ必要はない。

`ThreadTabsCoordinator.animateThreadPage` など selected page 不在時に `?: 0` を使う thread 経路も検索し、一時的不一致時は animation/no-op として page 0 を target にしない。

**代替案:** shared `deriveSelectedPageIndex` の fallback を全画面で削除すると board behavior まで変更して承認範囲を超えるため、thread call site に限定する。

### 4. destination 側の route 初期化は同じ completion 契約を使う

`ThreadScaffold.kt` の `LaunchedEffect(threadRoute, threadLoaded)` は、既に canonical target が selected なら何もしない。tab click など Deep Link 以外の route entry で対象登録が必要な場合も `TabSessionStore` の readiness/registration completion を await し、対象存在確認後だけ select する。Deep Link handler が成功後に遷移した場合、destination 側は二重 mutation を行わない。

### 5. failure は既存 error/consume 経路へ集約する

URL 解決失敗、registration failure、canonical confirmation failure、selection failure では `navigateToThreadScreen` を呼ばず、operation 開始前の selected key を維持する。`DeepLinkHandler` は現在の error Toast 表示処理を共通 helper/branch として再利用し、既存 `invalidUrlMessage` resource の内容を変更しない。`finally` の `onConsumed()` は成功/失敗で一度だけ実行する。

Coroutine cancellation は新しい失敗 Toast を表示せず再 throw し、先行変更の mutation cancellation cleanup に委ねる。新 URL による `LaunchedEffect` 再起動時も古い target を選択/navigation しない。

**代替案:** 新しい retry UI または専用 error 文言は UI 承認範囲外のため採用しない。

## Data Flow

```text
MainActivity deepLinkUrl
        ▼
DeepLinkHandler suspend orchestration
        │ resolve + normalize
        ▼
TabSessionStore.awaitThreadTabsReady()
        ▼
register target ──▶ ThreadTabsCoordinator mutation queue
        │                    │ targeted DB write
        │                    ▼
        │              Room canonical Flow
        ◀──────── canonical confirmation/result
        ▼
select only if target exists ──▶ selectedThreadTabKey
        ▼
navigateToThreadScreen
        ▼
ThreadScaffold sees already selected canonical target
```

pending/failure branch:

```text
current selected key/page ───────────────┐
pending target (separate) → failure      │ unchanged
                         └→ existing error/consume, no navigation
```

## Affected Areas

- `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/DeepLinkHandler.kt`: suspend state machine、success-only navigation、existing error/consume handling、testable orchestration boundary。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt`: readiness、registration completion、canonical verify、selection result の合成。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt`: absent target で既存 selection を消さない selection result、explicit removal correction。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`: thread-only missing-selection preserve policy、scroll effect guard、page helper。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`: duplicate ensure/select 防止と completion API 利用。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScaffold.kt`: shared signature の compile adjustment のみ。board behavior は変更しない。

## Error Cases and Recovery

- readiness 待機中: current selection/page を維持し、navigation しない。
- registration DB failure: pending target を cleanup し、current selection/page を維持し、既存 Deep Link error Toast を表示して URL を consume する。
- write success後の canonical confirmation 待機中: target を selected key にせず、current page を維持する。
- selection 直前に target 不在: selection API は current key を変更せず failure を返し、navigation しない。
- cancellation/new intent: mutation cleanup 後に cancellation を伝播し、古い route の Toast/selection/navigation を実行しない。
- last tab の実削除: Deep Link pending と区別し、既存 removal correction により null/adjacent を明示的に設定する。

## Compatibility and Migration

- DB、DataStore、route serialization、URL pattern に migration はない。
- `TabSessionStore.registerAndSelectThreadRoute` と `ThreadTabsCoordinator.selectThreadTab` の completion/result contract は source-level change であり、全 call site/test を更新する。
- board Deep Link、board selection、board pager fallback は変更しない。
- 既存 Toast resource と accessibility semantics を変更しないため resource migration はない。
- rollback は本変更を先に戻し、その後にのみ先行 persistence 変更を戻す。依存順を逆転させない。

## Testing Strategy

- `DeepLinkHandler` の thread orchestration を unit test 可能な suspend boundary にし、fake/deferred で call order を記録する。
  - readiness blocked 中は register/select/navigate が未呼出しで current selection が不変。
  - registration write complete だけでは select/navigate せず、canonical target confirmation 後に select、selection success 後に navigate。
  - registration/confirmation/selection failure では navigate せず、既存 error callback と consume が一度、selection は不変。
  - cancellation では古い target の select/navigate/error を行わない。
- `TabSessionStoreTest.kt` で ensure result、canonical target existence、selection result の順序を `coVerifyOrder` と deferred Flow で確認する。
- `ThreadTabsCoordinatorTest.kt` で absent target selection が既存 selected key を null/別 key にせず failure を返し、confirmed target のみ selection できることを確認する。
- `BbsRouteScaffoldSelectionTest.kt` で thread preserve policy の missing key が page 0 ではなく no-scroll を返し、matched key、loaded-empty、confirmed deletion後の adjacent key を検証する。board policy の既存 missing→first test は維持する。
- 必要なら `BbsRouteScaffold` の Compose test を追加し、current page が非 0 の状態で transient missing key を与えても `scrollToPage(0)` 相当の移動がないことを確認する。
- `NavigationExtensionsTest.kt` または新規 Deep Link test で canonical confirmation 前/失敗時に thread destination が back stack へ追加されず、成功時だけ一度追加されることを確認する。
- 実装時の必須 command: `./gradlew testDebugUnitTest`、`./gradlew assembleDebug`。UI test を追加した場合は接続済み emulator/device で対象 test を実行する。

## Implementation Contract

1. `refactor-thread-tab-persistence-consistency` の readiness、mutation completion、canonical confirmation が実装・test 済みであることを確認してから着手する。不足を本変更の ad-hoc polling や delay で代替しない。
2. Deep Link target を canonical 一覧で確認する前に selected key へ設定せず、navigation しない。
3. absent target の selection attempt で既存 selected key を null にしない。実削除の補正だけが adjacent/first/null を明示設定する。
4. thread Pager の unresolved selection は no-scroll とし、`?: 0` で programmatic page 0 movement を発生させない。board behavior は維持する。
5. Deep Link handler と ThreadScaffold から同一 target の競合 mutation を発行しない。両 entry point は同じ completion contract を使う。
6. failure では current selection/page を維持し、thread navigation を行わず、既存 error Toast/consume 経路のみを再利用する。resource/UI/accessibility を追加変更しない。
7. cancellation を通常 error として握りつぶさず、古い target の side effect を停止する。
8. readiness、canonical confirmation、selection、navigation の順序を deferred を使う決定的テストで固定し、time delay に依存する test を書かない。

## Risks / Trade-offs

- [Deep Link 表示開始が DB/Flow confirmation 分だけ遅くなる] → 誤選択・データ損失防止を優先し、既存画面/selection を維持して待つ。新 loading UI は追加しない。
- [shared scaffold の変更が board に波及する] → missing-selection preserve policy を thread call site のみに指定し、board regression test を残す。
- [failure Toast の既存文言が persistence failure を詳細に説明しない] → 新文言は未承認のため現行 error notification を再利用し、詳細は logger に記録する。文言改善は別途 UI approval が必要。
- [destination 側との二重 orchestration] → selected canonical target の guard と共通 `TabSessionStore` completion API で一経路化する。
- [cancellation timing が複雑] → structured concurrency と先行変更の pending cleanup を使用し、detached coroutine を作らない。

## Migration Plan

1. 先行変更の completion API と tests が完了していることを確認する。
2. selection result と thread-only no-scroll の failing tests を追加する。
3. coordinator/store の selection contract を変更する。
4. Deep Link suspend orchestration を success-only navigation へ変更する。
5. ThreadScaffold の二重 ensure/select を同じ completion contract へ統合する。
6. pager helper/effect を thread-only preserve policy へ変更し、board regression を確認する。
7. unit/build/UI verification 後に本変更を完了する。

DB migration はない。rollback は本変更の application code をまとめて戻し、先行 persistence API は残す。

## Open Questions

なし。既存 error Toast の文言改善や retry UI は本変更に含めず、必要なら別の UI approval と変更として扱う。
