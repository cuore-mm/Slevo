## Context

### 現在の挙動

`ui/bbsroute/BbsRouteScaffold.kt` は `isTabsLoaded`、`openTabs`、`selectedTabKey` を別々に受け取り、`deriveSelectedPageIndex` と `MissingSelectionPolicy` で Pager index を導出する。`BoardScaffold` は既定の `UseFirst` を使うため null/欠落 key を page 0 として描画する一方、`ThreadScaffold` は `PreserveCurrentPage` を渡し、欠落時に `-1` を返す。後者では programmatic scroll は抑止されるが、Pager を作る前に `tabs.getOrNull(-1)` を評価するため `currentTabInfo` が null になり得る。

板/スレッド coordinator は選択 key を別の `StateFlow` として公開する。close では、削除対象が選択中なら削除前 index と更新後一覧から同位置の tab、範囲外なら末尾、0 件なら null を選ぶ。一方、任意の欠落 key、初回 canonical emission、pending operation、Deep Link 登録、Flow reconciliation の原因は共有 UI から区別できない。複数 Flow を Composable が別々に collect するため、一覧と key の中間的な組合せも観測可能である。

スレッド側の `ThreadTabsCoordinator`、`ThreadTabsProjection.kt` は Room snapshot を canonical、pending operation 適用後を projected list とし、単一 FIFO worker と canonical revision confirmation を使用する。`refactor-thread-tab-persistence-consistency` と `fix-thread-deep-link-selection-consistency`、および close/cancellation の既存 change がこの契約を定義している。本 change はそれらの artifact を変更せず、完了後の API/挙動を前提に選択解決を統合する。

### 制約

- stable key が選択の正本であり、page index を永続化または復元 fallback に使わない。
- known pending の間だけ現在の表示 tab を保持し、選択 key を書き換えない。
- confirmed invalid は UI の暗黙 fallback ではなく coordinator が補正する。
- canonical DB、projected pending list、FIFO、confirmation、retained close、cancellation 境界を維持する。
- 新しい文言、icon、theme、semantics/content description は追加しない。

## Goals / Non-Goals

**Goals:**

- 板/スレッド共通の原因ベース選択状態を定義し、画面種別ベース policy を削除する。
- loaded tabs と選択解決結果を一つの immutable snapshot として UI に渡す。
- valid、pending-missing、confirmed-invalid、empty を決定論的に遷移させる。
- 初期読込/復元、板 Deep Link、close、pending、確定無効、0 tab で表示 tab と選択状態を一貫させる。
- pure unit test、coordinator/orchestration test、Compose test で state transition と実表示を検証できる構造にする。

**Non-Goals:**

- tab entity、Room schema、sort order、scroll position の保存形式を変更しない。
- スレッド mutation queue、projection、canonical confirmation、cancellation 実装を再設計しない。
- Deep Link の URL 解決、navigation destination、エラー通知を変更しない。
- board tab persistence を thread と同じ FIFO queue に移行しない。
- pending 中の新しい操作、表示、animation、メッセージを追加しない。

## Decisions

### 1. 共有 selection snapshot を UI 境界に導入する

`ui/bbsroute` に immutable な generic model を置く。

```kotlin
sealed interface TabSelectionResolution<out Key : Any> {
    data object Loading : TabSelectionResolution<Nothing>
    data class Selected<Key : Any>(val key: Key) : TabSelectionResolution<Key>
    data class PendingMissing<Key : Any>(val key: Key) : TabSelectionResolution<Key>
    data object Empty : TabSelectionResolution<Nothing>
}

data class TabPresentationState<TabInfo : Any, Key : Any>(
    val tabs: List<TabInfo>,
    val selection: TabSelectionResolution<Key>,
)
```

`Selected.key` は同じ snapshot の `tabs` に一意に存在しなければならない。`PendingMissing.key` は一時的に `tabs` に存在しないことが producer により確認済みで、pending cause が生存している間だけ使用する。`Empty` は canonical load 完了かつ tabs が 0 件で、選択 key は null である。`Loading` は初回 canonical state 未確定である。

