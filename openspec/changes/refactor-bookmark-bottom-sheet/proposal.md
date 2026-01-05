# Change: ブックマークシート処理のステートホルダー集約

## 理由
ブックマーク関連の操作が `ThreadViewModel`、`BoardViewModel`、`BookmarkListViewModel` に分散しており、同じ処理が重複している。シート専用コントローラに集約することで重複を減らし、挙動の一貫性を高める。

## 変更内容
- ブックマークシート専用のステートホルダー（ViewModelではない）を導入する。
- 単体編集（板/スレ）と一括編集（ブックマークリスト選択）を同一ステートホルダーで扱う。
- 画面側のViewModelが必要な依存（Repository等）を渡してステートホルダーを生成・破棄する。
- 星表示などの常駐状態は画面ViewModel側で購読し、ステートホルダーとは切り離す。
- `ThreadViewModel`、`BoardViewModel`、`BookmarkListViewModel` のブックマーク処理を集約先に委譲する。

## 影響範囲
- 対象spec: bookmark
- 対象コード: `ui/common/bookmark/*`, `ui/board/viewmodel/BoardViewModel.kt`, `ui/thread/viewmodel/ThreadViewModel.kt`, `ui/bookmarklist/BookmarkListViewModel.kt`, `ui/bbsroute/BbsRouteScaffold.kt`, `ui/bookmarklist/BookmarkListScaffold.kt`
