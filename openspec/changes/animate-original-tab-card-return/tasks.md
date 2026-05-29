## 1. 現状整理

- [x] 1.1 `TabScreenContent` / `TabLongPressOverlayLayer` の floating card enter/exit 処理を確認する
- [x] 1.2 `TabListCard` の `isHiddenForSelection`、alpha、scale、click/long-click 条件を確認する
- [x] 1.3 `OpenBoardsList` / `OpenThreadsList` から元カードへ渡している選択状態と退場中状態を確認する

## 2. 元カード側アニメーション

- [x] 2.1 `TabListCard` に長押し選択中スケールを適用し、透明化中も scale が選択中値へ進むようにする
- [x] 2.2 長押し選択解除時に、元カードの alpha を表示状態へ戻しつつ scale を選択中値から通常値へ戻す
- [x] 2.3 元カードのアニメーションが通常カード、板タブ、スレッドタブ、新着バッジ表示に影響しないことを確認する

## 3. floating card exit 整理

- [x] 3.1 `TabLongPressOverlayLayer` の解除時処理から、floating card の戻り縮小を主表示として扱う状態を取り除く
- [x] 3.2 解除開始時に floating card を即時破棄するか短い fade out にし、元カードとの二重表示を避ける
- [x] 3.3 ページ切替、詳細表示、固定切替、閉じる操作、overlay タップ、戻るキーの各解除経路で同じ復帰挙動になることを確認する

## 4. エッジケース対応

- [x] 4.1 解除時に対象カードが一覧に存在しない場合、元カード復帰アニメーションを省略して安全に選択状態を解除する
- [x] 4.2 削除アニメーション中カードでは長押し選択を開始しない既存条件を維持する
- [x] 4.3 LazyList の再配置やスクロール中でも、解除時に不自然なちらつきや二重表示が起きないことを確認する

## 5. 検証

- [ ] 5.1 板タブとスレッドタブで、長押し開始時に選択カードが前面で拡大表示されることを確認する
- [ ] 5.2 板タブとスレッドタブで、選択解除時に元カードが拡大状態から通常サイズへ戻ることを確認する
- [ ] 5.3 長押し中の dim overlay、下部操作群、アクションメニュー表示が既存挙動を維持することを確認する
- [ ] 5.4 Android CI workflow で build と unit test を確認する
