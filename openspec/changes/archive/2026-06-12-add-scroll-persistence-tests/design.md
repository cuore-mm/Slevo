## Context

現在の `BbsRouteScaffold` は、タブレイアウト、ViewModel 初期化、ボトムバー、シート表示、スクロール位置保存を同じ Composable 内で扱っている。スクロール位置保存は Issue 490 の修正で、周期保存、非アクティブ化時保存、破棄時保存を持つ重要な副作用になったが、現状のままでは `BbsRouteScaffold` 全体を組み立てないと検証しづらい。

テストしやすくするには、保存判定、Flow 変換、Compose ライフサイクルに反応する副作用を分離し、それぞれ適した粒度で検証する構成が必要である。

## Goals / Non-Goals

**Goals:**

- スクロール位置保存の責務を `BbsRouteScaffold` から専用単位へ分離する。
- 重複保存抑制を JVM unit test で検証できるようにする。
- 連続更新中の周期保存を Flow の仮想時間テストで検証できるようにする。
- 非アクティブ化時保存と破棄時保存を Compose test で検証できるようにする。
- 既存の保存データ形式、保存経路、呼び出し側 API の外部挙動を維持する。

**Non-Goals:**

- スクロール位置をレス番号アンカー方式へ変更することは扱わない。
- DB スキーマや Repository の保存方式は変更しない。
- 自動スクロール速度、下端更新、タブページングの挙動は変更しない。
- `BbsRouteScaffold` 全体の大規模分割は行わない。

## Decisions

### 1. スクロール保存副作用を専用 Composable に分離する

`BbsRouteScaffold` 内のスクロール保存処理を、例えば `ObserveScrollPositionPersistence` のような internal Composable に分離する。この Composable は `tabKey`、`LazyListState`、`isActive`、保存コールバックを受け取り、周期保存、非アクティブ化時保存、破棄時保存をまとめて扱う。

`BbsRouteScaffold` はレイアウトとタブ管理に集中し、スクロール保存は専用 Composable を呼び出すだけにする。これにより、保存ロジックのテストでボトムバー、シート、URL ダイアログ、ViewModel 生成を組み立てる必要がなくなる。

### 2. 保存位置は値オブジェクトで扱う

`firstVisibleItemIndex` と `firstVisibleItemScrollOffset` のペアは、内部的に `ScrollPosition` のような値オブジェクトで扱う。これにより、重複保存判定や Flow テストの期待値を読みやすくし、index / offset の取り違えを防ぐ。

### 3. 重複保存抑制は純粋ロジックとして切り出す

直近保存位置を保持し、同一位置なら保存しない判定を `ScrollPositionSaveState` のような小さな internal class へ分離する。Compose の state や ViewModel に依存しないため、JVM unit test で高速に検証できる。

### 4. Flow 変換は仮想時間で検証できる関数にする

`distinctUntilChanged` と周期保存は、`Flow<ScrollPosition>.scrollPositionsForPersistence(intervalMillis)` のような internal 関数に分離する。テストでは `kotlinx-coroutines-test` の仮想時間を使い、連続更新中でも周期ごとに最新値が出ること、同じ値が重複して出ないことを検証する。

### 5. Compose test はライフサイクル副作用に限定する

Compose test では、`ObserveScrollPositionPersistence` を単体で `setContent` し、`isActive` を true から false に変えたときの保存と、Composable を composition から外したときの保存を確認する。周期保存の詳細は Flow test に寄せ、Compose test は副作用の接続確認に集中する。

## Risks / Trade-offs

- [Risk] 分離によりファイル数と internal API が増える。 → スクロール保存専用の小さなファイルにまとめ、公開 API にはしない。
- [Risk] Compose test で `LazyListState` の実スクロールを扱うと不安定になる。 → `LazyListState` は `scrollToItem` などで明示的に位置を設定し、検証対象を非アクティブ化・破棄時保存に絞る。
- [Risk] Flow の `sample` テストが実時間に依存すると不安定になる。 → 仮想時間を使う unit test とし、実時間待機を使わない。
- [Risk] `BbsRouteScaffold` から分離する際に保存タイミングが変わる。 → 既存の周期保存、非アクティブ化時保存、破棄時保存の3経路を専用 Composable にそのまま移し、回帰テストで確認する。
