## Context

`AppRoute.Tabs` は `AppNavGraph.kt` で `TabsScaffold` を表示する既存の全画面 destination である。一方、Board / Thread の `BbsRouteScaffold.kt` はローカルの `showTabListSheet` と `TabsBottomSheet` を使い、同じ `TabScreenContent` を BottomSheet 内に表示している。

現在の `TabsScaffold` は `TabScreenContent` に `currentScreenRoute = null` と no-op の `closeDrawer` を渡す。したがって、Board / Thread から単純に `AppRoute.Tabs` をpushするだけでは、タブ選択時に元の画面種別を判定できず、`NavigationExtensions.kt` の `showBoardScreenForTabSelection` / `showThreadScreenForTabSelection` が選択先を追加でpushしてTabsを履歴に残す。

カード選択は `OpenBoardsList.kt` / `OpenThreadsList.kt` で先に `closeDrawer` を呼ぶ一方、URL入力は `TabScreenContent.kt` でNavigation後に `closeDrawer` を呼ぶ。BottomSheetでは問題にならないが、`closeDrawer` を `popBackStack()` に置き換えると新しいdestinationを誤ってpopするため、全画面化と同時に処理順を統一する必要がある。

選択中タブは引き続きActivity-retainedな `TabSessionStore` の正規化済みboard URL / `ThreadId`を正本とする。Board / Thread destinationのpush・pop・replace規則は既存の `NavigationExtensions.kt` に集約されている。

## Goals / Non-Goals

**Goals:**

- Board / Threadからのタブ一覧入口を既存の `AppRoute.Tabs` に統一する。
- Tabs直前のback stack entryから遷移元を復元し、別のorigin stateを永続化しない。
- Tabsを除去した後、既存のBoard / Thread選択Navigationへ委譲する。
- Board起点では板一覧、Thread起点ではスレッド一覧を必ず初期表示する。
- 板・スレッド一覧の初期スクロールは遷移元によらず、表示中の選択タブを中央へ配置し、選択対象がない場合は末尾へ移動する。
- カード選択とURL入力で、正規化・登録・選択・Navigationの順序を統一する。

**Non-Goals:**

- `TabSessionStore` の選択key、タブ登録、route正規化ロジックは変更しない。
- タブカード、検索、並び替え、削除、詳細BottomSheet、作成、更新の表示仕様は変更しない。
- Board / Thread以外のトップレベルNavigation、Shared Transition、永続データ形式は変更しない。
- タブ詳細用の `BoardInfoBottomSheet` / `ThreadInfoBottomSheet` は廃止しない。廃止対象はタブ一覧全体を包む `TabsBottomSheet` のみである。

## Decisions

### 1. 既存の`AppRoute.Tabs`を全画面表示先として再利用する

`BbsRouteScaffold.kt` のタブ一覧コールバックは `showTabListSheet = true` ではなく `navController.navigate(AppRoute.Tabs)` を実行する。新しいrouteや全画面Composableは追加せず、`TabsScaffold` → `TabScreenContent` の既存表示経路を使う。

別のTabs routeを追加する案は、同じ一覧UIに複数のdestinationとViewModel scopeを再び作るため採用しない。`AppRoute.Tabs` にorigin引数を追加する案も、back stackに既に存在する遷移元と同じ情報をroute引数として重複管理し、トップレベルTabsのroute同一性とrestoreStateに影響するため採用しない。

### 2. Tabsの遷移元は直前のback stack entryから導出する

`AppNavGraph.kt` の `composable<AppRoute.Tabs>` ラムダで受け取るTabs entryに対し、`navController.previousBackStackEntry` が `AppRoute.Board` または `AppRoute.Thread` かを `hasRoute` / `toRoute` で判定する。復元したrouteオブジェクトを `TabsScaffold` の `sourceRoute: AppRoute?` に渡す。それ以外の直前destinationまたはstart destinationとしてのTabsでは `sourceRoute = null` とする。

