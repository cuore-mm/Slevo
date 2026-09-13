## 1. Route契約と互換性

- [x] 1.1 `AppRoute.kt`（旧`AppNavGraph.kt`）の`AppRoute`へ`RootRoute` / `MainShellRoute` markerを追加し、全具象routeを設計どおり片方へ所属させてコンパイルで確認する
- [x] 1.2 default付き`AppRoute.MainShell`、`MainShellMode`、`MainShellStartDestination`、`BbsEntryTransition`、`MainShellBbsOrigin`を追加し、新しい型へ規約どおりKDocとenum用`@Keep` / `@Serializable`を付ける
- [x] 1.3 `AppRoute.Board` / `AppRoute.Thread`へdefault付き`BbsEntryTransition`を追加し、既存画面データ引数と呼び出し元が従来値を維持する単体テストを追加する
- [x] 1.4 Navigation Compose 2.8.9のproduction graphで全routeを登録・serialize・`toRoute`でき、custom `NavType`が不要なことをinstrumentedまたはAndroid単体テストで確認する
- [x] 1.5 Activity再生成相当のsaved stateテストでdefault付きroute引数とenumを復元し、互換性を確認できない場合はgraph移行前に本changeのdesignを更新する

## 2. RootとMainShellのgraph分割

- [x] 2.1 `AppRoute.kt`（旧`AppNavGraph.kt`）のdestinationと共通引数を棚卸しし、Rootへ残すroute、MainShellへ移すroute、各Hilt ViewModel ownerをテストまたは設計コメントで一覧化する
- [x] 2.2 `RootNavGraph.kt`を作成し、start destinationを`AppRoute.MainShell`にしてBoard / Thread / History / Settings / About / ImageViewerを登録する
- [x] 2.3 `MainShellNavGraph.kt`を追加し、Tabs / Bookmark / `RegisteredBBSNavigation.kt`のBBSサービス・カテゴリ・板一覧routeを登録する
- [x] 2.4 `MainShell.kt`を追加し、entryごとの`rememberNavController()`、`Scaffold`、既存`NavigationBottomBar`、MainShellNavGraphを接続する（runtime依存のためMainShell自体はPreview対象外）
- [ ] 2.5 MainShellの`Scaffold.innerPadding`をMainShell内destinationだけへ適用し、RootNavHostのconstraintsがNavigationBar表示状態で変わらないCompose testを追加する
- [x] 2.6 base MainShellのNavigationBar切替を既存`launchSingleTop` / `saveState` / `restoreState`規則へ接続し、Tabs / Bookmark / BBSサービス切替でRoot stackが増えないNavigation testを追加する
- [x] 2.7 contextual MainShellではTabsからBookmark / BBSサービスをinner stackへpushし、BackでTabsへ戻るNavigation testを追加する
- [ ] 2.8 移動した各destinationの`hiltViewModel` ownerとsaved stateを確認し、MainShellからRootへ往復しても期待するViewModel stateが復元される統合テストを追加する

## 3. AppScaffoldとRoot overlay

- [x] 3.1 `AppScaffold.kt`の外側`Scaffold`、`hasRootBottomBar`、Root content padding切替を除去し、`Box` + 単一`SharedTransitionLayout` + RootNavGraphへ置換する
- [x] 3.2 `pendingRestoreSnackbarHostState`、`PendingRestoreResultSnackbar`、acknowledge callbackを`AppScaffold`のRoot overlayとして維持し、MainShellへ移動していないことをテストする
- [x] 3.3 Root overlay専用のbottom chrome占有量モデルを追加し、MainShell NavigationBar、Board / Thread下部ツールバー、下部バーなし画面の値を同値更新抑止付きで供給する
- [x] 3.4 Root `SnackbarHost`へbottom chromeとsafe drawingを反映し、そのpadding / offsetがRootNavHostのsizeまたはcontent paddingへ伝播しないCompose testを追加する
- [ ] 3.5 MainShell / Board / Threadで復元結果Snackbarを表示し、Navigation中も同じhostで継続し、各下部バーとsystem navigation領域に重ならないbounds testを追加する
- [ ] 3.6 `MoreMenuDialog`等の既存Root overlayの描画順、dismiss、Navigation callbackを新しいRoot containerへ再接続して既存動作を確認する

## 4. Root/MainShell間Navigation

- [x] 4.1 `NavigationExtensions.kt`をRoot用とMainShell用責務へ整理し、MainShell内画面からRoot画面を開く処理を`onOpenBoard` / `onOpenThread` / Settings等のcallbackへ置換する。Board / ThreadからBookmark・BBS一覧を開く既存操作は、初期inner destination付きMainShell callbackへ接続する
- [x] 4.2 Bookmarkと`RegisteredBBSNavigation.kt`の板・スレッド起動を`BbsEntryTransition.MainShellSlide`付きRoot Navigationへ接続する
- [x] 4.3 TabsカードとURL入力のBoard / Thread起動を`BbsEntryTransition.TabsSharedBounds`付きRoot Navigationへ接続し、登録・選択確認をNavigationより先に実行する
- [x] 4.4 Board→Thread、Thread→Board、Deep linkの起動に適切な`BbsEntryTransition`を設定し、既存push / pop / replaceとroute正規化を維持する
- [x] 4.5 Board / Threadのタブ一覧操作を`AppRoute.MainShell(MainShellMode.ContextualTabs)`のRoot pushへ変更し、直前Root entryだけをsourceとして扱う
- [x] 4.6 Root MainShell entry IDとinner Tabs entry IDをTabs選択要求へ渡し、どちらかが現在entryと不一致ならRoot履歴を変更しないstale guardテストを追加する

