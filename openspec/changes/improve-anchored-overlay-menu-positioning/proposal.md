## Why

`AnchoredOverlayMenu` の縦位置指定が「重ね表示」と「非重なり表示」の意図を表現しづらく、さらに `offset.y` が効いていないため呼び出し元で調整したいケースに対応できていない。画像ビューア、設定メニュー、タブの長押しメニューなど用途が増えているため、意図を明示できる配置 API と一貫したオフセット挙動が必要になる。

## What Changes

- `AnchoredOverlayMenu` の縦位置指定を拡張し、重ね表示/非重なり表示/自動配置を明示的に選択できるようにする。
- 位置計算に `offset.y` を反映し、水平/垂直の微調整が意図通り効くようにする。
- 既存の呼び出し元を明示的な配置指定へ更新し、従来の重ね表示/非重なり表示の意図を維持する。

## Capabilities

### New Capabilities
- `anchored-overlay-menu-positioning`: アンカーオーバーレイメニューの縦位置指定を、重ね表示・非重なり表示・自動配置で明示的に制御する機能。

### Modified Capabilities
- (なし)

## Impact

- 影響範囲:
  - `AnchoredOverlayMenu` の配置 enum と位置計算ロジック
  - `AnchoredSelectionMenu` / `ImageViewerTopBar` / `SettingsGestureScreen` など呼び出し元の配置指定
  - 既存のメニュー表示位置（意図の明示化による調整）
- 依存関係/DB 変更なし
