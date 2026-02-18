## Why

画像読み込み進捗を URL 単位で管理しているため、同一 URL を同時に複数箇所で読み込むと、先に完了したリクエストが他方の進捗状態を消してしまう。結果として進捗インジケータがちらつく、または不正に消える不具合が発生する。

## What Changes

- 画像読み込み進捗の管理粒度を URL 単位から request 単位へ変更する。
- UI が参照する URL 単位の進捗は、内部の request 単位状態を集約して算出する。
- 同一 URL の並行読み込みで、あるリクエストの完了が他リクエストの進捗表示を消さない契約を追加する。
- 既存の進捗表示 UI（サムネイルグリッド、ビューア本体、ビューア下部サムネイル）への公開インターフェース互換を維持する。

## Capabilities

### New Capabilities
- `image-load-progress`: 画像読み込み進捗の管理・集約・表示契約を定義する。

### Modified Capabilities
- なし

## Impact

- 主な影響範囲は `ImageLoadProgressRegistry` と `ImageLoadProgressInterceptor`。
- 進捗購読側の `ImageThumbnailGrid`、`ImageViewerPager`、`ImageViewerThumbnailBar` はインターフェース互換の範囲で確認が必要。
- 外部 API 追加や依存ライブラリ変更は想定しない。
