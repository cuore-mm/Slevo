## 1. 共通ユーティリティの整備

- [ ] 1.1 `ToBottom` 用の viewport 更新待ちと末尾ターゲット算出を画面共通ユーティリティへ移設する
- [ ] 1.2 `GestureHint.Invalid` の遅延リセット effect を画面共通コンポーネントとして用意する
- [ ] 1.3 画像ビューア遷移用の route ビルダー（URL encode + index clamp）を共通化する

## 2. Screen レイヤーの共通化適用

- [ ] 2.1 `ThreadScreen` の `ToTop` / `ToBottom` 実行を共通 executor 呼び出しへ置き換える
- [ ] 2.2 `BoardScreen` の `ToTop` / `ToBottom` 実行を共通 executor 呼び出しへ置き換える
- [ ] 2.3 `ThreadScreen` と `BoardScreen` の invalid ヒント復帰処理を共通 effect 利用へ統一する

## 3. Scaffold レイヤーの共通化適用

- [ ] 3.1 `ThreadScaffold` の `onGestureAction` 共通部分を共通ディスパッチ関数へ置き換える
- [ ] 3.2 `BoardScaffold` の `onGestureAction` 共通部分を共通ディスパッチ関数へ置き換える
- [ ] 3.3 `ThreadScreen` / `ThreadScaffold` / `BoardScaffold` の画像ビューア遷移を共通 route ビルダー利用へ置き換える

## 4. 挙動確認

- [ ] 4.1 `ThreadScreen` と `BoardScreen` の `ToTop` / `ToBottom` 操作が従来どおり動作することを確認する
- [ ] 4.2 GestureHint invalid 表示後の復帰タイミングが両画面で同一であることを確認する
- [ ] 4.3 build と unit test を実行し、共通化による回帰がないことを確認する
