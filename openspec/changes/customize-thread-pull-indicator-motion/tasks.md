## 1. インジケーター表示モード分離

- [ ] 1.1 Thread 画面の下端インジケーター描画を専用 Composable（例: `ThreadBottomRefreshIndicator`）へ切り出す
- [ ] 1.2 `refreshing`（`isLoading`）モードで既存どおり右回転 `ContainedLoadingIndicator` を表示する
- [ ] 1.3 `pulling`（`!isLoading && overscroll > 0`）モードで determinate `ContainedLoadingIndicator(progress)` を表示する

## 2. プル量連動モーション実装

- [ ] 2.1 `overscroll / refreshThresholdPx` からプル進捗を計算し、サイズ拡大率へ反映する
- [ ] 2.2 閾値到達で最大サイズになるクランプ処理を実装する
- [ ] 2.3 プル中は左回転、更新中は右回転となる回転ロジックを適用する

## 3. 回帰確認

- [ ] 3.1 閾値未満・閾値到達・閾値超過・更新中の表示挙動を手動確認する
- [ ] 3.2 下端更新の既存発火条件（arming/haptic/refresh）が不変であることを確認する
- [ ] 3.3 build と unit test を実行し、回帰がないことを確認する
