# Change: ブックマークシート処理のコントローラ集約

## 理由
ブックマーク関連の操作が `ThreadViewModel`、`BoardViewModel`、`BookmarkListViewModel` に分散しており、同じ処理が重複している。シート専用コントローラに集約することで重複を減らし、挙動の一貫性を高める。

## 変更内容
- ブックマークシート専用のコントローラクラス（ViewModelではない）を導入する。
- 単体編集（板/スレ）と一括編集（ブックマークリスト選択）を同一コントローラで扱う。
- 画面側のViewModelが必要な依存（Repository等）を渡してコントローラを生成する。
- `ThreadViewModel`、`BoardViewModel`、`BookmarkListViewModel` のブックマーク処理を集約先に委譲する。

## 影響範囲
- 対象spec: bookmark
- 対象コード: `ui/common/bookmark/*`, `ui/board/viewmodel/BoardViewModel.kt`, `ui/thread/viewmodel/ThreadViewModel.kt`, `ui/bookmarklist/BookmarkListViewModel.kt`, `ui/bbsroute/BbsRouteScaffold.kt`, `ui/bookmarklist/BookmarkListScaffold.kt`
