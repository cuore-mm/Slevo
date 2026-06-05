## Why

`TabSessionStore` と `TabListViewModel` への責務分離後も、既存互換ラッパーとして `TabsViewModel` と `TabsUiState` が残っている。これによりタブセッション状態の正本が分かりにくく、画面固有状態を再び Activity スコープへ戻してしまう余地があるため、最終的な削除計画を明確にする。

## What Changes

- `TabsViewModel` をタブセッション API の互換ラッパーとして使う構造を廃止し、各画面が必要なセッション状態・操作を `TabSessionStore` から取得する構造へ移行する。
- `TabsUiState` を削除し、タブセッション状態は `TabSessionStore` の個別 `StateFlow` または必要最小限の画面別集約状態で扱う。
- `BbsRouteScaffold` が `TabsUiState.isUrlValidating` に依存している状態を解消し、URL入力ダイアログの検証状態を画面ローカルまたは専用 ViewModel の状態として管理する。
- タブ一覧画面は引き続き `TabListViewModel` で検索、長押し選択、削除待ち、BottomSheet、URL入力ダイアログ状態を管理する。
- 既存のユーザー向け挙動（タブ追加/削除/固定、板/スレ遷移、Deep Link、URL入力、タブ更新）は維持する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `tablist-ui`: タブ一覧画面が `TabsViewModel` / `TabsUiState` に依存せず、セッション状態と画面固有状態を分離して収集・受け渡す要件へ更新する。

## Impact

- 影響範囲:
  - `ui/tabs/TabsViewModel.kt`
  - `ui/tabs/TabsUiState.kt`
  - `ui/tabs/store/TabSessionStore.kt`
  - `ui/tabs/TabListViewModel.kt`
  - `ui/tabs/TabsScaffold.kt`
  - `ui/tabs/TabsBottomSheet.kt`
  - `ui/tabs/screen/*`
  - `ui/board/screen/BoardScaffold.kt`
  - `ui/thread/screen/ThreadScaffold.kt`
  - `ui/bbsroute/BbsRouteScaffold.kt`
  - `ui/navigation/*`
  - `MainActivity.kt`
- 既存の Activity スコープ `TabsViewModel` 注入経路を段階的に除去する。
- Hilt 注入先が増える可能性があるため、`TabSessionStore` を Compose / ViewModel から取得する境界を設計で明確化する。
- 既存テストは `TabsViewModel` 前提から `TabSessionStore` / `TabListViewModel` 前提へ更新する。
