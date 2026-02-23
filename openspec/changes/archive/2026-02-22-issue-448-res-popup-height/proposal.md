## Why

現在のレスポップアップは段数に関係なく最大高さが固定で、2段目以降を重ねたときに何段目を見ているかを視認しづらい。
Issue #448 の要望どおり、段数に応じて最大高さを段階的に縮小しつつ下限を設け、可読性と操作把握を改善する。

## What Changes

- レスポップアップの最大高さを「段数に応じて徐々に小さくする」表示ルールを追加する。
- 段数が増えても小さくなりすぎないよう、最大高さに下限値を追加する。
- 既存のポップアップ表示位置、タップ挙動、スクロール挙動、アニメーション契約は維持する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-tree-popup`: 多段ポップアップ時の最大高さを段階別に縮小し、下限クランプを適用する要件を追加する。

## Impact

- Affected specs:
  - `openspec/specs/thread-tree-popup/spec.md`
- Affected code:
  - `ReplyPopup` の高さ制約計算ロジック
  - ポップアップ段数に応じたレイアウト適用経路
  - 高さ計算のユニットテスト（新規または既存拡張）
- Non-functional impact:
  - 多段表示時の視認性向上
  - 極端な高さ縮小による可読性低下の防止
