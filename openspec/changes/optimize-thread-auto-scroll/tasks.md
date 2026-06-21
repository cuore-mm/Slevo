## 1. 再合成トリガーの整理

- [x] 1.1 `ThreadRouteViewModel.createUiStateFlow(tabKey)` の `tabFlow` が `firstVisibleItemIndex` / `firstVisibleItemScrollOffset` だけの変更では emit しない比較キーを導入する
- [x] 1.2 比較キーに、表示内容へ影響する `ThreadTabInfo` 項目（thread id、title、board 情報、レス数、新着/既読境界、bookmark 色、pin）を含める
- [x] 1.3 スクロール位置保存だけでは `ThreadVisiblePostsUseCase` による visible rows 再計算が走らないことを単体テストまたは flow テストで確認する
- [x] 1.4 title、boardId、レス数、新着境界など表示内容に関係するタブ情報変更では `ThreadUiState` が更新されることを確認する

## 2. 自動スクロール駆動の安定化

- [x] 2.1 `ObserveAutoScrollEffect` の `LaunchedEffect` key から `listState.isScrollInProgress` 依存を除去し、programmatic scroll 自身で Effect が再起動しないようにする
- [x] 2.2 ユーザー手動 drag を `LazyListState.interactionSource.interactions` で検知し、自動スクロール中のユーザー操作を一時停止状態として扱う
- [x] 2.3 `DragInteraction.Stop` / `Cancel` 後は fling 完了を検知し、100〜200ms 程度の猶予後に自動スクロールを再開する
- [x] 2.4 手動 scroll / fling の終了待ち中も `isAutoScroll` 自体は解除せず、ユーザー操作を奪わない再開条件をテストする
- [x] 2.5 下端到達通知の発火ポリシーは変更せず、既存の ViewModel 側 10 秒制御で自動更新頻度が維持されることを確認する

## 3. スクロール位置保存と復元の維持

- [x] 3.1 自動スクロール中もタブ切替時の最終スクロール位置が保存されることを確認する
- [x] 3.2 自動スクロール中にスレッド画面を離れた場合、離脱時点のスクロール位置が保存されることを確認する
- [x] 3.3 `ScrollPositionPersistence` の周期保存・非アクティブ化時保存・破棄時保存の既存テストが維持されることを確認する

## 4. 性能回帰確認

- [x] 4.1 自動スクロール開始直後と長時間継続後で、スクロール位置保存による `ThreadUiState` 再合成回数が増え続けないことを確認する
- [x] 4.2 ミニマップ表示 ON/OFF の両方で自動スクロールの滑らかさを確認する
- [x] 4.3 自動スクロール中の手動スクロール後に、自動スクロールが停止状態で残らず再開することを確認する
- [x] 4.4 CI で unit test / build が通ることを確認する

### Implementation notes

- `ThreadRouteViewModel` の `tabFlow` に `distinctUntilChangedBy { tab -> tab?.toUiStateSourceKey() }` を追加し、scroll position だけの `ThreadTabInfo` 更新では `ThreadUiState` を再合成しないようにした。
- `ObserveAutoScrollEffect` から loop 内の `isScrollInProgress` 監視と `isScrollInProgressWaiting` ヘルパーを削除し、programmatic scroll 自身の進行状態とユーザー操作判定が混ざらないようにした。
- `DragInteraction.Start / Stop / Cancel` は別 `LaunchedEffect` で監視し、`isPausedByUser` と `isResumePending` を更新して手動操作中の pause と操作終了後の resume 待ちを分離した。
- 再開待ち中は別 `LaunchedEffect` で `snapshotFlow { listState.isScrollInProgress }` が false になるのを待ち、その後 150ms の猶予を置いて自動スクロール loop を再開する。
- 自動スクロール loop 自体は `isAutoScroll` と `isPausedByUser` だけを key に持つ単純な構造へ戻し、ユーザー操作中の毎フレーム polling を避けた。
- 下端到達通知は既存どおり `ThreadRouteViewModel.onAutoScrollReachedBottom(tabKey)` の 10 秒制御に任せる形を維持し、UI 側の edge-trigger / throttle 変更は行わなかった。
- 既存 `ScrollPositionPersistence` 系の単測は変更していないため、周期保存・非アクティブ化時保存・破棄時保存の挙動は維持される。
- `ThreadRouteViewModel` 側の `distinctUntilChangedBy` 導入は `ThreadTabUiStateSourceKey` ヘルパーと `toUiStateSourceKey` 拡張関数を同ファイル内に private で追加し、表示内容へ影響するフィールドを網羅する。
