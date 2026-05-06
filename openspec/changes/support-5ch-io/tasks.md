## 1. 5ch.io URL受付と解析

- [ ] 1.1 `AndroidManifest.xml` のPC版板/スレ Deep Link に `*.5ch.io` を追加する
- [ ] 1.2 `AndroidManifest.xml` のitest版板/スレ Deep Link に `itest.5ch.io` を追加する
- [ ] 1.3 Deep Link許可ドメインに `5ch.io` を追加する
- [ ] 1.4 共通URLリゾルバのユニットテストに `5ch.io` / `itest.5ch.io` の板・スレ解析ケースを追加する
- [ ] 1.5 Deep Link解決のユニットテストに `5ch.io` / `itest.5ch.io` の許可ケースを追加する

## 2. 5ch.netを5ch.ioとして開く設定

- [ ] 2.1 設定DataStoreに `5ch.net` を `5ch.io` として開くboolean設定を追加し、未設定時デフォルトをオンにする
- [ ] 2.2 `SettingsLocalDataSource` と `SettingsRepository` に設定の監視/更新APIを追加する
- [ ] 2.3 `SettingsViewModel` と `SettingsUiState` に設定値と更新イベントを追加する
- [ ] 2.4 全般設定画面に設定スイッチを追加し、ラベル/説明文の文字列リソースとPreviewを更新する

## 3. 開く直前のboardUrl正規化

- [ ] 3.1 `*.5ch.net` のhostだけを `*.5ch.io` に変換するURL正規化処理を追加する
- [ ] 3.2 URL入力から板/スレを開く処理で、設定オン時のみ正規化後の `boardUrl` を使う
- [ ] 3.3 Deep Linkから板/スレを開く処理で、設定オン時のみ正規化後の `boardUrl` を使う
- [ ] 3.4 itestスレURLから解決した `agree.5ch.net` のようなhostも、設定オン時は `agree.5ch.io` として開く
- [ ] 3.5 投稿処理、スレ立て処理、OkHttpクライアント全体には変換処理を追加しないことを確認する

## 4. 既定BBSMenu更新

- [ ] 4.1 既定BBSMenu URLを `https://menu.5ch.io/bbsmenu.json` に変更する
- [ ] 4.2 boardKeyからhostを補完する既定メニュー参照が `5ch.io` 側を使うことを確認する
- [ ] 4.3 実装時に `https://menu.5ch.io/bbsmenu.json` の取得可否と既存パーサー互換性を確認する

## 5. 検証

- [ ] 5.1 URL正規化処理の単体テストを追加し、設定オン/オフ、`5ch.net`、`5ch.io`、`bbspink.com`、`2ch.sc`、無関係hostを確認する
- [ ] 5.2 URL入力/Deep Link関連テストを更新し、`5ch.net` 入力が設定オン時に `5ch.io` の `boardUrl` で開かれることを確認する
- [ ] 5.3 CIビルド/ユニットテストを実行し、失敗があれば修正する