## 5. contextual Tabsの履歴変換

- [x] 5.1 `X → Board A → contextual Tabs`からBoard Bを直接選んだ場合に`popUpTo(Board A, inclusive = true)`付きnavigateで`X → Board B`にするRoot stack変換を実装・テストする
- [x] 5.2 `X → Thread A → contextual Tabs`からThread Bを直接選んだ場合に`X → Thread B`にするRoot stack変換を実装・テストする
- [x] 5.3 contextual Tabsから反対種別を直接選択した場合に、Board→Threadのpush、Thread→直下Boardのpop、直下Boardなしのreplaceを既存規則どおり実装・テストする
- [x] 5.4 `X → Board A → contextual Tabs → Bookmark → Board B`ではsourceをpopせず、Backが`Board B → Bookmark → Tabs → Board A → X`となる二階層Navigation testを追加する
- [x] 5.5 BookmarkとBBSサービス起点ではMainShellがcontextualでも`origin == Tabs`として扱わず、通常Root pushすることを全Board / Thread組合せでテストする
- [x] 5.6 contextual MainShellの直下が期待するBoard / Threadでない場合やpop対象が消失した場合、古いentryを探索せずNavigationを中止する異常系テストを追加する

## 6. Root transitionとShared Transition

- [x] 6.1 `TransitionSpecs.kt`とRootNavGraphのenter / exit / popEnter / popExitを`BbsEntryTransition`ベースへ変更し、Tabs Shared Bounds、MainShell slide、Board↔Thread slide-only、Deep link、defaultを判定する
- [ ] 6.2 `TransitionSpecsTest.kt`へ全遷移文脈のpush / pop、Board↔Thread優先、ImageViewerのnull transition、その他destinationの回帰ケースを追加する
- [x] 6.3 Root MainShell destinationの`AnimatedVisibilityScope`を`MainShell.kt`からTabsカード描画経路へ伝播し、MainShellNavHostのinner scopeと型またはparameter名で明確に区別する
- [ ] 6.4 Board / Thread側の`BbsPageSharedBounds`がRoot destination scopeを維持し、Tabsカードと同一`SharedTransitionScope` / Root transition上でmatchする実NavHost testを追加する
- [ ] 6.5 Board↔Threadの`BbsControllerSharedBoundsKey`、ImageViewer Shared Element、Tabs Shared Bounds fallbackがgraph分割後も既存対象と描画順を維持する回帰テストを追加する
- [ ] 6.6 MainShell→Board / ThreadとBackのCompose testでNavigationBarがMainShellより先に消えず、RootNavHost boundsとTabsカード始点boundsが遷移途中に変化しないことを測定する

## 7. Deep link・状態復元・Back

- [x] 7.1 `DeepLinkHandler.kt`とMainActivityの初期NavigationをRoot/MainShell controllerへ振り分け、冷起動時はbase MainShellを土台として目的Rootまたはinner destinationを表示する
- [ ] 7.2 base MainShellとcontextual MainShellが別々のinner controller stateを保持し、Root push / pop後に選択画面・nested list・スクロール状態を復元するActivity再生成テストを追加する
- [ ] 7.3 MainShell内にpop可能なinner entryがある場合はinner Backを優先し、start destinationではRoot Backへ委譲するBack dispatcherテストを追加する
- [ ] 7.4 構成変更と可能なprocess recreation環境でRoot stack、各MainShell inner stack、`BbsEntryTransition`、pending restore通知候補を同時に復元する統合テストを追加する

## 8. 既存計画の整合と検証

- [x] 8.1 `add-tabs-bbs-page-shared-transition`のgraph、scope伝播、Tabs↔BBS transition、contextual stackに関するdesign / tasksをRoot/MainShell構成へ更新してstrict validationする
- [x] 8.2 `replace-tabs-bottom-sheet-with-fullscreen-tabs`の単一stack、sourceRoute、top-level Tabsに関するdesign / tasksを二階層履歴へ更新してstrict validationする
- [x] 8.3 新規・変更Composable、class、interface、enum、非自明関数についてリポジトリのKDoc、section header、guard / fallbackコメント規約を確認する
- [x] 8.4 `openspec validate move-navigation-bar-into-main-shell --strict`を成功させ、CI上のbuildとunit testを成功させる
- [ ] 8.5 対象CIで追加instrumented testを成功させ、ジェスチャーナビゲーション・3ボタン・縦横画面のNavigationBar、Shared Bounds、Snackbar、全Back順を実機確認する
