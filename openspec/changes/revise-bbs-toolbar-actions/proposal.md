## Why

板・スレ画面の下部ツールバーでは、頻繁に使うタブ一覧への導線がタイトルカードと分離され、限られたアクション行の領域も消費している。タイトルカードをタブ操作の入口として統一し、板画面にも主要画面へ移動できる「その他」メニューを揃える。

## What Changes

- **BREAKING**: 板・スレ画面の展開時アクション行および縮退時タイトル行から「タブ一覧」ボタンを削除する。
- **BREAKING**: 板・スレ画面のタイトルカードの通常タップを、情報シート表示からタブ一覧表示へ変更する。
- 板・スレ画面のタイトルカードの長押しで、従来の通常タップと同じ板情報／スレ情報シートを表示する。
- タイトルカード内のブックマーク操作、更新操作、ロード進捗、および板／スレ画面種別ボタンの挙動は維持する。
- 板画面のアクション行に「その他」ボタンを追加し、「ブックマーク」「板一覧」「履歴」「設定」への遷移を提供する。スレ画面だけが持つ「表示設定」は板画面のメニューには表示しない。
- タイトルカードに通常クリックと長押しのアクセシビリティ操作・ラベルを提供し、アイコン内操作と競合させない。

## Capabilities

### New Capabilities

- `bbs-toolbar-actions`: 板・スレ画面のツールバーアクション、タイトルカードのタブ一覧／詳細操作、および画面別「その他」メニュー項目を規定する。

### Modified Capabilities

- `board-thread-info-sheet`: 板画面タイトルカードから板情報シートを開く操作を、通常タップから長押しへ変更し、スレ画面タイトルカードにも同じ長押し契約を明示する。

## Impact

- 共通ツールバー UI: `ui/common/TabToolBar.kt`
- 板／スレ固有ツールバー: `ui/board/components/BoardToolBar.kt`、`ui/thread/components/ThreadToolBar.kt`
- 画面配線とナビゲーション: `ui/board/BoardScaffold.kt`、`ui/thread/ThreadScaffold.kt`、`ui/bbsroute/BbsRouteScaffold.kt`
- 板／スレのセッション状態・UiState・RouteViewModel、および「その他」メニュー UI
- Compose UI テスト、板／スレ RouteViewModel 単体テスト、既存ツールバー測定・Shared Transition テスト
- 外部 API、データモデル、永続化形式、Navigation route の公開引数、依存ライブラリには変更なし
