## Why

画像ビューアのその他メニューはアンカー重ね表示になったが、背景は不透明寄りの単色Surfaceで表示しているため、画像コンテンツ上での没入感が不足している。ビューア画面では背景画像との関係性を保ちながら操作項目の可読性を確保する必要がある。

メニュー背景へhaze（背景ぼかし）を導入し、視認性とデザイン一貫性を両立する表示契約を定義する。

## What Changes

- 画像ビューアのアンカーオーバーレイメニュー背景にhazeを適用し、背後コンテンツをぼかして表示する。
- haze適用時も項目テキストの可読性を維持するため、前景色と補助Tintの適用ルールを明確化する。
- haze未適用環境や描画条件不一致時は既存の半透明Surface表示へフォールバックする。
- 既存のメニュー項目構成、アクション実行、dismiss契約は変更しない。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `image-viewer`: 画像ビューアのその他メニュー背景表現を単色Surfaceからhaze対応へ拡張し、フォールバック表示契約を追加する。

## Impact

- Affected specs:
  - `openspec/specs/image-viewer/spec.md`
- Affected code:
  - `AnchoredOverlayMenu`（背景描画とスタイル定義）
  - `ImageViewerTopBar`（メニュー呼び出し側の描画文脈）
- UX impact:
  - 背景画像との一体感向上
  - 項目可読性維持
