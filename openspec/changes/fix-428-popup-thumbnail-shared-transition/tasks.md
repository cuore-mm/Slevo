## 1. キー生成契約の拡張

- [x] 1.1 `ui/common/transition` に transitionNamespace/key 生成の共通ユーティリティを追加する
- [x] 1.2 `ImageThumbnailGrid` の shared transition キー生成を共通ユーティリティ経由に変更する
- [x] 1.3 `PostItemMedia` と `PostItem` に transitionNamespace の受け渡し引数を追加する
- [x] 1.4 `PopupInfo` に生成時固定の `popupId` を追加し、ポップアップ追加時に一意IDを払い出す
- [x] 1.5 通常リストとポップアップで衝突しない transitionNamespace 生成規則を共通ユーティリティで実装する（ポップアップは `popupId` を使用）
- [x] 1.6 `PostDialog` の画像サムネイルにも専用 transitionNamespace を共通ユーティリティ経由で適用する

## 2. 画像ビューア遷移の文脈伝播

- [x] 2.1 画像タップイベントのコールバック契約に transitionNamespace を追加する（Thread/Popup/PostDialog）
- [x] 2.2 `AppRoute.ImageViewer` に transitionNamespace を追加し、既存呼び出し互換を保つ
- [x] 2.3 `ImageViewerPager` 側の shared transition キー生成を共通ユーティリティ経由に切り替え、遷移元文脈と一致させる

## 3. 検証

- [ ] 3.1 再現手順（2 段目以降のポップアップ）でサムネイルが欠落しないことを確認する
- [ ] 3.2 ポップアップ起点と通常リスト起点の双方で画像ビューア遷移と初期表示インデックスを確認する
- [ ] 3.3 投稿ダイアログ起点でサムネイル表示欠落がないことと画像ビューア遷移整合を確認する
- [x] 3.4 共通ユーティリティのキー生成ルールを単体テストで検証する
- [ ] 3.5 `./gradlew :app:assembleDebug` と `./gradlew :app:testDebugUnitTest` を実行して成功を確認する
