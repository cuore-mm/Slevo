# Change: PostDialog画像アップロードの共通化とURL挿入の集約

## Why
- Board/Threadで画像アップロード後の本文更新処理が重複している。
- 投稿本文へのURL挿入はPostDialogControllerに集約した方が一貫性が高い。

## What Changes
- 画像アップロード処理を共通のUploaderへ整理する。
- PostDialogControllerに画像URLの追記操作（append）を追加する。
- Board/ThreadはUploaderの成功結果をControllerへ委譲する。

## Impact
- Affected specs: manage-post-dialog
- Affected code: ui/board/viewmodel/BoardImageUploader.kt, ui/thread/viewmodel/ThreadViewModel.kt, ui/common/postdialog/PostDialogController.kt, ui/board/viewmodel/BoardViewModel.kt
