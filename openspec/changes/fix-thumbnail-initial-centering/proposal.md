# Change: 画像ビューア初期表示のサムネイル中央寄せを保証する

## Why
画面遷移直後は選択サムネイルが中央に来ず、見た目が不安定になる。1回画像を切り替えると中央に寄るため、初期表示のみ挙動が不足している。

## What Changes
- 画像ビューア初期表示時にも選択サムネイルを中央に配置する
- サムネイルバーの初期レイアウト完了後に中央寄せ処理を実行する

## Impact
- Affected specs: image-viewer
- Affected code: app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt
