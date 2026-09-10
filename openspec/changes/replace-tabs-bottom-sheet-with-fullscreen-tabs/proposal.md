## Why

Board / Thread 画面のタブ一覧は BottomSheet と全画面 destination の二つの表示経路を持ち、同じ一覧 UI に対して閉じ方と Navigation 処理が分岐している。`AppRoute.Tabs` に統一し、既存の Board / Thread の push・pop・replace 規則と `TabSessionStore` の選択状態を再利用しながら、タブ一覧から戻った後もそれ以前の Navigation 履歴を維持する。

## What Changes

- `TabsBottomSheet` を廃止し、Board / Thread のタブ一覧操作を `AppRoute.Tabs` への全画面遷移へ置き換える。
- Board / Thread から開いた Tabs は、遷移元と同じ種別の一覧ページを初期表示する。
- Tabs の直前の Board / Thread destination を選択元として扱い、選択完了時に Tabs を back stack から除去して既存のタブ選択 Navigation 規則へ委譲する。
- 同種タブの選択では `TabSessionStore` の選択タブを更新して元の destination へ戻り、同種 destination を追加しない。
- Thread から Board タブを選んだ場合は、直前に Board destination があればそこまで戻り、なければ Thread destination を Board destination に置き換える。
- Board から Thread タブを選んだ場合は、Tabs を除去して Thread destination を積む。
- ルートの Tabs からの選択と、Bookmark など Board / Thread より前の履歴は従来どおり維持する。
- カード選択と URL 入力の登録・選択・Navigation 順序を統一し、登録成功後だけ Tabs を閉じる。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `separated-board-thread-tab-navigation`: Board / Thread から全画面 Tabs を経由した同種・別種タブ選択時の back stack 規則を明確化する。
- `tablist-ui`: Board / Thread から開くタブ一覧を全画面表示へ統一し、遷移元種別に応じた初期ページを規定する。

## Impact

- Navigation: `AppNavGraph.kt`、`NavigationExtensions.kt`、`AppRoute.Tabs` の呼び出し箇所と back stack テスト。
- Board / Thread 共通 UI: `BbsRouteScaffold.kt` のタブ一覧表示状態とコールバック。
- タブ一覧 UI: `TabsScaffold.kt`、`TabScreenContent.kt`、`TabsPagerContent.kt`、`OpenBoardsList.kt`、`OpenThreadsList.kt`。
- 削除対象: `TabsBottomSheet.kt` と BottomSheet 固有の state・dismiss・検索状態リセット処理。
- `TabSessionStore` の stable key による選択状態、Board / Thread の route 正規化、既存 destination の描画責務は維持する。
- 新規依存関係、永続データ形式、外部 API の変更はない。
