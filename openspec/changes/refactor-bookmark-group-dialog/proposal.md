# Change: ブックマークグループ編集の共通化再設計

## Why
板/スレッド向けと一覧向けでグループ編集・ダイアログ制御が重複しており、委譲による責務分離が難しい。共通化された状態と操作を用意し、BookmarkViewModel を一覧操作に集中させたい。

## What Changes
- 共有の GroupDialogState と GroupDialogController を導入する。
- SingleBookmarkState と BookmarkUiState に GroupDialogState を内包させる。
- BoardBookmarkViewModel / ThreadBookmarkViewModel / BookmarkViewModel が GroupDialogController を合成して利用する。
- BookmarkViewModel は一覧の選択・一括適用・解除に責務を寄せ、グループ編集は委譲する。
- 既存の BookmarkGroupEditor を置き換える（または最小限に退役する）。

## Impact
- Affected specs: manage-bookmark-groups
- Affected code: ui/common/bookmark/*, ui/bookmarklist/BookmarkListViewModel, ui/bookmarklist/BookmarkListScaffold
