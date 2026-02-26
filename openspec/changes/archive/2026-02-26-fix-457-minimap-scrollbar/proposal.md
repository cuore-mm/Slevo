## Why

スレッド画面でミニマップ付きスクロールバーを表示した際、表示中インジケータが投稿単位で段階的に移動するため、常時かくついて見える。
閲覧位置の把握性を高める機能であるにもかかわらず体験品質を下げており、Issue #457 で恒常的に再現しているため早期に修正する。

## What Changes

- ミニマップ付きスクロールバーの表示中インジケータ位置を、投稿インデックスだけでなく先頭要素のスクロールオフセットを反映した連続値で算出する。
- 表示中インジケータの高さを可視アイテム数の整数近似ではなく、先頭・末尾の部分可視量を含めた実表示領域で算出する。
- 既存のミニマップ描画要素（勢いグラフ、URL マーカー、新着ライン）の見た目・操作性は維持しつつ、指標矩形のみを滑らかに追従させる。

## Capabilities

### New Capabilities
- `thread-minimap-scrollbar`: スレッド画面のミニマップ付きスクロールバーが、スクロール量に連動して連続的にインジケータを移動・伸縮表示する要件を定義する。

### Modified Capabilities
- なし

## Impact

- 対象コード: `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/components/MomentumBar.kt`
- 対象画面: `ThreadScreen` の `showMinimapScrollbar = true` 経路
- API や外部依存の追加はなし（Compose の既存 `LazyListState.layoutInfo` を活用）
