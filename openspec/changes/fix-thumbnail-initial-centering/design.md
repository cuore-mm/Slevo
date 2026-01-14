## Context
画像ビューアのサムネイルバーは、現在表示中の画像に合わせて中央寄せを行う実装が入っている。一方で画面遷移直後は LazyRow のレイアウト情報が未確定な状態でセンタリング処理が終了してしまい、初期表示のみ中央に寄らないケースがある。

## Goals / Non-Goals
- Goals: 画面遷移直後（初期表示）でも選択サムネイルが中央に位置する。
- Goals: 画像切替後の既存センタリング挙動は維持する。
- Non-Goals: サムネイルの拡大率、表示/非表示ルール、画像の並び順は変更しない。

## Decisions
- Decision: 初期表示時は LazyRow のレイアウトが確定してからセンタリング処理を実行する。
- Implementation outline:
  - センタリング処理を `centerSelectedThumbnail()` として分離し、`pagerState.currentPage` と初期表示の完了をトリガに呼び出す。
  - `thumbnailListState.layoutInfo` を監視し、`totalItemsCount > 0` かつ `viewportWidth > 0` を満たすまで `withFrameNanos` で待機する。
  - 目的のアイテムが可視範囲外なら `scrollToItem(targetIndex)` で可視化し、1フレーム後に `animateScrollBy(delta)` を実行する。
  - 初期表示の一回目は必ずセンタリングを実行するため、`hasCenteredInitially` を保持して再実行条件を明確化する。
  - `centerPaddingPx` の更新でレイアウトが変わるため、初期表示の処理は `centerPaddingPx` 変化時にも再試行できるようにする。
  - 初期表示時は `targetCenterPaddingDp` を直接適用し、レイアウト確定後にセンタリングする。
  - 初回センタリングはアニメーションなしで行い、センタリング完了までサムネイルを不可視にして移動が見えないようにする。

## Alternatives considered
- 代替案: 初期表示は `scrollToItem(index, offset)` で中央寄せを完結させる。
  - 理由: サムネイルの拡大率と動的パディングを考慮するとオフセット算出が複雑になり、既存の差分スクロール方式と二重管理になるため不採用。

## Risks / Trade-offs
- 初期表示の待機回数が増えるため、低スペック端末で遅延が発生する可能性がある。→ 待機回数を上限付きで制御する。
- センタリングの再試行が多いとスクロールが揺れる可能性がある。→ `hasCenteredInitially` と `abs(delta)` の閾値で抑制する。

## Migration Plan
- UI 挙動のみの変更のためマイグレーション不要。

## Open Questions
- なし。
