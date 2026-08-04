## Why

タブが増えた際、利用者は未固定タブを1件ずつ閉じる必要があり、固定タブを残して一覧を整理する操作に手間がかかる。タブ一覧の表示中ページを対象とする一括操作を追加し、板タブとスレッドタブの境界および固定状態を保ったまま整理できるようにする。

## What Changes

- タブ一覧上部の検索ボタン右側に、アクセシブルな「その他」ボタンを追加する。
- `AnchoredTabActionMenu` を用いて「全てのタブを閉じる」項目だけを持つポップアップメニューを表示する。
- メニュー実行時、現在表示中の `TabPage` に属する未固定タブをすべて閉じる。
- 板ページでは未固定の板タブだけを、スレッドページでは未固定のスレッドタブだけを対象とし、反対ページのタブと固定タブは変更しない。
- 一括操作を既存の対象行削除・選択補正・セッション破棄・永続化経路へ接続し、通常操作向けでない全件置換APIは使用しない。
- メニュー状態、削除境界、固定タブ除外、選択状態、永続化およびアクセシビリティの回帰テストを追加する。

## Capabilities

### New Capabilities

なし。

### Modified Capabilities

- `tablist-ui`: タブ一覧のページ単位一括クローズ操作、固定タブ除外、ページ境界、メニュー表示およびアクセシビリティ要件を追加する。

## Impact

- UI: `TabListSearchControls.kt`、`AnchoredTabActionMenu.kt`、`TabScreenContent.kt`、文字列リソース
- UI状態とイベント: `TabListUiState.kt`、`TabListViewModel.kt`
- タブセッション: `TabSessionStore.kt` と既存の板・スレッドCoordinatorの対象行クローズ経路
- テスト: ViewModel、SessionStore、画面コールバックまたはCompose UI、既存Coordinatorの一括要求回帰
- 外部API、データベーススキーマ、依存ライブラリの変更はない。
