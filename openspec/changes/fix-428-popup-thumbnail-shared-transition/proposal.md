## Why

Issue #428 では、レスポップアップを多段で開いた際に 2 段目以降の画像サムネイルが描画されず、枠だけが表示される不具合が常時再現している。ポップアップ階層でも画像の共有トランジションを維持したい要件があるため、表示文脈ごとに衝突しない shared transition 識別子を導入する必要がある。

## What Changes

- 画像サムネイルと画像ビューア間で利用する shared transition キー生成規則を見直し、ポップアップ階層を含む文脈情報で一意化する。
- スレッド通常表示・ポップアップ表示の双方で同じキー契約を適用し、画像ビューア遷移時にキー文脈を維持して受け渡す。
- 多段ポップアップ環境でもサムネイル表示を維持しつつ、既存の画像タップ遷移・初期表示インデックス挙動を保持する。

## Capabilities

### New Capabilities
- なし

### Modified Capabilities
- `thread-tree-popup`: 多段ポップアップでも画像サムネイルが表示され、画像タップで共有トランジションが破綻しないことを要件化する。
- `image-viewer`: shared transition キーを遷移元の表示文脈と一致させる契約を要件化する。

## Impact

- 影響コード: `ui/thread/screen/ThreadScreen.kt`, `ui/thread/res/ReplyPopup.kt`, `ui/thread/res/PostItem*.kt`, `ui/common/ImageThumbnailGrid.kt`, `ui/navigation/AppNavGraph.kt`, `ui/viewer/ImageViewer*.kt`
- 影響範囲: 画像サムネイル表示、画像ビューア遷移パラメータ、shared element キー生成ロジック
- 外部 API や依存ライブラリの追加はなし
