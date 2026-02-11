# Change: レス内画像ビューアのスワイプ切替

## Why
画像ビューアが単一画像の表示に留まっており、同一レス内の画像を連続で確認する際に操作が煩雑なため。

## What Changes
- ImageViewerScreen で HorizontalPager を使い、同一レス内の画像を左右スワイプで切り替えられるようにする
- 画像ビューアを開いたとき、タップした画像を初期表示として選択する
- 切り替え対象は「同じレス本文から抽出した画像URL」に限定する

## Impact
- Affected specs: image-viewer（新規）
- Affected code: ImageViewerScreen、AppRoute/ImageViewer のナビゲーション引数、画像タップ時のコールバック（ThreadScreen/PostItemMedia/ReplyPopup など）
