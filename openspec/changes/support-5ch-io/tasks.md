## 1. 5ch.io URL受付と解析

- [x] 1.1 `AndroidManifest.xml` のPC版板/スレ Deep Link に `*.5ch.io` を追加する
- [x] 1.2 `AndroidManifest.xml` のitest版板/スレ Deep Link に `itest.5ch.io` を追加する
- [x] 1.3 Deep Link許可ドメインに `5ch.io` を追加する
- [x] 1.4 共通URLリゾルバのユニットテストに `5ch.io` / `itest.5ch.io` の板・スレ解析ケースを追加する
- [x] 1.5 Deep Link解決のユニットテストに `5ch.io` / `itest.5ch.io` の許可ケースを追加する

## 2. 5ch.netを5ch.ioとして開く設定

- [x] 2.1 設定DataStoreに `5ch.net` を `5ch.io` として開くboolean設定を追加し、未設定時デフォルトをオンにする
- [x] 2.2 `SettingsLocalDataSource` と `SettingsRepository` に設定の監視/更新APIを追加する
- [x] 2.3 `SettingsViewModel` と `SettingsUiState` に設定値と更新イベントを追加する
- [x] 2.4 全般設定画面に設定スイッチを追加し、ラベル/説明文の文字列リソースとPreviewを更新する

## 3. 全入口で開く直前のboardUrl正規化

- [x] 3.1 `*.5ch.net` のhostだけを `*.5ch.io` に変換するURL正規化処理を追加する
- [x] 3.2 `navigateToBoard` / `navigateToThread` で、設定オン時のみ正規化後のrouteをタブ保証と画面遷移に使う
- [x] 3.3 URL入力、Deep Link、既存板タブ、既存スレタブ、ブックマーク、履歴、板一覧、レス本文中リンク、スレ情報シートから開く入口が共通ナビゲーション経由で正規化されることを確認する
- [x] 3.4 itestスレURLから解決した `agree.5ch.net` のようなhostも、設定オン時は `agree.5ch.io` として開く
- [x] 3.5 保存済みタブ、ブックマーク、履歴、板DBなどの永続化データを直接変更しないことを確認する
- [x] 3.6 投稿処理、スレ立て処理、OkHttpクライアント全体には変換処理を追加しないことを確認する

## 4. 既定BBSMenu更新

- [x] 4.1 既定BBSMenu URLを `https://menu.5ch.io/bbsmenu.json` に変更する
- [x] 4.2 boardKeyからhostを補完する既定メニュー参照が `5ch.io` 側を使うことを確認する
- [x] 4.3 実装時に `https://menu.5ch.io/bbsmenu.json` の取得可否と既存パーサー互換性を確認する

## 5. 検証

- [x] 5.1 URL正規化処理の単体テストを追加し、設定オン/オフ、`5ch.net`、`5ch.io`、`bbspink.com`、`2ch.sc`、無関係hostを確認する
- [x] 5.2 URL入力/Deep Link関連テストを更新し、`5ch.net` 入力が設定オン時に `5ch.io` の `boardUrl` で開かれることを確認する
- [x] 5.3 既存タブ、ブックマーク、履歴から開く場合に、保存URLを変更せず正規化後の `boardUrl` で開くことを確認する
- [x] 5.4 CIビルド/ユニットテストを実行し、失敗があれば修正する

## 6. route正規化とタブ保存の再整理

- [x] 6.1 `navigateToBoard` / `navigateToThread` の共通入口でrouteを正規化し、正規化後routeをタブ保証と画面遷移の両方に使う
- [x] 6.2 `AppRoute.Thread.threadTitle` をURL正規化対象から外し、タイトル未取得のURL由来入口では未設定として扱えるようにする
- [x] 6.3 URL入力、Deep Link、レス本文リンクなど、タイトル未取得の入口が元URLをタブ名として保存しないことを確認する
- [x] 6.4 設定オン時に `5ch.net` の既存タブがあっても、`5ch.io` のタブを別タブとして作成できることを確認する
- [x] 6.5 ブックマーク、履歴、板DBは一括移行せず、そこから開いたタブだけが `5ch.io` routeで保存されることを確認する
- [x] 6.6 route正規化、タブ保存、タイトル未設定時の表示fallbackに関する単体テストまたは既存テスト更新を追加する
- [x] 6.7 タイトル未取得時は空文字ではなく、正規化後 `boardUrl` と `threadKey` から構築したスレURLを表示する