この判定は直前entryだけを対象とする。Board / ThreadからTabsを開く操作は必ず対象画面の直上へTabsをpushするため、より古いback stack全体を探索しない。これによりBookmarkなどを誤ってpop対象にしない。

### 3. 初期一覧ページはsourceRouteから直接導出する

`TabsScaffold.kt` は初期ページを次の優先順位で決める。

1. `sourceRoute is AppRoute.Board`: 板一覧ページ
2. `sourceRoute is AppRoute.Thread`: スレッド一覧ページ
3. `sourceRoute == null`: `TabSessionStore.lastSelectedTabsPage`

Board / Threadから開く前に `lastSelectedTabsPage` を書き換える方式は採用しない。遷移元による一時的な初期表示と、ユーザーが最後に選択した一覧ページの記録を分離するためである。表示後にユーザーが板・スレ切替を操作した場合は、既存どおり `setLastSelectedTabsPage` で記録する。

### 4. Tabs選択用ラッパーでTabs除去後に既存規則へ委譲する

`NavigationExtensions.kt` にBoard用とThread用のTabs選択完了関数を追加する。関数は `sourceRoute`、選択先route、選択処理を開始したTabs entryのIDを受け取り、次の順で処理する。

1. 現在のback stack entryが期待したTabs entry IDで、destinationが`AppRoute.Tabs`であることを確認する。
2. `sourceRoute == null` の場合はTabsをpopせず、既存の `showBoardScreenForTabSelection(null, route)` または `showThreadScreenForTabSelection(null, route)` に委譲する。
3. `sourceRoute` がBoard / Threadの場合は `popBackStack()` でTabsだけを除去する。popに失敗した場合は後続Navigationを実行しない。
4. pop成功後、既存の `showBoardScreenForTabSelection(sourceRoute, route)` または `showThreadScreenForTabSelection(sourceRoute, route)` に委譲する。

これにより次のstack変換になる。

| 選択前 | 選択 | 選択後 |
|---|---|---|
| `Bookmark → Board → Tabs` | Board | `Bookmark → Board` |
| `Bookmark → Board → Tabs` | Thread | `Bookmark → Board → Thread` |
| `Bookmark → Board → Thread → Tabs` | Thread | `Bookmark → Board → Thread` |
| `Bookmark → Board → Thread → Tabs` | Board | `Bookmark → Board` |
| `Bookmark → Thread → Tabs` | Board | `Bookmark → Board` |
| ルート`Tabs` | Board / Thread | `Tabs → Board / Thread` |

Tabsから直接ケース別の`popUpTo`を組み立てる案は、既存の `showBoardScreenForTabSelection` にあるThread→Boardのpop/replace判断を重複実装するため採用しない。

### 5. タブ登録成功後だけNavigationを完了する

`OpenBoardsList.kt`、`OpenThreadsList.kt`、`TabScreenContent.kt` のURL入力は次の順序に統一する。

1. 入力またはタブ情報からrouteを作成する。
2. `TabSessionStore` でrouteを正規化する。
3. `registerAndSelectBoardRoute` または `registerAndSelectThreadRoute` を実行する。
4. Threadでは返却indexが0以上の場合だけ、Boardでは登録処理完了後にTabs選択用Navigation関数を呼ぶ。

`closeDrawer`を別途呼ばない。Tabs entryのID確認により、非同期処理中にBackした場合や別のTabs entryを開き直した場合は古い処理からNavigationしない。登録・選択が既に完了していた場合は`TabSessionStore`の選択key更新は保持するが、現在画面を予期せず変更しない。

### 6. BottomSheet固有状態とAPIを削除する

`BbsRouteScaffold.kt` から `showTabListSheet`、タブ一覧用sheet state、`TabsBottomSheet`描画ブロックを削除する。`TabsBottomSheet.kt` 自体を削除する。

`TabScreenContent.kt`、`TabsPagerContent.kt`、`OpenBoardsList.kt`、`OpenThreadsList.kt` に伝播している `closeDrawer` は削除し、Tabs選択完了関数に必要な `sourceRoute` とTabs entry IDを渡す。詳細BottomSheetやURL入力ダイアログのdismiss APIは維持する。

