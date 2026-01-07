# Change: PostUiStateの廃止とPostDialogStateへの統合

## Why
- 投稿ダイアログの状態がPostUiStateとPostDialogStateに分散しており、理解と保守が難しい。
- Board/Threadで投稿状態の持ち方が異なるため、共通化の意図が伝わりにくい。

## What Changes
- PostUiStateを廃止し、PostDialogStateに統合する。
- BoardUiState/ThreadUiStateがPostDialogStateを保持する（Option B）。
- namePlaceholderをPostDialogStateへ追加する（Option A）。
- PostDialogはPostDialogStateを受け取る形に統一する。
- PostDialogStateAdapterはUiState内のpostDialogStateを読み書きする形に整理する。
- PostFormStateを共通領域へ移し、Board/Threadの依存を揃える。

## Impact
- Affected specs: manage-post-dialog（新規）
- Affected code: ui/common/PostDialog.kt, ui/common/postdialog/*, ui/board/state/BoardUiState.kt, ui/thread/state/ThreadUiState.kt, ui/thread/state/PostUiState.kt, ui/board/viewmodel/BoardViewModel.kt, ui/thread/viewmodel/ThreadViewModel.kt
