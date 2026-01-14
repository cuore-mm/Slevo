# Change: サムネイルを常に中央に表示する

## Why
画像枚数が少ない場合に選択中サムネイルが中央に来ないため、閲覧時の視線誘導が弱くなる。

## What Changes
- 選択中サムネイルが常に中央に表示されるようにする（先頭/末尾でも空白を許容）
- 画像枚数が少ない場合も同様に中央寄せを維持する

## Impact
- Affected specs: image-viewer
- Affected code: app/src/main/java/com/websarva/wings/android/slevo/ui/viewer/ImageViewerScreen.kt
