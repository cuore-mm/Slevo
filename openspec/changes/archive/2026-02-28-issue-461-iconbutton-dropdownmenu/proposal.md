## Why

アプリ内のアイコンボタンとドロップダウンメニューの実装が画面ごとに分散しており、押下フィードバックや長押しツールチップ、メニュー表示位置の体験が統一されていない。Issue #461 の対象画面に共通コンポーネントを適用し、UI 挙動と保守性を同時にそろえる必要がある。

## What Changes

- `FeedbackTooltipIconButton` を `ui/common` の再利用可能コンポーネントとして整理し、既存のローカル実装を置き換える。
- 板画面・スレッド画面のボトムバー内ボタン、検索バー内ボタン、設定画面トップバー内ボタンを `FeedbackTooltipIconButton` ベースへ統一する。
- ジェスチャー設定画面の `DropdownMenu` を `AnchoredOverlayMenu` に置き換え、アンカー基準の重ね表示に統一する。
- 既存機能契約（ボタン押下時のアクション、メニュー項目、dismiss 条件）は維持したまま、見た目と操作フィードバックのみを共通化する。

## Capabilities

### New Capabilities
- `unified-icon-and-overlay-menu`: アプリ内の対象画面で、アイコン操作フィードバックとアンカー型オーバーレイメニューを共通コンポーネントで提供する要件を定義する。

### Modified Capabilities
- なし

## Impact

- UI 共通部品: `app/src/main/java/com/websarva/wings/android/slevo/ui/common/*`
- 画面適用先: `board`, `thread`, `settings` 配下のトップバー/ボトムバー/検索バー関連 Composable
- 受け入れ確認: 対象画面でのボタン操作、長押しツールチップ、メニュー表示位置と dismiss 挙動
