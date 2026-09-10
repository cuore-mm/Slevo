## 1. Tabs遷移コンテキストと初期ページ

- [x] 1.1 `AppNavGraph.kt` の `composable<AppRoute.Tabs>` で現在のTabs entry IDを取得し、直前entryが`AppRoute.Board`または`AppRoute.Thread`の場合だけ`toRoute`でsource routeを復元して`TabsScaffold`へ渡す。start destinationおよびBoard / Thread以外の直前entryではsourceがnullになることを確認する。
- [x] 1.2 `TabsScaffold.kt` にsource routeとTabs entry IDの引数を追加し、Board起点は板ページ、Thread起点はスレッドページ、sourceなしは`TabSessionStore.lastSelectedTabsPage`を初期ページに使う純粋な導出処理を実装する。ページ切替後の`setLastSelectedTabsPage`は維持する。
- [x] 1.3 初期ページ導出のunit testを追加し、最後のページがスレッドでもBoard起点は板、最後のページが板でもThread起点はスレッド、sourceなしは最後のページになることを検証する。

## 2. Tabs選択時のNavigation

- [x] 2.1 `NavigationExtensions.kt` にBoard選択用のTabs完了関数を追加し、期待するTabs entry IDと現在entryを照合したうえで、contextual Tabsだけをpopして既存`showBoardScreenForTabSelection`へsource route付きで委譲する。sourceなしではTabsを残して従来のpushを実行する。
- [x] 2.2 `NavigationExtensions.kt` にThread選択用のTabs完了関数を追加し、2.1と同じentry検証・Tabs除去を行って既存`showThreadScreenForTabSelection`へ委譲する。Tabsのpop失敗時とentry不一致時は後続Navigationを実行しない。
- [x] 2.3 `NavigationExtensionsTest.kt` に`Bookmark → Board → Tabs`からBoard選択でBoard entryを再利用するケースと、Thread選択で`Bookmark → Board → Thread`になるケースを追加し、Tabsが残らずBookmark相当entryが維持されることを検証する。
- [x] 2.4 `NavigationExtensionsTest.kt` に`Bookmark → Board → Thread → Tabs`からThread選択でThread entryを再利用するケースと、Board選択で既存Boardまでpopするケースを追加し、同種destinationを追加しないことをentry IDで検証する。
- [x] 2.5 `NavigationExtensionsTest.kt` に背後Boardなしの`Bookmark → Thread → Tabs`からBoard選択でThreadをreplaceするケース、ルートTabsではTabsを残して選択先をpushするケース、entry ID不一致およびTabs pop失敗で遷移しないケースを追加する。

## 3. タブ一覧の選択処理統一

- [x] 3.1 `TabScreenContent.kt`、`TabsPagerContent.kt`、`OpenBoardsList.kt`、`OpenThreadsList.kt` の引数伝播を、`closeDrawer`ではなくsource routeとTabs entry IDを使う形へ変更し、BottomSheet固有の命名とno-op callbackを除去する。
- [x] 3.2 `OpenBoardsList.kt` のカード選択を「route作成→正規化→`registerAndSelectBoardRoute`完了→Board用Tabs完了関数」の順へ変更し、登録前にTabsを閉じないことをコードとテストで確認する。
- [x] 3.3 `OpenThreadsList.kt` のカード選択を「route作成→正規化→`registerAndSelectThreadRoute`→indexが0以上の場合だけThread用Tabs完了関数」の順へ変更し、登録失敗時にTabsへ留まることを検証する。
- [x] 3.4 `TabScreenContent.kt` のURL入力によるBoard / Thread遷移もカード選択と同じTabs完了関数へ統一し、Navigation後の`closeDrawer`を削除する。非同期処理中にBackまたは別Tabs entryへ再入場した場合に古いentry IDから遷移しないことを検証する。
- [x] 3.5 選択処理変更後も、板は正規化済みboard URL、スレッドは`ThreadId`を`TabSessionStore`のselected keyとして更新し、ComposableやNavigation entryに選択状態の正本を追加していないことを確認する。

## 4. Board / Thread入口の全画面化

- [x] 4.1 `BbsRouteScaffold.kt` の下部タイトルカードとコンテンツからのタブ一覧コールバックを`navController.navigate(AppRoute.Tabs)`へ変更し、Board / Thread destinationの直上へTabsをpushする。
- [x] 4.2 `BbsRouteScaffold.kt` から`showTabListSheet`、タブ一覧用sheet state、`TabsBottomSheet`描画ブロック、および不要になったimportsを削除し、詳細・Bookmark・URL入力用BottomSheet/Dialogには影響がないことを確認する。
- [x] 4.3 `TabsBottomSheet.kt` を削除し、プロジェクト全体で`TabsBottomSheet`、`showTabListSheet`、タブ一覧用途の`closeDrawer`参照が0件であることを検索して確認する。
- [x] 4.4 Board / Threadのタブ一覧操作が`AppRoute.Tabs`へ遷移し、システムBackで元のBoard / Threadへ戻ることをNavigationテストとCIで検証する。既存の可視文言、content description、フォーカス順に変更がないことも確認する。

## 5. 回帰検証

- [x] 5.1 Bookmark相当の前段entryを含むNavigationテストで、同種選択、Board→Thread、背後Boardあり/なしのThread→Board後も前段履歴が維持され、破棄対象のTabs / ThreadだけがBackで再表示されないことを検証する。
- [x] 5.2 ルートTabsのタブ選択、板・スレ一覧切替、検索、並び替え、詳細BottomSheet、URL入力、作成、スレッド更新が従来どおり利用できることを既存コードの責務維持とCI unit test通過で検証する。
- [ ] 5.3 Board→Tabs、Thread→Tabs、Bookmark→Board→Tabs、Bookmark→Thread→Tabs、非同期正規化中のBack、画面再生成の各シナリオを手動確認し、初期ページ、back stack、遷移アニメーションにちらつきや履歴欠損がないことを記録する。
- [x] 5.4 リポジトリ規約に従い、追加・変更したclass/interfaceと非自明関数のKDoc、長い関数のセクションコメント、guard/fallbackコメントを確認する。Compose Preview関数にはdoc commentを追加しない。
- [x] 5.5 CI Run `34468900513` でbuildおよびunit testを実行し、全テスト成功を確認した。
- [x] 5.6 `BbsRouteScaffold` のPager同期判定を、Composition再生成後に旧settledPageが先に通知されるケースで検証し、Tabsから別Threadを選択したstable keyが旧Threadへ戻らないことをunit testで固定する。
