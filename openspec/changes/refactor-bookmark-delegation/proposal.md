# Change: ブックマーク操作のインターフェース委譲へのリファクタリング

## Why
BoardViewModel と ThreadViewModel のブックマーク操作が手動の転送と型キャストに依存しており、共通UIでの再利用性と拡張性を下げている。

## What Changes
- 共有のブックマーク操作インターフェースと委譲実装を導入する。
- BoardViewModel と ThreadViewModel をインターフェース委譲で実装する。
- BbsRouteScaffold の具体型キャストを共有インターフェース呼び出しに置き換える。
- SingleBookmarkState の伝播挙動は変更しない。

## Impact
- Affected specs: bookmark-actions
- Affected code: ui/board/viewmodel/BoardViewModel, ui/thread/viewmodel/ThreadViewModel, ui/bbsroute/BbsRouteScaffold, ui/common/bookmark/*
