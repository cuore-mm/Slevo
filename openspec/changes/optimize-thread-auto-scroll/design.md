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

## Decisions

### Decision 1: `ThreadUiState` 合成入力ではスクロール位置だけの `ThreadTabInfo` 更新を無視する

`ThreadRouteViewModel.createUiStateFlow(tabKey)` の `tabFlow` は `openThreadTabs` から対象タブを取り出すが、`distinctUntilChangedBy` により `ThreadUiState` 合成に必要なキーだけで変化判定する。比較キーには `id`、title、board 情報、レス数、新着/既読境界、bookmark 色、pin 状態を含める。一方で `firstVisibleItemIndex` / `firstVisibleItemScrollOffset` は含めない。

これにより、スクロール位置保存による `openThreadTabs` emit は継続しても、表示内容に関係しない scroll offset だけの変更では `visiblePostRows` や `replyCounts` の再計算を走らせない。

代替案として自動スクロール中のスクロール位置保存を完全停止する案もあるが、タブ切替や画面離脱時の復元精度低下リスクがあるため、まず合成入力側で無視する方針を採用する。

### Decision 2: 自動スクロール Effect は `isScrollInProgress` を key にしない

`ObserveAutoScrollEffect` の `LaunchedEffect` key から `listState.isScrollInProgress` を外す。自動スクロール自身の `scrollBy()` で `isScrollInProgress` が変わっても Effect を再起動しないため、フレームごとのスクロールが途切れにくくなる。

代替案として以前の key 構成へ戻す案もあるが、かくつき原因を再導入するため採用しない。

### Decision 3: 手動スクロールは「自動スクロール解除」ではなく「一時停止」として扱う

ユーザーが自動スクロール中にドラッグした場合、`isAutoScroll` 自体は true のまま維持する。`LazyListState.interactionSource.interactions` の `DragInteraction.Start` / `Stop` / `Cancel` を監視し、ユーザー操作中だけ自動スクロールの `scrollBy()` ループを止める。操作終了後は短い待機または次フレーム以降に自動スクロールを再開する。

これにより、`isScrollInProgress` による自動再起動には戻さず、ユーザー操作だけを再開トリガーとして扱う。

### Decision 4: 下端到達通知は過剰発火を抑える

自動スクロールが下端に到達した状態では、フレームごとに `onAutoScrollBottom()` を呼ぶと ViewModel 側 throttling があっても不要な呼び出しが続く。必要に応じて、下端へ入った瞬間または一定間隔だけ通知する edge-trigger / throttle を追加する。

この判断は主因対策ではないが、長時間自動スクロール時の余分な処理を抑える補助策として扱う。

## Risks / Trade-offs

- [Risk] scroll offset を `ThreadUiState` 合成入力から除外すると、スクロール位置に依存した表示フィールドが将来追加された際に更新漏れが起きる → `ThreadTabUiStateSourceKey` に含める項目を明示し、表示内容に関係するタブ項目を追加した場合は比較キーも更新する。
- [Risk] ユーザー操作検知を `DragInteraction` のみにすると fling や programmatic scroll の扱いが曖昧になる → ユーザー drag 中は停止し、drag 終了後の fling は短い待機後に再開する挙動として定義する。
- [Risk] 自動スクロール再開が早すぎるとユーザー操作を奪う → `DragInteraction.Stop` / `Cancel` 後に小さな猶予を置く、または fling 完了を待つ設計を検討する。
- [Risk] `distinctUntilChangedBy` により必要な tab 変更が抑制される → boardId、title、レス数、新着境界、bookmark 色、pin など表示に関係する項目を比較キーへ含める。

## Migration Plan

1. `ThreadRouteViewModel` の `tabFlow` に、スクロール位置を除外した比較キーによる `distinctUntilChangedBy` を追加する。
2. `ObserveAutoScrollEffect` から `isScrollInProgress` key 依存を外す。
3. `DragInteraction` を用いたユーザー操作中の一時停止・操作終了後の再開を追加する。
4. 必要に応じて下端到達通知の過剰発火を抑える。
5. 自動スクロール中のスクロール位置保存、手動操作後の再開、タブ切替/画面離脱時の復元をテストする。

## Open Questions

- 手動スクロール後の再開猶予を固定時間にするか、fling 完了検知に寄せるか。
- 下端到達通知の throttle は ViewModel 側の既存 10 秒制御だけで十分か、UI 側でも edge-trigger を持つべきか。
