## Why

現在のツリー表示はインデント幅が段数に対して固定増分のため、深いツリーでは投稿本文の実表示幅が極端に狭くなり、可読性が著しく低下する。
Issue #259 の期待どおり、通常レス幅に対する上限を設けてツリーごとにインデント増分を自動調整し、深いツリーでも表示崩れを防ぐ必要がある。

## What Changes

- ツリー表示時のインデントに「通常レス横幅の 1/4」を上限として導入する。
- 同一ツリー内の最大深さに応じて、インデント増分をデフォルト値から自動縮小するルールを追加する。
- 本体スレッド一覧とツリーポップアップの両方で同一のインデント計算契約を適用し、表示挙動を一致させる。
- 浅いツリーでは既存と同等の見た目を維持し、必要な場合のみ縮小が働くようにする。

## Capabilities

### New Capabilities

- `thread-tree-indentation`: ツリー表示のインデント幅に上限を導入し、ツリー深度に応じて段数増分を動的調整する契約を定義する。

### Modified Capabilities

- なし

## Impact

- Affected specs:
  - `openspec/changes/fix-259-tree-indentation/specs/thread-tree-indentation/spec.md`
- Affected code:
  - `ThreadPostListContent` のツリー行/区切り線の開始位置計算
  - `PostItemContainer` の投稿行インデント適用
  - `ReplyPopup` のポップアップ内投稿インデント適用
  - インデント計算ユーティリティと関連ユニットテスト
- Non-functional impact:
  - 深いツリーでの本文可読性向上
  - ツリー深度による UI 崩れの抑制
