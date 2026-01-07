# Change: 投稿履歴処理をPostDialogControllerへ移管

## Why
- 投稿履歴の監視/更新ロジックがBaseViewModelに残っており、投稿機能と責務が分散している。
- Board/Threadの投稿フローをPostDialogControllerに集約したため、履歴も同じ境界に寄せた方が理解しやすい。

## What Changes
- BaseViewModelから投稿履歴処理（prepare/refresh/delete）を削除する。
- PostDialogControllerが履歴の監視・候補更新・削除を直接管理する。
- Board/Thread ViewModelは履歴処理の委譲をやめ、PostDialogControllerを直接利用する。

## Impact
- Affected specs: manage-post-dialog（新規）
- Affected code: ui/bbsroute/BaseViewModel.kt, ui/common/postdialog/PostDialogController.kt, ui/board/viewmodel/BoardViewModel.kt, ui/thread/viewmodel/ThreadViewModel.kt
