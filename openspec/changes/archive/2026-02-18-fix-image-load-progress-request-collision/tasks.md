## 1. Registry の request 単位化

- [x] 1.1 `ImageLoadProgressRegistry` の内部構造を request 単位で保持できるデータモデルへ変更する
- [x] 1.2 URL 単位公開値 `progressByUrl` を request 状態の集約結果として再計算する処理を実装する

## 2. Interceptor 連携の更新

- [x] 2.1 `ImageLoadProgressInterceptor` で requestId を生成し、`start/update/finish` を requestId 付きで呼び出す
- [x] 2.2 完了・例外・ボディなしレスポンスを含む全経路で request 状態が確実に解放されることを確認する

## 3. 回帰確認

- [x] 3.1 `ImageThumbnailGrid`、`ImageViewerPager`、`ImageViewerThumbnailBar` が URL キー参照のまま動作することを確認する
- [x] 3.2 同一 URL 並行読み込み時に進捗表示が早期消失しないことを確認し、必要なテストを追加する
