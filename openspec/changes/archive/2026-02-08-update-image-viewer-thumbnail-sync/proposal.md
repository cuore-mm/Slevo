# Change: 画像ビューアのサムネイル中央選択の連動強化

## Why
サムネイルバーのスクロール位置と表示画像の選択が一致しないため、中央のサムネイルを基準にした直感的な閲覧ができない。

## What Changes
- サムネイルバーの中央に最も近いサムネイルを選択状態として扱い、スクロール中にリアルタイムで表示画像を切り替える。
- 画像のスワイプ/サムネイルタップによる表示切替に合わせて、サムネイルバーが該当サムネイルを中央に表示するよう追従する。

## Impact
- Affected specs: image-viewer
- Affected code: `app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt`