`BoardTabsCoordinator` と `ThreadTabsCoordinator` はそれぞれ `StateFlow<TabPresentationState<BoardTabInfo, String>>` / `StateFlow<TabPresentationState<ThreadTabInfo, String>>` を公開し、`TabSessionStore` はそれを委譲する。正確な既存 model 名は実装時に definition を確認して import し、別名 model を作らない。既存の `open*Tabs` と `selected*TabKey` は他 call site の互換性のため直ちに削除せず、同じ coordinator state から派生させる。`BoardScaffold` と `ThreadScaffold` は atomic snapshot のみを `BbsRouteScaffold` に渡す。

代替案の `missingSelectionPolicy` 拡張は、欠落原因を表せず、画面が invalid と pending を推測し続けるため採用しない。個別 Boolean (`isPendingSelection`) は tabs/key と別 emission になり同じ race を残すため採用しない。

### 2. confirmed-invalid の補正を coordinator の state reducer に集約する

各 coordinator は tabs、selected key、pending cause、削除前 index を入力に一つの reducer/publisher で snapshot を作る。

1. 初回 canonical 未確定: `Loading`。
2. tabs が 0 件: selected key を null にし `Empty`。
3. selected key が tabs に存在: `Selected(key)`。
4. selected key が不在だが、その key に対する既知の pending cause がある: key を維持し `PendingMissing(key)`。
5. それ以外: confirmed-invalid として coordinator が key を補正し、補正済み `Selected(key)` のみを公開する。

補正 target は、選択 tab close では既存どおり削除前 index が更新後範囲内なら同位置、範囲外なら末尾を使う。close 以外の stale/null/未知 key と初回 restore では先頭を使う。非選択 tab close では有効な現在 key を維持する。補正後一覧が空なら `Empty` とする。UI は confirmed-invalid state や page-0 fallback を受け取らず、選択 key の書換え callback も行わない。

板初回 canonical emission の一覧と補正済み key は同じ coordinator publish で公開する。これにより `BoardScaffold` の route 解決 effect が動く前でも、復元済み tabs があれば有効な `Selected` を描画できる。板 Deep Link は既存の register/ensure → select → navigate 順を維持し、navigation 前に coordinator snapshot が target の `Selected` になったことを確認する orchestration に揃える。

代替案の UI 側 first fallback は visual page と selected key の不一致を許すため採用しない。`currentPage` を coordinator に保存する案は stable-key source-of-truth 契約に反するため採用しない。

### 3. pending cause は既存 operation lifecycle から明示的に導出する

スレッドでは既存 `pendingOperations`、canonical revision、Deep Link readiness/registration lifecycle を source とし、selected key の一時的不在を説明する operation が存在する期間だけ `PendingMissing` を公開する。operation confirmation、失敗、または cancellation 後は pending cause を必ず除去し、次の publish で valid/confirmed-invalid/empty のいずれかへ遷移する。pending marker を新しい DB state として保存しない。

板では route/Deep Link の register/ensure が開始済みで target の反映待ちである期間だけ同じ pending cause model を使用できるようにするが、board tabs を新しい FIFO/projection architecture へ変更しない。通常の初回 null selection や存在しない任意 key を pending と分類してはならない。

この判断により「欠落しているから pending」と推測する実装を禁止する。pending cause がない欠落は confirmed-invalid である。

### 4. `BbsRouteScaffold` は resolution に応じて表示と同期を分ける

`BbsRouteScaffold` の型 parameter に `Key : Any` を追加し、`isTabsLoaded`、`openTabs`、`selectedTabKey`、`missingSelectionPolicy` の代わりに `TabPresentationState<TabInfo, Key>` を受ける。`getKey` も `(TabInfo) -> Key` とする。

- `Selected(key)`: pure helper が key の index を返し、Pager をその index へ同期する。`HorizontalPager(key = ...)` の stable key を維持する。Pager のユーザー移動時だけ既存 `onTabSelected` を呼ぶ。
- `PendingMissing`: programmatic scroll と Pager 起因の `onTabSelected` を抑止する。`currentTabInfo` は `pagerState.currentPage` の tab から取得し、content/bottom bar/sheets を継続描画する。`HorizontalPager` の stable item key により list reconciliation 中も表示 tab identity を保持する。
- `Loading`: 初回は既存 loading 表示とする。直前の stable snapshot を保持する再bindが既存要件として必要な場合だけ、その snapshot 全体を cache し、tabs と selection を別々に cache しない。
- `Empty`: `onEmptyTabs` を一度発火可能な effect に渡し、tab content を構成しない。

既存 `deriveSelectedPageIndex` は `deriveTabDisplayDecision` 相当の pure helper に置き換え、少なくとも `Selected(index)`、`PreserveCurrent`、`Loading`、`Empty` を区別する。実装名は `BbsRouteScaffoldSelectionTest.kt` と同じ package に置き、test から直接検証可能にする。`Selected` の invariant 違反を先頭 fallback で隠さず、debug/test で検出できる明示的な failure または producer 修正につながる結果にする。

### 5. active change との依存順を固定する

実装は `refactor-thread-tab-persistence-consistency` と `fix-thread-deep-link-selection-consistency` の残 task 完了後に行う。本 change は `ThreadTabPendingOperation`、projection、canonical confirmation、Deep Link ordering、retained close/cancellation の最終 API を読んで adapter/reducer を追加する。既存 change の spec、proposal、design、tasks を編集せず、既存 tests を削除・弱化しない。

## Data Flow

```text
Room canonical tabs ─┐
pending lifecycle ───┼─> coordinator reducer ─> TabPresentationState ─> BbsRouteScaffold
selection commands ──┤          │                         │
close removed index ─┘          └─ selected key repair    ├─ Selected: key -> pager index
                                                          ├─ PendingMissing: retain pager/tab
                                                          └─ Empty/Loading: no tab content
```

板 Deep Link は `resolve -> register/ensure -> select/confirm presentation -> navigate`、close は `request -> mutation/canonical update -> reducer repair -> Selected/Empty publish` の順を維持する。

## Affected Areas

