## Why

現在のタブ一覧は永続的な表示順を持つものの、利用者がその順序を変更する操作を提供していない。既存の長押しアクションメニュー、スワイプ削除、縦スクロールを維持しながら、カードを長押しした同じ指で直感的に並び替えられるようにする。

## What Changes

- 板タブとスレッドタブの縦型カード一覧で、カード本体を長押しして上下へドラッグ並び替えできるようにする。
- 長押し成立時は従来のアクションメニューを非操作プレビューとして即時表示し、指を離せば操作可能なメニューへ、追加移動すればメニューを閉じて並び替えへ遷移する。
- 通常タップ、長押し前の横スワイプ削除、縦スクロール、closeボタンを既存どおり利用できるよう、ジェスチャーの優先順位と開始領域を定義する。
- ドラッグ中はstable keyの順序だけを一時保持し、正常終了時に既存Coordinatorのpending projectionへ引き継ぐ。キャンセル時は開始前の順序へ戻す。
- RoomではタブEntity全体を置換せず、`sortOrder`だけを1トランザクションで再採番して永続化する。
- `sh.calvin.reorderable:reorderable:3.1.0`を導入し、位置判定、移動アニメーション、エッジ自動スクロールを委譲する。
- 並び替え中のドラッグ対象カードは半透明で表示し、開始・終了時の透明度を短時間で補間する。
- TalkBack等からドラッグせずに順序を変更できる「上へ移動」「下へ移動」アクションを追加する。

## Capabilities

### New Capabilities

- `tab-reordering`: 長押しドラッグによるタブ順序変更、キャンセル、永続化、同時更新、アクセシビリティ代替操作を定義する。

### Modified Capabilities

- `tablist-ui`: 既存の長押しメニュー、スワイプ削除、縦スクロール、closeボタンと並び替えジェスチャーの共存要件を追加する。
- `tab-controller-state-machine`: 並び替えを既存のpending projectionとRoom canonical確認へ統合し、失敗時に正しい順序へ収束させる要件を追加する。
- `tab-viewmodel-architecture`: ドラッグ中に限ってstable keyの一時順序を画面状態として保持し、ドロップ後はCoordinatorへ所有権を戻す境界を追加する。

## Impact

- UI: `TabListCard.kt`、`RemovableTabList.kt`、`OpenBoardsList.kt`、`OpenThreadsList.kt`、`TabScreenContent.kt`、`AnchoredOverlayMenu.kt`、`AnchoredTabActionMenu.kt`
- 状態管理: `TabListUiState.kt`、`TabListViewModel.kt`、`TabSessionStore.kt`
- セッション制御: `BoardTabsCoordinator.kt`、`ThreadTabsCoordinator.kt`、`TabProjectionPrimitives.kt`
- 永続化: `TabsRepository.kt`、`OpenBoardTabDao.kt`、`OpenThreadTabDao.kt`。既存`sortOrder`を利用するためRoom schema migrationは行わない。
- 依存関係: Gradle version catalogとapp moduleへCalvin-LL Reorderable 3.1.0を追加する。
- テスト: ジェスチャー競合のCompose UIテスト、ViewModel/Coordinatorのunit test、Room再採番と1,252件規模のinstrumented testを追加する。
