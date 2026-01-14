## Context
画像ビューア下部のサムネイルバーは、現在は単純な LazyRow 表示のみで、選択中のサムネイルが端に寄る場合がある。特に画像枚数が少ない場合、先頭/末尾のサムネイルが中央に来ず UI が安定しない。

## Goals / Non-Goals
- Goals: 選択中サムネイルを常に中央に配置し、先頭/末尾でも空白を許容する。
- Goals: 画像枚数が少ないケース（2枚など）でも中央寄せを維持する。
- Non-Goals: サムネイルの拡大率や表示/非表示のルール変更、データ取得や並び順の変更。

## Decisions
- Decision: サムネイルバーの横幅と選択サムネイルの実測幅からセンターオフセットを算出し、LazyRow の contentPadding とスクロールオフセットで中央寄せを実現する。
- Implementation outline:
  - LazyRow の横幅を onSizeChanged で取得し、センター位置 (rowWidth / 2) を算出する。
  - 選択サムネイルの実測幅（拡大後）を onSizeChanged で保持する。
  - センター配置に必要な先頭/末尾の空白量を `centerPadding = max(0, rowWidth / 2 - itemWidth / 2)` として contentPadding の左右に反映する。
  - ページ切替時に `animateScrollToItem(index, scrollOffset = centerPadding.roundToInt())` を呼び、選択アイテムの開始位置をセンターに揃える。
  - 画像枚数が少ない場合も同じパディングが有効になり、中央に固定される。

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
