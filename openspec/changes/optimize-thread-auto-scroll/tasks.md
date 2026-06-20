## 1. 再合成トリガーの整理

- [ ] 1.1 `ThreadRouteViewModel.createUiStateFlow(tabKey)` の `tabFlow` が `firstVisibleItemIndex` / `firstVisibleItemScrollOffset` だけの変更では emit しない比較キーを導入する
- [ ] 1.2 比較キーに、表示内容へ影響する `ThreadTabInfo` 項目（thread id、title、board 情報、レス数、新着/既読境界、bookmark 色、pin）を含める
- [ ] 1.3 スクロール位置保存だけでは `ThreadVisiblePostsUseCase` による visible rows 再計算が走らないことを単体テストまたは flow テストで確認する
- [ ] 1.4 title、boardId、レス数、新着境界など表示内容に関係するタブ情報変更では `ThreadUiState` が更新されることを確認する

## 2. 自動スクロール駆動の安定化

- [ ] 2.1 `ObserveAutoScrollEffect` の `LaunchedEffect` key から `listState.isScrollInProgress` 依存を除去し、programmatic scroll 自身で Effect が再起動しないようにする
- [ ] 2.2 ユーザー手動 drag を `LazyListState.interactionSource.interactions` で検知し、自動スクロール中のユーザー操作を一時停止状態として扱う
- [ ] 2.3 `DragInteraction.Stop` / `Cancel` 後に自動スクロールが再開するようにし、`isAutoScroll` 自体は解除しない
- [ ] 2.4 手動 scroll / fling と自動スクロール再開の境界で、ユーザー操作を奪わない猶予または再開条件を定義する
- [ ] 2.5 自動スクロール中に下端へ到達したとき、`onAutoScrollBottom` が過剰に連続発火しないか確認し、必要なら UI 側で edge-trigger または throttle を追加する

## 3. スクロール位置保存と復元の維持

- [ ] 3.1 自動スクロール中もタブ切替時の最終スクロール位置が保存されることを確認する
- [ ] 3.2 自動スクロール中にスレッド画面を離れた場合、離脱時点のスクロール位置が保存されることを確認する
- [ ] 3.3 `ScrollPositionPersistence` の周期保存・非アクティブ化時保存・破棄時保存の既存テストが維持されることを確認する

## 4. 性能回帰確認

- [ ] 4.1 自動スクロール開始直後と長時間継続後で、スクロール位置保存による `ThreadUiState` 再合成回数が増え続けないことを確認する
- [ ] 4.2 ミニマップ表示 ON/OFF の両方で自動スクロールの滑らかさを確認する
- [ ] 4.3 自動スクロール中の手動スクロール後に、自動スクロールが停止状態で残らず再開することを確認する
- [ ] 4.4 CI で unit test / build が通ることを確認する
