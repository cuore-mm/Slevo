## Why

スレッド閲覧中に、`LazyColumn` の item key が重複して `IllegalArgumentException: Key "..." was already used` によりクラッシュする事象が発生している。特に TREE 表示と複数の新着グループが組み合わさると、同一レス番号が通常行・dimmed 親行・新着グループ内行として複数回表示されるため、現在の「レス番号 + 表示属性」ベースの key では一意性を保証できない。

## What Changes

- スレッド一覧の `LazyColumn` に渡す最終表示単位として、投稿表示情報とは別の `ThreadListItem` モデルを導入する。
- `ThreadListItem` は各表示行の `stableKey` を保持し、LazyColumn はその key のみを使用する。
- 投稿行には、レス番号だけでなく「表示文脈」を表す情報を持たせる。
  - 更新グループ index
  - 表示ロール（通常投稿、dimmed 親投稿など）
  - 同一文脈内の出現 index
- `DisplayPost` は投稿の表示属性を表す中間モデルとして残し、LazyColumn item の identity 生成責務を持たせない。
- TREE 表示および NUMBER 表示の両方で、同一 `visibleItems` 内の key 重複をテストで検出できるようにする。
- 既存の新着バー位置、ツリーインデント、スクロール位置保存、共有要素遷移の挙動は維持する。

## Capabilities

### New Capabilities

- なし

### Modified Capabilities

- `thread-response-order`: スレッド一覧の表示行 identity を、投稿番号ではなく表示文脈を含む最終表示行モデルとして扱う方針を明確化する。

## Impact

- 影響範囲:
  - `ui/thread/state` の表示状態モデル
  - `ui/thread/viewmodel` の表示リスト生成処理
  - `ui/thread/screen/components` の `LazyColumn` 描画処理
  - スレッド表示順・新着グループ・ツリー表示関連のユニットテスト
- 外部 API や DB スキーマへの影響はない。
- Compose Navigation やタブ経路設計への影響はない。
