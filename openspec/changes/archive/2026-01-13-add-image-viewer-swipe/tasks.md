## 1. Implementation
- [x] 1.1 ImageViewer のナビゲーション引数に画像URL一覧と初期インデックスを追加し、デコード処理を更新する
- [x] 1.2 ImageViewerScreen に HorizontalPager を導入し、ページごとに ZoomableAsyncImage を表示する
- [x] 1.3 画像サムネイルのタップ時に、同一レス内の画像URL一覧と選択位置を渡す（ThreadScreen/PostItemMedia/ReplyPopup など）
- [x] 1.4 共有トランジションとバーの表示切替など既存の表示挙動を維持する調整を行う
- [x] 1.5 同一URLが複数ある場合でもタップ位置を初期表示に反映する
- [x] 1.6 PostDialog 経由でもレス内画像のスワイプ切替を有効にする
- [x] 1.7 画像スワイプ時に拡大率をリセットする

## 2. Validation
- [x] 2.1 画像が1件/複数件のレスでスワイプ切替が同一レス内に限定されることを確認する
- [x] 2.2 build と unit tests を実行し、両方成功させる
