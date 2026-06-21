## Why

スレッド画面の自動スクロールが、RouteViewModel 化後に時間経過とともにかくつきやすくなっている。自動スクロール中のスクロール位置保存が `openThreadTabs` 更新を通じて `ThreadUiState` 再合成を連続発火させ、さらに手動スクロール後の自動再開挙動も不安定になっているため、表示合成とスクロール駆動の境界を整理する。

## What Changes

- 自動スクロール中のスクロール位置保存が、スレッド表示用 `UiState` の重い再合成を誘発しないようにする。
- `ThreadRouteViewModel.uiStateFor(tabKey)` のタブ情報入力では、スクロール位置だけの変更を表示合成トリガーから除外する。
- 自動スクロールのループは、プログラムスクロール自身の `isScrollInProgress` 変化で再起動しないようにする。
- ユーザーが自動スクロール中に手動スクロールした場合は、一時停止後に自動スクロールを再開できるよう、ユーザー操作と自動スクロールの状態を分けて扱う。
- 既存のスクロール位置保存、タブ切替時/画面離脱時の復元、下端到達時の自動更新は維持する。

## Capabilities

### New Capabilities

<!-- なし -->

### Modified Capabilities

- `thread-state-sync`: スレッドタブのスクロール位置保存と `ThreadUiState` 合成の分離、および自動スクロール中の保存・再開挙動を明確化する。

## Impact

- 影響範囲:
  - `ui/thread/screen/effects/ThreadScreenEffects.kt`
  - `ui/thread/viewmodel/ThreadRouteViewModel.kt`
  - `ui/bbsroute/ScrollPositionPersistence.kt` またはその呼び出し境界
  - `ui/bbsroute/BbsRouteScaffold.kt`
  - 関連するスレッド画面/RouteViewModel/スクロール位置保存テスト
- DB スキーマや外部 API の変更は不要。
- 主なリスクは、スクロール位置保存の間引きや合成入力分離により、タブ切替・画面離脱時の最終位置保存が抜けること。
