## 1. キー生成契約の拡張

- [ ] 1.1 `ImageThumbnailGrid` の shared transition キーを `transitionNamespace|url|index` 形式へ変更する
- [ ] 1.2 `PostItemMedia` と `PostItem` に transitionNamespace の受け渡し引数を追加する
- [ ] 1.3 通常リストとポップアップで衝突しない transitionNamespace 生成規則を実装する
- [ ] 1.4 `PostDialog` の画像サムネイルにも専用 transitionNamespace を適用する

## 2. 画像ビューア遷移の文脈伝播

- [ ] 2.1 画像タップイベントのコールバック契約に transitionNamespace を追加する（Thread/Popup/PostDialog）
- [ ] 2.2 `AppRoute.ImageViewer` に transitionNamespace を追加し、既存呼び出し互換を保つ
- [ ] 2.3 `ImageViewerPager` 側の shared transition キー生成を遷移元文脈と一致させる

## 3. 検証

- [ ] 3.1 再現手順（2 段目以降のポップアップ）でサムネイルが欠落しないことを確認する
- [ ] 3.2 ポップアップ起点と通常リスト起点の双方で画像ビューア遷移と初期表示インデックスを確認する
- [ ] 3.3 投稿ダイアログ起点でサムネイル表示欠落がないことと画像ビューア遷移整合を確認する
- [ ] 3.4 `./gradlew :app:assembleDebug` と `./gradlew :app:testDebugUnitTest` を実行して成功を確認する
