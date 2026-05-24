## Why

タブ一覧で開いているタブが増えると、重要なタブを残しながら不要なタブを整理する操作が難しくなる。タブの長押しアクションメニューと固定状態を追加し、詳細確認・固定切替・クローズを一覧上で扱えるようにする。

## What Changes

- 板タブ一覧・スレッドタブ一覧のカード長押しで、タブ専用の `AnchoredTabActionMenu` を表示する。
- 長押し選択中は、選択タブを少し拡大し、非選択タブを少し暗く表示する。
- 長押し選択中も下部操作群は変わらず表示し、既存のボタンとインジケータを維持する。
- タブ専用メニューに「詳細」「タブを固定 / タブの固定を解除」「タブを閉じる」を表示し、閉じる項目は破壊的操作として赤字で表示する。
- 「詳細」から板タブは `BoardInfoBottomSheet`、スレッドタブは `ThreadInfoBottomSheet` を表示する。
- タブ固定状態を永続化し、固定済みタブではカード右上の閉じるアイコンを表示専用の固定アイコンに置き換える。
- タブ一覧のソート順は固定状態で変えず、これまで通り既存のタブ順に従う。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `tablist-ui`: タブ一覧カードの長押しアクションメニュー、固定状態表示、詳細表示、選択中も維持する下部操作群表示を追加する。

## Impact

- UI: `TabScreenContent`, `TabsPagerContent`, `OpenBoardsList`, `OpenThreadsList`, `RemovableTabList`, `TabListCard`, 新規 `AnchoredTabActionMenu`
- 状態管理: `TabsUiState`, `TabsViewModel`, `BoardTabsCoordinator`, `ThreadTabsCoordinator`
- データ: `BoardTabInfo`, `ThreadTabInfo`, `OpenBoardTabEntity`, `OpenThreadTabEntity`, DAO, `TabsRepository`, Room migration
- 既存 UI 部品: `BoardInfoBottomSheet`, `ThreadInfoBottomSheet`, `AnchoredOverlayMenu`
- テスト: Coordinator、Repository 永続化、Room migration、タブ一覧 UI の長押しメニュー表示
