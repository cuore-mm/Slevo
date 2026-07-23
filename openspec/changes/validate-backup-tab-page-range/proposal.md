## Why

`BackupReader.parseTabs()` は `lastSelectedTabsPage` の負数だけを拒否するため、現在のタブ一覧 pager が持つ 2 page（index 0/1）の範囲外値を有効なバックアップとして受け入れ、無効な `initialPage` を DataStore に永続化できる。pager とバックアップ検証が別々の数値リテラルを持つ状態を解消し、将来 page 数を変更するときにも検証側の重複修正を不要にする。

## What Changes

- タブ一覧 pager とバックアップ検証が参照する canonical な page 定義を 1 箇所に設け、現在の有効 index を 0 と 1 のまま維持する。
- `datastore/tabs.json` の `lastSelectedTabsPage` が canonical な有効範囲外なら、既存の無効バックアップ経路で拒否し、preview/pending restore を作成しない。
- pager の `pageCount` とバックアップ検証の境界が同じ定義から導出されることをテストし、将来の page 追加時に両者がずれることを防ぐ。
- JSON field、整数による serialized format、DataStore key、既存 page 順序、UI の表示・文言・操作・accessibility は変更しない。text-setting の range validation は対象外とする。

## Capabilities

### New Capabilities

- `backup-tab-page-validation`: 復元バックアップ内の最終選択 tab page を、タブ一覧 pager と共有する canonical page 定義に照らして検証する契約。

### Modified Capabilities

- なし。

## Impact

- 対象は `app` module のタブ page 定義、`ui/tabs/screen/TabScreenContent.kt` の pager、`data/backup/restore/BackupReader.kt` の tabs JSON validation、および対応 unit test である。
- `BackupTabsJson.lastSelectedTabsPage: Int`、`last_selected_page`、backup format version 1 は維持し、Room/DataStore migration や stable ID 形式への移行は行わない。
- 既存の復元エラー UI と文言をそのまま利用するため、UI delta はない。
