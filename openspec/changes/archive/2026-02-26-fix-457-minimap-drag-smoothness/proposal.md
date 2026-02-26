## Why

Issue #457 の一次対応で、通常スクロール時のミニマップインジケータのかくつきは改善された。
一方でミニマップバーを直接ドラッグした場合は、投稿インデックス単位のジャンプスクロールが残っており、操作体験が依然として不連続である。

## What Changes

- ミニマップバーのドラッグ操作を、インデックス丸め + `scrollToItem` の離散移動から、ドラッグ差分に追従する連続移動へ変更する。
- バー操作中のスクロール更新方式を見直し、イベントごとのコルーチン乱立を避ける。
- タップジャンプ、勢いグラフ、URL/新着/書き込みマーカーなど既存表示要素の挙動は維持する。

## Capabilities

### New Capabilities
- `thread-minimap-drag-scroll`: ミニマップバーの直接ドラッグ時に、投稿単位ジャンプではなく連続的にスクロール追従する要件を定義する。

### Modified Capabilities
- なし

## Impact

- 対象コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/components/MomentumBar.kt`
- 影響範囲: ミニマップバーのポインタイベント処理（タップ/ドラッグ経路）
- API 変更や外部依存の追加はなし
