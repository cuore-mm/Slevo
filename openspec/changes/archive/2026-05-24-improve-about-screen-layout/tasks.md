## 1. About 画面ヘッダー

- [ ] 1.1 `AboutScreen` の `LazyColumn` に、アプリアイコン・アプリ名・バージョンを中央揃えで表示するヘッダー領域を追加する。
- [ ] 1.2 アプリアイコンは `R.mipmap.ic_launcher` を `Image` で表示し、フルカラー表示を維持する。
- [ ] 1.3 アプリ名とバージョンは既存の string resource と `BuildConfig.VERSION_NAME` を使って表示する。
- [ ] 1.4 ヘッダーに適切な余白、サイズ、テキストスタイルを設定し、画面上部の情報として読みやすくする。

## 2. 操作項目カード

- [ ] 2.1 既存の GitHub、ログ共有、OSS ライセンスの直接 `ListItem` 表示を `SettingsCardWithListItems` に置き換える。
- [ ] 2.2 GitHub、ログ共有、OSS ライセンスの各項目を `listItemSpecOfBasic` または `ListItemSpec` で定義する。
- [ ] 2.3 GitHub 項目に既存の GitHub URL supporting text と外部 URI 起動処理を接続する。
- [ ] 2.4 ログ共有項目に既存の `onShareLogClick` callback を接続する。
- [ ] 2.5 OSS ライセンス項目に既存の `onOpenSourceLicenseClick` callback を接続する。

## 3. Leading icon とリソース

- [ ] 3.1 GitHub、ログ共有、OSS ライセンスそれぞれに leading icon を追加する。GitHub は `Icons.Extended.GitHub`、ログ共有は `Share`、OSS ライセンスは `Description` を使う。
- [ ] 3.2 アイコンの contentDescription は項目名の string resource を使う。
- [ ] 3.3 既存依存関係で利用可能な icon を選び、新しい依存関係を追加しない。

## 4. Preview と確認

- [ ] 4.1 `AboutScreenPreview` を新しいレイアウトで表示できるよう更新し、Preview 関数には KDoc を付けない。
- [ ] 4.2 About 画面でアプリアイコン、アプリ名、バージョン、3 つのカード項目、leading icon が表示されることを確認する。
- [ ] 4.3 GitHub、ログ共有、OSS ライセンスの各クリック動作が既存どおり接続されていることを確認する。
- [ ] 4.4 実装後に CI または指定された build / unit test を実行し、成功を確認する。
