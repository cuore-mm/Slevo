## 1. AnchoredOverlayMenu API 拡張

- [x] 1.1 縦位置指定 enum に重ね表示用の選択肢（例: OverlapTop/OverlapBottom）を追加する
- [x] 1.2 縦位置計算に `offset.y` を反映する
- [x] 1.3 既存の `Above` / `Below` / `Auto` の計算式を整理し、gap と重ねの意図が明確になるよう調整する

## 2. 呼び出し元の更新

- [x] 2.1 `AnchoredSelectionMenu` の重ね表示意図を新しい縦位置指定で明示する
- [x] 2.2 `AnchoredTabActionMenu` の非重なり表示意図を新しい縦位置指定で明示する
- [x] 2.3 画像ビューアや設定画面など既存メニューの表示意図を確認し、必要に応じて縦位置指定を明示する

## 3. 検証

- [ ] 3.1 主要なメニュー（タブ、選択メニュー、設定、画像ビューア）で位置が意図通りになっていることを確認する
- [ ] 3.2 画面端に近いアンカーでも不自然な重なりが発生しないことを確認する
- [ ] 3.3 Android CI workflow で build と unit test を確認する
