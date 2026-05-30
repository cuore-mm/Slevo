## Why

`AnchoredOverlayMenu` の縦位置指定が「重ね表示」と「非重なり表示」の意図を表現しづらく、`offset` による位置調整は水平/垂直の意味が混在している。特に縦方向は `offset.y` ではなく配置 enum と spacing で表現する方が意図を読み取りやすい。画像ビューア、設定メニュー、タブの長押しメニューなど用途が増えているため、意図を明示できる配置 API に整理する必要がある。

## What Changes

- `AnchoredOverlayMenu` の縦位置指定を拡張し、重ね表示/非重なり表示/自動配置を明示的に選択できるようにする。
- `offset` を主要な位置調整 API として使わない方針にし、縦方向の調整は配置 enum と `verticalSpacing` に集約する。
- 既存の横方向微調整が必要な箇所は、縦方向と混同しない明示的な水平調整 API へ移行する。
- 既存の呼び出し元を明示的な配置指定へ更新し、従来の重ね表示/非重なり表示の意図を維持する。

## Capabilities

### New Capabilities
- `anchored-overlay-menu-positioning`: アンカーオーバーレイメニューの縦位置指定を、重ね表示・非重なり表示・自動配置で明示的に制御する機能。

### Modified Capabilities
- (なし)

## Impact

- 影響範囲:
  - `AnchoredOverlayMenu` の配置 enum と位置計算ロジック
  - `offset` パラメータの廃止/非推奨化と、必要に応じた水平調整 API
  - `AnchoredSelectionMenu` / `ImageViewerTopBar` / `SettingsGestureScreen` など呼び出し元の配置指定
  - 既存のメニュー表示位置（意図の明示化による調整）
- 依存関係/DB 変更なし
