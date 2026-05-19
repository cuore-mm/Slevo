## 1. 正規化責務の移動

- [ ] 1.1 `TabsViewModel` に `normalizeBoardRouteForNavigation(route: AppRoute.Board)` の suspend API を追加する
- [ ] 1.2 `TabsViewModel` に `normalizeThreadRouteForNavigation(route: AppRoute.Thread)` の suspend API を追加する
- [ ] 1.3 追加APIは `SettingsRepository.getIsRedirect5chNetToIoEnabled()` を使い、`TabsViewModel` の一時キャッシュを参照しないようにする
- [ ] 1.4 `NavigationExtensions.navigateToBoard` / `navigateToThread` から設定値参照と `boardUrl` 正規化処理を削除する
- [ ] 1.5 `NavigationExtensions` は渡されたrouteをそのままタブ保証と画面遷移に使うことを確認する

## 2. 板/スレを開く入口の更新

- [ ] 2.1 URL入力入口（`TabScreenContent.kt`）で、生成した板/スレrouteを正規化してから遷移する
- [ ] 2.2 Deep Link入口（`DeepLinkHandler.kt`）で、生成した板/スレrouteを正規化してから遷移する
- [ ] 2.3 画面内URL入力入口（`BbsRouteScaffold.kt`）で、生成した板/スレrouteを正規化してから遷移する
- [ ] 2.4 板/スレ一覧入口（`BoardScaffold.kt`）で、スレrouteを正規化してから遷移する
- [ ] 2.5 ブックマーク入口（`BookmarkListScaffold.kt`）で、保存URLを変更せず開くrouteだけ正規化する
- [ ] 2.6 履歴入口（`HistoryListScaffold.kt`）で、保存URLを変更せず開くrouteだけ正規化する
- [ ] 2.7 既存スレタブ入口（`OpenThreadsList.kt`）で、開くrouteを正規化してから遷移する
- [ ] 2.8 レス本文リンク入口（`PostItemBody.kt`）で、スレリンクrouteを正規化してから内部遷移する
- [ ] 2.9 ポップアップ/スレ画面内リンク入口（`ThreadScaffold.kt`, `ThreadScreen.kt`）で、受け取ったrouteを正規化してから遷移する
- [ ] 2.10 スレ情報シート入口（`ThreadInfoBottomSheet.kt` または呼び出し元）で、板/スレ遷移routeを正規化してから遷移する

## 3. 既存方針の維持確認

- [ ] 3.1 `threadTitle` はURL正規化対象外のまま維持し、タイトル未取得入口では `null` を維持する
- [ ] 3.2 ブックマーク、履歴、板DB、既存タブの保存済みURLを一括移行しないことを確認する
- [ ] 3.3 投稿処理、スレ立て処理、OkHttpクライアント全体に追加変換が入っていないことを確認する
- [ ] 3.4 `itest.5ch.net/subback/{board}` のhost補完が永続化済み設定値と入力元ドメインに基づく既存方針を維持していることを確認する

## 4. テスト更新

- [ ] 4.1 起動直後・未設定デフォルトオンで `5ch.net` route が `5ch.io` に正規化される単体テストを追加/更新する
- [ ] 4.2 起動直後・設定オフで `5ch.net` route が `5ch.net` のまま開かれる単体テストを追加/更新する
- [ ] 4.3 URL入力、Deep Link、レス本文リンク、ブックマーク、履歴の代表入口で正規化済みrouteが使われることをテストまたは既存テストで確認する
- [ ] 4.4 `NavigationExtensions` がrouteを変更せず、受け取ったrouteでタブ保証と画面遷移を行うことを確認する

## 5. 検証

- [ ] 5.1 `openspec validate refactor-route-normalization-settings --strict` を実行する
- [ ] 5.2 Android CIでビルドとユニットテストが通ることを確認する
- [ ] 5.3 Codex reviewで `origin/develop` との差分レビューを行い、未解決指摘がないことを確認する
