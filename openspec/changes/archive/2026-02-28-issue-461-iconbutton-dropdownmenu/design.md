## Context

Issue #461 は、画面ごとにばらついているアイコンボタンとドロップダウンメニュー実装を共通化する変更である。現状は `FeedbackTooltipIconButton` が画像ビューア内のローカル関数に閉じており、他画面では `IconButton` と `DropdownMenu` が個別実装されているため、押下フィードバック、長押しツールチップ、メニュー表示位置が統一されていない。

この変更は `ui/common` と複数画面（board/thread/search/settings）にまたがる横断変更であり、既存アクション契約を維持したまま UI 部品の統一を行う。

## Goals / Non-Goals

**Goals:**
- `FeedbackTooltipIconButton` を `ui/common` で再利用可能にし、対象画面の `IconButton` を置き換える。
- 板画面・スレッド画面のボトムバー、検索バー、設定画面トップバーで押下フィードバックと長押しツールチップ体験を統一する。
- ジェスチャー設定画面のメニューを `AnchoredOverlayMenu` へ置換し、アンカー重ね表示へ統一する。
- 既存のクリック時動作、表示文言、dismiss 契約を保持する。

**Non-Goals:**
- 対象外画面（タブ一覧、ボトムシート、ダイアログ等）の一括置換。
- メニュー項目や画面遷移、ビジネスロジックの仕様変更。
- `AnchoredOverlayMenu` の見た目刷新や新アニメーション追加。

## Decisions

- 共通コンポーネント配置は `ui/common` とし、`FeedbackTooltipIconButton` は `tooltipText`、`onClick`、`icon`、必要に応じた表示制御引数（例: ツールチップ表示許可）を受け取る API で定義する。
  - 代替案: 既存 `IconButton` に拡張 Modifier を追加する。
  - 不採用理由: 長押しツールチップと押下スケールは内部状態管理が必要で、Modifier だけでは画面ごとの実装差分が残るため。

- 横断適用は「共通入口の優先置換」を採用する。具体的には `TabToolBar`、`SearchBottomBar`、`SlevoTopAppBar` を優先し、各画面側の重複置換を最小化する。
  - 代替案: 画面単位で個別に `IconButton` を置換する。
  - 不採用理由: 実装漏れが起きやすく、同一 UI パターンに差分が残るため。

- ジェスチャー設定メニューは `DropdownMenu` から `AnchoredOverlayMenu` へ切り替え、アンカー座標はボタン側で取得して渡す。
  - 代替案: `DropdownMenu` を維持して shape のみ合わせる。
  - 不採用理由: Issue 要件が `AnchoredOverlayMenu` への統一を明示しているため。

- 互換性維持のため、置換後も各ボタンの `contentDescription` と既存 callback は変更しない。

## Risks / Trade-offs

- [リスク] 共通化 API が不足すると、画面固有要件のために再ローカル化が発生する。 → [緩和策] 先に対象3系統（ボトムバー・検索バー・トップバー）の共通引数を洗い出して最小十分 API を定義する。
- [リスク] ツールチップ表示制御を誤るとメニュー表示中にツールチップが残る。 → [緩和策] 既存画像ビューアのガード条件を共通部品へ取り込み、表示フラグ変化時に明示 dismiss する。
- [トレードオフ] UI 挙動統一により画面ごとの細かな差異表現は減る。 → [緩和策] tint/文言/表示可否は引数で残し、契約を壊さない範囲で調整可能にする。