### 7. 初期スクロールは選択keyを優先し、見つからなければ末尾へ移動する

`TabScreenContent.kt` は `sourceRoute` を初期スクロール位置の判定に使用しない。板一覧は `TabSessionStore.selectedBoardTabKey`、スレッド一覧は `TabSessionStore.selectedThreadTabKey` を正本とし、表示順反映後の通常一覧からstable keyに一致するindexを解決する。

対象keyが存在する場合は対象カードを一度可視化した後、`LazyListLayoutInfo` の実測値からカード中心とviewport中心の差分を計算し、`LazyListState`を補正して中央付近へ配置する。先頭・末尾ではスクロール可能範囲に自然にクランプし、content paddingによる追加の空白を作らない。対象keyがnull、一覧に存在しない、または一覧が空の場合は、最後のindexを初期位置にする。

この初期化はデータロードとレイアウト確定後に各通常一覧で一度だけ実行する。初期化完了後の再Composition、画面回転からのstate復元、検索結果の変更では初期位置へ戻さない。検索入力時に既存の検索一覧を先頭へ戻す処理は維持する。

## Implementation Contract

- `AppRoute.Tabs` の型とトップレベルNavigation項目を変更しない。
- `AppNavGraph.kt` はTabs entry、直前のBoard / Thread route、`NavHostController`を `TabsScaffold` に渡せる形にする。遷移元を `rememberSaveable`、ViewModel、`TabSessionStore`へ複製しない。
- `TabsScaffold.kt` は `sourceRoute` とTabs entry IDを `TabScreenContent` へ渡し、初期ページをBoard=0、Thread=1、その他=`lastSelectedTabsPage`として導出する。
- `NavigationExtensions.kt` の既存 `showBoardScreenForTabSelection`、`showThreadScreenForTabSelection`、`replaceCurrentScreen` の責務と既存呼び出し元を壊さず、Tabs専用の薄いラッパーを追加する。
- Tabs専用ラッパーは期待するTabs entry IDと現在entryの一致を確認し、コンテキスト付きTabsをpopしてから既存関数へ委譲する。直接`popUpTo`で同じ分岐を再実装しない。
- `TabSessionStore`への登録・選択が成功する前にTabsをpopしない。
- 初期スクロールは `sourceRoute` ではなく `TabSessionStore`のselected keyと表示順反映後の一覧を使い、keyが解決できない場合は末尾へフォールバックする。
- 初期スクロールの中央補正はレイアウト確定後に行い、対象カード・viewport・スクロール可能範囲を実測して境界内に収める。初期化済みの一覧を再Compositionで再移動しない。
- `TabsBottomSheet.kt` と、その表示だけに必要だったstate・imports・parametersを残さない。
- 新しいclassまたはinterfaceを追加する場合はKDocを付け、非自明関数には既存リポジトリ規約に従うKDocと制御フローコメントを付ける。Compose Preview関数にはコメントを追加しない。
- ユーザー向け文言、カードのcontent description、フォーカス順は変更しない。全画面化後もシステムBackで遷移元へ戻れるため、専用の閉じるボタンや新規文字列は追加しない。

## Error Cases / Compatibility

- Thread routeの登録・canonical解決が失敗した場合、Tabsをpopせずエラー表示または既存の非遷移動作を維持する。
- route正規化中にTabsを離れた場合、entry ID不一致により遅延Navigationを抑止する。
- `sourceRoute == null` のルートTabsは従来どおり選択先をpushし、BackでTabsへ戻れるようにする。
- プロセス再生成後もback stackからsourceRouteを再導出し、別の保存状態を必要としない。
- `TabSessionStore` のselected key、`lastSelectedTabsPage`、永続化形式にmigrationを追加しない。
- Board / Thread以外からトップレベルTabsへ遷移する既存bottom navigationのsaveState / restoreStateを変更しない。

