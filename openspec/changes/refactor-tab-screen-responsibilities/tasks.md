## 1. URL入力処理の責務分離と安全化

- [ ] 1.1 `TabScreenContent` 内の URL 入力処理分岐を整理し、板 URL / スレ URL / itest 板 URL の各非同期処理で検証終了処理が必ず実行されるようにする
- [ ] 1.2 URL 入力処理の結果を表す typed result（板遷移、スレ遷移、エラー）を定義する
- [ ] 1.3 URL 判定、ホスト解決、route 正規化を `TabsViewModel` または専用 handler へ移し、Composable から長い inline 分岐を取り除く
- [ ] 1.4 `TabScreenContent` は typed result に基づいて既存の `navigateToBoard` / `navigateToThread` と dialog / drawer close を実行する

## 2. BottomSheet と長押し overlay の分割

- [ ] 2.1 `TabDetailBottomSheets` を切り出し、`detailBoardTab` / `detailThreadTab` と dismiss callback から BottomSheet を描画する
- [ ] 2.2 `TabLongPressOverlayLayer` を切り出し、dim overlay、floating card、action menu、BackHandler をまとめる
- [ ] 2.3 floating card の enter / exit アニメーション仕様、scale 値、duration、表示条件を分割前と同じに保つ
- [ ] 2.4 board/thread floating card の共通配置処理を整理し、座標変換と `graphicsLayer` scale 適用の重複を減らす

## 3. UiState収集と子Composableの責務整理

- [ ] 3.1 `TabScreenContent` の `uiState` 収集を `collectAsStateWithLifecycle()` に変更する
- [ ] 3.2 `TabsPagerContent` が `tabsViewModel.uiState` を再収集しないようにし、上位から必要な state と callback を受け取る
- [ ] 3.3 `OpenBoardsList` が production 経路で `tabsViewModel.uiState` を再収集しないようにし、選択中/退場中 state と callback を引数で受け取る
- [ ] 3.4 `OpenThreadsList` が production 経路で `tabsViewModel.uiState` を再収集しないようにし、選択中/退場中 state、新着レス数、callback を引数で受け取る
- [ ] 3.5 Preview は ViewModel nullable fallback ではなく、state と callback を渡せる下位 Composable で維持する

## 4. 回帰確認

- [ ] 4.1 長押しメニュー、dim overlay、floating card enter / exit、戻るキー解除、overlay タップ解除の挙動が分割前と同じであることを確認する
- [ ] 4.2 詳細ボタン押下後にアクションメニューが閉じ、対象タブの BottomSheet が表示され続けることを確認する
- [ ] 4.3 URL 入力で板 URL / スレ URL / itest 板 URL / 無効 URL の挙動が維持され、検証中状態が残らないことを確認する
- [ ] 4.4 Android CI workflow で build と unit test を確認する
