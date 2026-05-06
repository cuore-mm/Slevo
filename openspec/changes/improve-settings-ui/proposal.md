## Why

設定画面の全般・スレッド表示設定は、項目ごとに表示部品や選択 UI が異なっており、設定画面内の操作体験が揃っていません。Issue #475 では、テーマ設定を 3 択化しつつ、既存の設定カードとアンカー付きオーバーレイメニューへ統一することで、設定項目の見た目と選択操作を一貫させます。

## What Changes

- 全般設定のテーマ選択を「ライト」「ダーク」「システムテーマに従う」の 3 択に変更し、既定値を「システムテーマに従う」にする。
- 全般設定とスレッド表示設定の項目を `SettingsCardWithListItems` ベースのカード表示へ統一する。
- テーマ設定とデフォルト並び順設定の選択ポップアップを `AnchoredOverlayMenu` で表示する。
- 既存のスレッド表示設定（並び順、ミニマップ付きスクロールバー表示）の保存・反映契約を維持する。

## Capabilities

### New Capabilities
- `settings-ui-preferences`: 設定画面のテーマ選択、スレッド表示設定、カード表示、アンカー付き選択メニューの振る舞いを扱う。

### Modified Capabilities
- `unified-icon-and-overlay-menu`: 既存の `AnchoredOverlayMenu` 利用範囲に、全般設定のテーマ選択とスレッド表示設定の並び順選択を追加する。

## Impact

- 影響コード: `ui/settings/*`, `ui/theme/*`, `data/repository/SettingsRepository.kt`, `data/datasource/local/*`, 必要に応じてアプリルートのテーマ適用箇所。
- 影響データ: DataStore のテーマ設定は boolean から 3 状態を表現できる保存形式へ拡張する。既存のダークモード設定があるユーザーは、既存値と同等のライト/ダーク選択として扱う。
- 影響 UI: 全般設定・スレッド設定画面の項目レイアウトと選択ポップアップ表示。
- 追加外部依存は不要。
