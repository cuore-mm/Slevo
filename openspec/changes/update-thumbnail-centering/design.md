## Context
画像ビューア下部のサムネイルバーは、現在は単純な LazyRow 表示のみで、選択中のサムネイルが端に寄る場合がある。特に画像枚数が少ない場合、先頭/末尾のサムネイルが中央に来ず UI が安定しない。

## Goals / Non-Goals
- Goals: 選択中サムネイルを常に中央に配置し、先頭/末尾でも空白を許容する。
- Goals: 画像枚数が少ないケース（2枚など）でも中央寄せを維持する。
- Non-Goals: サムネイルの拡大率や表示/非表示のルール変更、データ取得や並び順の変更。

## Decisions
- Decision: LazyRow の viewport 情報と visibleItemsInfo から中心差分を計算し、差分量のみ `animateScrollBy(delta)` で調整する。端のサムネイルでも中央に寄せられるよう、左右の contentPadding を動的に広げる。
- Implementation outline:
  - `layoutInfo.viewportStartOffset / viewportEndOffset` からビューポート中心を計算する。
  - `visibleItemsInfo` から選択アイテムの `offset / size` を使い、アイテム中心を算出する。
  - `delta = itemCenter - viewportCenter` を計算し、`abs(delta) >= 1px` の場合のみ `animateScrollBy(delta)` を実行する。
  - 選択アイテムが可視範囲外の場合は `scrollToItem(index)` で可視化し、次のフレームで差分スクロールを行う。
  - 端でも中央寄せできるよう、`centerPadding = max(0, viewportWidth / 2 - itemWidth / 2)` を左右の `contentPadding` に設定する。
  - 画像枚数が少ない場合も同じパディングと差分スクロールが有効になり、中央に固定される。

## Alternatives considered
- 代替案: 先頭/末尾にダミーの Spacer アイテムを追加する。
  - 理由: 実装がやや冗長になり、アイテム計算が複雑になるため不採用。

## Risks / Trade-offs
- 初期フレームではサムネイル実測幅が 0 の可能性があるため、幅取得後にスクロールを再実行する必要がある。
- センターオフセット計算のためにサイズ取得が必要となり、計測フローが増える。

## Migration Plan
- UI のみの変更のためマイグレーション不要。

## Open Questions
- なし。
