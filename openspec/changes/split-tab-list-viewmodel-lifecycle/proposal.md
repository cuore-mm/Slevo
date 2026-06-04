## Why

現在の `TabsViewModel` は、アプリ全体で共有するタブセッション管理と、タブ一覧画面だけで必要な検索・選択・BottomSheet などの一時 UI 状態を同時に保持している。さらに、画面に直接紐づかないタブセッション管理まで ViewModel として表現しているため、ViewModel の責務境界と画面ライフサイクルとの対応が分かりにくい。

## What Changes

- タブ一覧画面専用の ViewModel を導入し、検索状態、長押し選択状態、削除中状態、詳細 BottomSheet 表示状態などの画面固有 UI 状態を画面スコープで管理する。
- 開いている板/スレッドタブ、タブ永続化、ページ状態、スレッドタブ更新、子 ViewModel キャッシュなどのアプリ内タブセッション管理を、ViewModel ではない `TabSessionController` / `TabSessionStore` 相当のコンポーネントへ分離する。
- タブ一覧画面は、タブセッション管理コンポーネントの状態を参照しつつ、画面専用 ViewModel がフィルタリングや一時 UI 操作を組み立てる構成へ移行する。
- ユーザー向けのタブ一覧挙動、表示内容、操作結果は維持する。
- **BREAKING**: なし。内部構造の責務分離であり、ユーザー向け API や画面仕様は変更しない。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `tablist-ui`: タブ一覧画面の画面固有 UI 状態を画面ライフサイクルに紐付け、画面に紐づかないタブセッション管理を非 ViewModel コンポーネントへ分離しつつ、既存の表示・操作仕様を維持する責務分離要件を追加する。

## Impact

- 影響範囲:
  - `ui/tabs/TabsViewModel.kt`
  - `ui/tabs/TabsUiState.kt`
  - タブ一覧画面の Composable 群（`TabsScaffold`, `TabScreenContent`, `TabsPagerContent`, `OpenBoardsList`, `OpenThreadsList`, `TabsBottomSheet` など）
  - ナビゲーション/Activity からの ViewModel / タブセッションコンポーネント受け渡し箇所
  - `BoardTabsCoordinator`, `ThreadTabsCoordinator`, `TabViewModelRegistry` の利用境界
- 追加が想定される要素:
  - タブ一覧画面専用 ViewModel（例: `TabListViewModel`）
  - タブ一覧画面専用 UiState（例: `TabListUiState`）
  - タブセッション管理コンポーネント（例: `TabSessionController`, `TabSessionStore`）
- 依存ライブラリや永続化スキーマの変更は想定しない。