- `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`: state model、API、display decision、Pager/content 同期。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScaffold.kt`: atomic board presentation state の collect と共有 API 呼出し。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`: screen-specific policy の削除と atomic thread state の collect。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/BoardTabsCoordinator.kt`: initial/restore/close/invalid reducer と atomic publish。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinator.kt`: pending cause と canonical confirmation を selection resolution に接続。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/TabSessionStore.kt`: presentation state 公開と Deep Link/close orchestration の委譲。
- `app/src/main/java/com/websarva/wings/android/slevo/ui/navigation/DeepLinkHandler.kt`: 板 target の selection confirmation が不足する場合のみ順序を補強する。
- 対応する `app/src/test` unit/orchestration tests と、必要最小限の `app/src/androidTest` Compose test。

## Error Cases and Recovery

- pending operation 成功: canonical/projected list に key が現れた時点で `PendingMissing` から `Selected` へ移り、key index へ同期する。
- pending operation 失敗/cancellation: pending cause を `finally` 相当の既存 lifecycle で除去し、旧 key が有効なら `Selected`、無効なら first/adjacent repair、0 件なら `Empty` にする。FIFO worker 自体は caller cancellation で停止させない。
- close confirmation: pending delete 中の projection と canonical emission のどちらでも selection repair を二重適用せず、削除前 index を一度だけ使用する。
- Board Deep Link 解決/登録失敗: navigation と target selection を確定せず、既存選択と表示を保持する。既存 error handling の文言を変更しない。
- invariant 違反 (`Selected.key` が tabs にない): UI fallback で隠さず test failure とし、producer の atomic publish を修正する。
- 0 tabs: selected key と runtime selection を null にし、既存 `onEmptyTabs` navigation を維持する。

## Compatibility and Migration

DB schema、entity、DataStore、backup、navigation route の migration は不要である。`MissingSelectionPolicy` と `BbsRouteScaffold` parameter は source-level breaking change だが internal call site は `BoardScaffold` と `ThreadScaffold` のみであり同一 commit で更新する。既存 public `StateFlow` は repository 内の他 consumer を壊さないよう atomic state から派生して暫定維持し、全 call site が新 state を使うことを確認後にのみ不要 API を削除する。

rollback は本 change の application-code commit を revert し、旧 policy と個別 Flow collection に戻す。永続形式を変更しないため data rollback は不要である。active dependency changes は rollback 対象に含めない。

## Testing Strategy

1. `BbsRouteScaffoldSelectionTest`: valid key の index、pending の preserve decision、Loading、Empty、invariant 違反を pure unit test で検証する。
2. `BoardTabsCoordinatorTest`: 初回 loaded non-empty + null、restore valid、restore invalid、非選択 close、選択 close の同位置/末尾、last close、pending cause の開始/成功/失敗を virtual time なしで検証する。
3. `ThreadTabsCoordinatorTest`: stale canonical emission 中の selected key 欠落が pending、confirmation 後 selected、失敗/cancellation 後 deterministic repair、rapid FIFO intents と既存 cancellation regression が不変であることを `MutableSharedFlow` と `CompletableDeferred` で検証する。
4. `DeepLinkHandlerTest` または board route orchestration test: board register/ensure、selection confirmation、navigation の順序、失敗時に旧 selection/page を保持することを検証する。
5. Compose test: Selected は対応 content を表示し、PendingMissing は page 0 へ移動せず現在 content を表示し続け、selection callback を発火せず、Selected への confirmation で target content に移ることを stable keys と test tags で検証する。0 tabs は content を表示しない。
6. 既存 thread pending、last-tab close、tab-list close、Deep Link cancellation tests を変更せず回帰確認する。
7. 実装完了時に `./gradlew testDebugUnitTest` と `./gradlew assembleDebug` を実行する。

## Implementation Contract

1. dependency change の最終コードと tests を再読し、pending/confirmation/cancellation API 名を確定する。dependency artifact は編集しない。
2. immutable resolution/snapshot model と pure display-decision helper を `ui/bbsroute` に追加し、型と非自明関数へ repository 規則どおり KDoc を付ける。
3. coordinator の全 publish 経路を列挙し、tabs/key/pending cause を一つの reducer 経由にする。confirmed-invalid snapshot を UI に公開しない。
4. close の既存 adjacent algorithm、thread projection/FIFO/canonical revision、retained scope、CancellationException 再throw を保存する。
5. `TabSessionStore` から atomic state を公開し、legacy flows が必要なら atomic state から派生させる。二つの独立 mutable source を残さない。
6. `BoardScaffold` と `ThreadScaffold` を同じ共有 API に移行し、`MissingSelectionPolicy` と screen-specific argument を削除する。
7. Pager content の tab は resolution 前の `selectedPage` ではなく、Selected の resolved index または PendingMissing の `pagerState.currentPage` から取得する。pending 中は programmatic scroll と selection callback を抑止する。
8. test を先に追加または同時追加し、受入 scenario ごとに一つ以上の deterministic assertion を置く。既存 test の期待値を新挙動に必要な範囲以外で変更しない。
9. application code の長い関数には section header、guard/fallback には非自明 control-flow comment を追加する。Preview function には KDoc を追加しない。

## Risks / Trade-offs

- [複数 Flow 互換 API が再び不整合を作る] → mutable source を atomic snapshot 一つに限定し、legacy flows は map/stateIn で派生させる。
- [pending marker が失敗後に残り永久に page を保持する] → operation lifecycle の success/failure/cancellation 全経路で marker 除去を test する。
- [Pager effect が recomposition 時に selection callback を発火する] → PendingMissing 分岐では effect を停止し、Compose test で callback count を検証する。
- [list reorder 中に別 content が見える] → `HorizontalPager` の stable item key を維持し、current page の tab から content を構成する。
- [close の adjacent 補正が二重実行される] → removed index を mutation result に結び、一回の reducer transition として扱う。
- [active change と同じ spec/コード領域で conflict する] → dependency 完了後に実装し、本 change の artifact は既存 active artifact を編集しない。

## Migration Plan

1. active dependencies の未完 task を完了する。
2. model/reducer と unit tests を追加する。
3. coordinator/store の atomic publish を導入し、legacy consumer を維持する。
4. 二つの scaffold call site と Pager logic を切り替え、旧 policy/helper を削除する。
5. orchestration/Compose regression tests、unit test、debug build を通す。
6. DB migration なしで通常 release する。

## Open Questions

なし。pending 中に新しいユーザー操作を追加するなど、本承認範囲を越える挙動が必要になった場合は実装を止めて別途 UI/product 承認を得る。
