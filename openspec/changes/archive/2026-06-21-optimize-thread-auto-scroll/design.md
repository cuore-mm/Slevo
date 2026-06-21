## Context

スレッド画面の自動スクロールは `ObserveAutoScrollEffect` が `LazyListState.scrollBy()` をフレームごとに呼び出して進めている。RouteViewModel 化後は、スレッドタブのスクロール位置保存により `openThreadTabs` が更新され、その Flow が `ThreadRouteViewModel.uiStateFor(tabKey)` の `tabFlow` 入力として `ThreadUiState` 再合成を発火させる構造になった。

自動スクロール中は `firstVisibleItemIndex` / `firstVisibleItemScrollOffset` が継続的に変わるため、スクロール位置保存、Room/Repository Flow、`ThreadUiState` 合成、Compose 再描画が連鎖しやすい。さらに `ObserveAutoScrollEffect` が `listState.isScrollInProgress` を `LaunchedEffect` key に含めると、自動スクロール自身の `scrollBy()` によるスクロール状態変化で Effect が再起動し、かくつきの原因になる。一方で `isScrollInProgress` を単純に外すだけでは、ユーザー手動スクロールで `scrollBy()` がキャンセルされた後に自動スクロールが再開しない可能性がある。

## Goals / Non-Goals

**Goals:**

- 自動スクロール中のスクロール位置保存が、スレッド表示用 `ThreadUiState` の重い再合成を連続発火させないようにする。
- 自動スクロール自身の `scrollBy()` による `isScrollInProgress` 変化でスクロール Effect が再起動しないようにする。
- ユーザーが自動スクロール中に手動スクロールしても、自動スクロール設定を解除せず、操作終了後に再開できるようにする。
- タブ切替、画面離脱、画面破棄時のスクロール位置保存と復元は維持する。

**Non-Goals:**

- 自動スクロール速度や UI デザインの変更。
- スクロール位置保存の DB スキーマ変更。
- `ThreadTabInfo` からスクロール位置を完全に別モデルへ移す大規模分離。
- ミニマップ/勢いバー描画の全面最適化。
- 下端到達通知の発火ポリシー変更。自動更新の実行可否は既存どおり ViewModel 側の 10 秒制御に任せる。

## Decisions

### Decision 1: `ThreadUiState` 合成入力ではスクロール位置だけの `ThreadTabInfo` 更新を無視する

`ThreadRouteViewModel.createUiStateFlow(tabKey)` の `tabFlow` は `openThreadTabs` から対象タブを取り出すが、`distinctUntilChangedBy` により `ThreadUiState` 合成に必要なキーだけで変化判定する。比較キーには `id`、title、board 情報、レス数、新着/既読境界、bookmark 色、pin 状態を含める。一方で `firstVisibleItemIndex` / `firstVisibleItemScrollOffset` は含めない。

これにより、スクロール位置保存による `openThreadTabs` emit は継続しても、表示内容に関係しない scroll offset だけの変更では `visiblePostRows` や `replyCounts` の再計算を走らせない。

代替案として自動スクロール中のスクロール位置保存を完全停止する案もあるが、タブ切替や画面離脱時の復元精度低下リスクがあるため、まず合成入力側で無視する方針を採用する。

### Decision 2: 自動スクロール Effect は `isScrollInProgress` を key にしない

`ObserveAutoScrollEffect` の `LaunchedEffect` key から `listState.isScrollInProgress` を外す。自動スクロール自身の `scrollBy()` で `isScrollInProgress` が変わっても Effect を再起動しないため、フレームごとのスクロールが途切れにくくなる。

代替案として以前の key 構成へ戻す案もあるが、かくつき原因を再導入するため採用しない。

### Decision 3: 手動スクロールは「自動スクロール解除」ではなく「一時停止」として扱う

ユーザーが自動スクロール中にドラッグした場合、`isAutoScroll` 自体は true のまま維持する。`LazyListState.interactionSource.interactions` の `DragInteraction.Start` / `Stop` / `Cancel` を監視し、drag 開始で `isPausedByUser = true`、drag 終了で「再開待ち」状態へ遷移させる。

再開待ち中は、自動スクロール loop とは別の `LaunchedEffect` で `snapshotFlow { listState.isScrollInProgress }` を監視し、慣性 scroll が完全に止まったことを確認してから 100〜200ms 程度の短い猶予を置き、最後に `isPausedByUser = false` として loop を再開する。自動スクロール loop 本体では `isScrollInProgress` を参照せず、`isAutoScroll` と `isPausedByUser` だけで起動/停止を切り替える。

これにより、programmatic scroll 自身が作る `isScrollInProgress` を loop 内で誤検知せず、手動操作による一時停止と再開条件だけを分離して扱える。固定時間だけで再開する案は実装が単純だが、fling 中に自動スクロールが介入しうるため採用しない。

### Decision 4: 下端到達通知は今回変更しない

自動スクロールが下端に到達した状態では `onAutoScrollBottom()` が複数回呼ばれうるが、現在は `ThreadRouteViewModel.onAutoScrollReachedBottom(tabKey)` が表示中タブ確認、`isAutoScroll` 確認、10 秒間隔制御を持っている。今回の不具合はスクロール位置保存による再合成と、手動スクロール後の再開トリガー喪失が主因であり、下端到達通知の発火ポリシー変更は直接の修正対象にしない。

UI 側 edge-trigger / throttle は、下端滞在中の呼び出し負荷が実測上問題になった場合の follow-up とする。今回の実装では既存挙動を維持し、更新間隔の正本を ViewModel 側に残す。

## Risks / Trade-offs

- [Risk] scroll offset を `ThreadUiState` 合成入力から除外すると、スクロール位置に依存した表示フィールドが将来追加された際に更新漏れが起きる → `ThreadTabUiStateSourceKey` に含める項目を明示し、表示内容に関係するタブ項目を追加した場合は比較キーも更新する。
- [Risk] 手動操作停止と自動スクロール再開を同じ loop に混在させると、programmatic scroll と user scroll の境界が曖昧になりやすい → 停止/再開判定は別 `LaunchedEffect` に分離し、loop 本体では `isScrollInProgress` を見ない。
- [Risk] 自動スクロール再開が早すぎるとユーザー操作を奪う → `DragInteraction.Stop` / `Cancel` 後は fling 完了検知と 100〜200ms 程度の猶予を組み合わせる。
- [Risk] `distinctUntilChangedBy` により必要な tab 変更が抑制される → boardId、title、レス数、新着境界、bookmark 色、pin など表示に関係する項目を比較キーへ含める。

## Migration Plan

1. `ThreadRouteViewModel` の `tabFlow` に、スクロール位置を除外した比較キーによる `distinctUntilChangedBy` を追加する。
2. `ObserveAutoScrollEffect` から `isScrollInProgress` key 依存を外す。
3. `DragInteraction` による pause 状態管理と、別 `LaunchedEffect` による fling 完了待ち + 猶予再開を追加する。
4. 下端到達通知の発火ポリシーは変更せず、ViewModel 側の既存 10 秒制御に任せる。
5. 自動スクロール中のスクロール位置保存、手動操作後の再開、タブ切替/画面離脱時の復元をテストする。

## Open Questions

- なし。
