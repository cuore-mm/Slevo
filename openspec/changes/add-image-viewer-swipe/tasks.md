## 1. Implementation
- [ ] 1.1 ImageViewer のナビゲーション引数に画像URL一覧と初期インデックスを追加し、デコード処理を更新する
- [ ] 1.2 ImageViewerScreen に HorizontalPager を導入し、ページごとに ZoomableAsyncImage を表示する
- [ ] 1.3 画像サムネイルのタップ時に、同一レス内の画像URL一覧と選択位置を渡す（ThreadScreen/PostItemMedia/ReplyPopup など）
- [ ] 1.4 共有トランジションとバーの表示切替など既存の表示挙動を維持する調整を行う

## 2. Validation
- [ ] 2.1 画像が1件/複数件のレスでスワイプ切替が同一レス内に限定されることを確認する
- [ ] 2.2 build と unit tests を実行し、両方成功させる
