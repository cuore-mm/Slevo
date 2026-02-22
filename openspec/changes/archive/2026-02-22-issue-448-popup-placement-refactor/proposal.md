## Why

Issue #448 対応の過程でポップアップ配置ロジックに複数回の修正が入り、現在は余白計算・幅制約・オフセット補正が分散している。
動作は成立しているが、計算経路が複線化しており、将来の調整時に副作用を追跡しにくい。
配置責務を単一の計算経路に統合し、実装の見通しと保守性を改善する。

## What Changes

- `ReplyPopup` の配置計算を「余白主導」の単一経路へ統合する。
- `calculatePopupOffset` / `calculateClampedPopupOffsetX` など分散した座標計算を整理し、配置責務を1つの関数群に集約する。
- `Int.MAX_VALUE` を使う疑似フォールバック計算を削減し、未計測時の挙動を明示的に扱う。
- 既存の表示仕様（段数別左余白、右余白4.dp、右見切れ防止、左余白上限、高さ縮小）は維持する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-tree-popup`: 多段ポップアップ配置ロジックをリファクタリングし、同一仕様をより単純な計算構造で提供する。

## Impact

- Affected specs:
  - `openspec/specs/thread-tree-popup/spec.md`
- Affected code:
  - `ReplyPopup` の配置計算関数と呼び出し経路
  - 配置計算ユニットテスト
- Non-functional impact:
  - 可読性と変更容易性の向上
  - 仕様変更時の回帰リスク低減
