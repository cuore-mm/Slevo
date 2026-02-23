## Why

issue #449 の自動再取得抑止を入れた後も、画面再生成やリスト再構築の境界をまたぐと一部でローディング表示が再発しています。原因は失敗状態を `remember` でローカル保持しているためで、再生成時に失敗フラグが失われるためです。

失敗表示を安定させるには、失敗状態を `UiState`（ViewModel 管理）へ昇格し、表示ライフサイクルに依存しない永続性を持たせる必要があります。

## What Changes

- 投稿サムネイル失敗状態を UI ローカル保持から `UiState` 管理へ移行し、再表示時も失敗表示を維持する。
- 画像ビューア本体/下部サムネイルの失敗状態を `UiState` 管理へ移行し、ページ離脱復帰や再生成後も失敗表示を維持する。
- 失敗状態キーを index 依存から URL 中心へ整理し、並び変化時の取り違えを抑止する。
- 再取得トリガーを「明示リロード操作のみ」の契約で維持する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `image-viewer`: 失敗状態の保持責務を `UiState`/ViewModel へ移し、再生成境界をまたいでも失敗表示固定契約を維持する。

## Impact

- 対象 UI: `ui/common/ImageThumbnailGrid.kt`, `ui/viewer/ImageViewerPager.kt`, `ui/viewer/ImageViewerThumbnailBar.kt`。
- 対象状態管理: スレッド画面側の `UiState` と画像ビューア側 `ImageViewerUiState`。
- イベント: `onImageLoadError` / `onRetry` / `onLoadSuccess` 相当のイベントを ViewModel で集約する必要がある。
