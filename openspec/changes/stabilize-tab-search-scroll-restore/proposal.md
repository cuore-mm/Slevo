## Why

タブ一覧検索のスクロール位置復元は、起動直後の最初のタブ一覧では機能する一方、他画面から戻った後やスレ画面から開くタブ一覧 BottomSheet では復元されない。検索状態と復元用スクロール状態の保持スコープが分かれており、Composition 再生成や BottomSheet 再表示で復元情報だけが失われるため、状態管理を安定化する必要がある。

## What Changes

- タブ一覧検索の検索状態、検索前スクロール位置、クエリ遷移判定、スクロール命令を `TabListViewModel` 側へ集約する。
- `TabScreenContent` は `LazyListState` の実体と `scrollToItem` 実行だけを担当し、復元状態を `remember` に保持しない。
- 検索クエリが空から非空へ変わる直前に、板一覧・スレッド一覧それぞれの index/offset を ViewModel へ保存する。
- 検索解除時は完全リストへ戻った後、ViewModel が発行した復元命令を UI が消費してスクロール位置を復元する。
- 検索クエリ変更時は、現在表示中ページの検索結果リストだけを先頭表示する命令として扱う。
- 安定性優先の追加修正として、検索状態更新とスクロール副作用を段階分離し、UI が検索解除後の完全リストまたは検索クエリ変更後の検索結果リストを描画対象にしたことを確認してからスクロールを実行する。
- タブ一覧 BottomSheet を閉じるときは検索モードを明示的に終了し、再表示時に検索クエリだけが残る不整合を防ぐ。
- 通常タブ一覧と BottomSheet の検索状態は、それぞれの `TabListViewModel` スコープ内で独立して管理する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `tablist-ui`: タブ一覧検索のスクロール位置復元を、画面復帰・Composition 再生成・BottomSheet 再表示に対して安定して動作させる要件を追加する。

## Impact

- `TabListViewModel`: 検索前スクロールスナップショット、前回検索クエリ、復元待ち状態、スクロール命令、命令消費処理を追加する。復元命令は即時発行せず、復元待ちスナップショットまたは実行可能条件が揃った後の命令として扱う設計へ調整する。
- `TabListUiState`: スクロール命令または復元待ちスナップショットに関する UI 状態を追加する。
- `TabScreenContent`: `remember` ベースの復元状態を削除し、ViewModel の状態/命令に基づき、表示リストが更新済みであることを確認してから `LazyListState` へスクロール副作用を適用する。
- `TabsBottomSheet` / `BbsRouteScaffold`: BottomSheet dismiss 時に検索状態を閉じる導線を追加する。
- テスト: ViewModel のクエリ遷移・スクロール命令発行/消費、および UI の復元動作を検証する。