## Testing Strategy

- `NavigationExtensionsTest.kt` の最小graphに、Tabs entry IDとsourceRouteを使う新ラッパーのstack変換を追加する。上記表の全ケース、Tabs pop失敗、現在entry不一致、ルートTabsを検証する。
- `TabsScaffold`または初期ページ導出を純関数化して単体テストし、Board=板、Thread=スレッド、null=最後の一覧ページを検証する。
- Board / Threadのタブ一覧コールバックが `AppRoute.Tabs` へ遷移し、BottomSheetを表示しないことをComposeまたはNavigation統合テストで検証する。
- カード選択とURL入力について、登録失敗時はTabsに留まり、成功時はTabsがstackから消えることを検証する。
- Bookmark相当の前段entryを含むstackで、同種・別種選択後も前段entryが残ることとAndroid Backの戻り先を検証する。
- `BbsRouteScaffold` のPager同期ガードはComposition再生成時に未同期として開始し、Tabsから同種Threadを選択して元destinationへ復帰した際、保存された旧Pager位置のsettle通知がstable selected keyを旧タブへ戻さないことを検証する。
- 板・スレッド一覧の初期スクロールはselected keyが表示順反映後の一覧に存在すれば中央付近、存在しなければ末尾となることをunit testとCompose UI testで検証する。遷移元routeの有無は初期位置に影響させない。
- 実装後に `./gradlew build` と `./gradlew test` を実行する。手動でBoard→Tabs、Thread→Tabs、Bookmark→Board→Tabs、Bookmark→Thread→Tabs、非同期処理中のBack、画面再生成を確認する。

## Migration Plan

1. Tabs起点情報と初期ページ導出を追加する。
2. Tabs選択用Navigationラッパーと単体テストを追加する。
3. タブカード・URL入力を新しい選択完了処理へ移行する。
4. Board / Threadの入口を`AppRoute.Tabs`へ切り替える。
5. `TabsBottomSheet`と不要なstate・parameterを削除する。
6. build、unit test、Navigationシナリオを確認する。

ロールバック時はBoard / Threadの入口と`closeDrawer`経路を戻し、`TabsBottomSheet`を復元する。データmigrationはないため永続データのロールバック作業は不要である。

## Risks / Trade-offs

- [Tabsをpopしてから既存Navigationへ委譲する二段階操作で中間状態が描画される可能性] → 同一メインスレッドイベント内で連続実行し、Board↔ThreadおよびTabs transitionを実機で確認する。視覚的な中間状態が発生する場合だけ、既存規則を共通の決定関数へ抽出して単一NavOptionsへ変換する。
- [非同期正規化中にユーザーがBackまたは再入場すると古いcallbackが発火する] → Tabs entry IDと現在entryを照合して古いNavigationだけを抑止する。
- [destination再生成時に保存済みPagerの旧settledPageがユーザー操作として通知される] → `lastSynchronizedSelectedKey`をComposition開始時にnullで初期化し、selected key変更に伴うprogrammatic scrollが対象keyへsettleするまでsettled callbackを抑止する。
- [一覧ロード前・並び替え反映前に初期スクロールして位置がずれる] → 表示一覧が確定してから一度だけkeyをindexへ解決し、レイアウト情報を待って中央補正する。keyが解決できない場合は末尾へ移動する。
- [画面再生成や検索変更で初期位置がユーザー位置を上書きする] → 初期化済みフラグを一覧stateのライフサイクルに紐づけ、初期処理と既存の検索先頭処理を別のeffectとして維持する。
- [トップレベルTabsをBoard / Thread起点と誤判定する] → 直前entryがBoard / Threadの場合だけcontextual Tabsとし、それ以外はsourceなしとして扱うテストを追加する。
- [BottomSheet削除で検索状態のライフサイクルが変わる] → contextual Tabsはentry popでViewModelを破棄し、トップレベルTabsは従来のdestination scopeを維持する。検索状態を`TabSessionStore`へ移さない。
