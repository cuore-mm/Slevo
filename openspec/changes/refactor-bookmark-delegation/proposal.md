# Change: ブックマーク操作のインターフェース委譲へのリファクタリング

## Why
BoardViewModel と ThreadViewModel のブックマーク操作が手動の転送と型キャストに依存しており、共通UIでの再利用性と拡張性を下げている。`by` を使った委譲に揃えるには、SingleBookmarkViewModel を ViewModel 生成時に用意できる設計が必要。

## What Changes
- 共有のブックマーク操作インターフェースを定義し、SingleBookmarkViewModel が実装する。
- BoardViewModel と ThreadViewModel を Kotlin の `by` を使ったインターフェース委譲で実装する。
- ViewModel の生成時に SingleBookmarkViewModel を構築できるように、BoardInfo/ThreadInfo をコンストラクタで受け取る設計へ変更する。
- BbsRouteScaffold の具体型キャストを共有インターフェース呼び出しに置き換える。
- SingleBookmarkState の伝播挙動は変更しない。

## Impact
- Affected specs: bookmark-actions
- Affected code: ui/board/viewmodel/BoardViewModel, ui/thread/viewmodel/ThreadViewModel, ui/bbsroute/BbsRouteScaffold, ui/common/bookmark/*
