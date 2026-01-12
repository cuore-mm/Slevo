# Change: レス内画像の一括保存メニュー追加

## Why
同一レスに複数の画像が含まれる場合、1枚ずつ保存する手間が大きいため、まとめて保存できる導線を提供する。

## What Changes
- 画像メニューに「レス内の画像をすべて保存」を追加し、選択したレス本文の画像URLをまとめて保存する。
- 画像URLは本文から抽出し、同一URLは1回のみ保存する。
- レス内画像が2件以上のときのみメニュー項目を表示する。
- 一括保存の結果を成功/失敗件数で通知する。
- 既存の単一保存・共有などの挙動は維持する。

## Impact
- Affected specs: thread-image-menu (new)
- Affected code: ui/thread/sheet/ImageMenuSheet.kt, ui/thread/screen/ThreadScaffold.kt, ui/thread/res/PostItemMedia.kt, ui/thread/res/PostItem.kt, ui/thread/screen/ThreadScreen.kt, ui/thread/viewmodel/ThreadViewModel.kt, ui/common/ImageThumbnailGrid.kt, ui/util/LinkUtils.kt, res/values/strings.xml
