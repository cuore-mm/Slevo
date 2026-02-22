## Why

現在のレスポップアップは `ThreadScreen` の content レイヤーで描画されるため、Scaffold の bottomBar がポップアップ外タップとして扱われず、ボタンが反応してポップアップが閉じない。
ユーザー期待は「ポップアップ表示中はボトムバー含む画面外タップで閉じる」ため、ポップアップ描画レイヤーを Scaffold 全体を覆う位置へ移す必要がある。

## What Changes

- `ReplyPopup` の描画位置を content 内から、Scaffold 全体の上位レイヤーへ移設する。
- ポップアップ外タップ判定の対象に bottomBar 領域を含める。
- 既存のポップアップ仕様（段数別余白、タップ投稿基準、右端保護、閉じる挙動）は維持する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-tree-popup`: ポップアップ外タップ判定の対象領域を content 限定から画面全体（bottomBar 含む）へ拡張する。

## Impact

- Affected specs:
  - `openspec/specs/thread-tree-popup/spec.md`
- Affected code:
  - `ThreadScaffold` / `ThreadScreen` の `ReplyPopup` 配置責務
  - ポップアップ外タップ判定経路
  - ポップアップ可視状態通知（必要な場合）
- Non-functional impact:
  - bottomBar 誤操作の防止
  - 外側タップ操作の一貫性向上
