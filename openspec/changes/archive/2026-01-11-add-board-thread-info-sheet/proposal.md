# Change: 板画面のスレッド長押しでThreadInfoBottomSheetを表示

## Why
板一覧からスレッドの詳細操作に素早くアクセスできるようにするため。

## What Changes
- 板画面のスレッド項目に長押し操作を追加し、ThreadInfoBottomSheetを表示する
- 選択中のスレッド情報をUI状態で管理し、シートの表示/非表示を制御する
- 既存のタップ遷移は維持する

## Impact
- Affected specs: board-thread-info-sheet
- Affected code: BoardScreen/ThreadCard, BoardScaffold, BoardUiState, BoardViewModel, ThreadInfoBottomSheet
