## 1. ページShared Bounds基盤

- [x] 1.1 `ui/common/transition/BbsPageSharedBounds.kt`を追加し、KDoc付き`BbsPageSharedBoundsKey.Board(identity)` / `Thread(identity)`を既存`BbsControllerSharedBoundsKey`とは別型で定義する。
- [x] 1.2 同ファイルへ`Modifier.bbsPageSharedBounds`を追加し、`sharedBounds`、`scaleToBounds()`、約300msのbounds transform、短いenter / exit fadeを設定する。`enabled=false`では入力Modifierを返すテストを含める。
- [x] 1.3 `BbsPageSharedBoundsKeyTest.kt`を追加し、同種同一identityの等価、Board / Thread種別分離、異なるidentity、コントローラーkeyとページkeyの型分離を検証する。
- [x] 1.4 `BbsPageSharedBoundsTest.kt`を追加し、小さいカードrootと全画面page rootのmatch、逆方向match、disabled、identity不一致、種別不一致を既存`SharedTransitionLayout` harnessで検証する。

## 2. Board / Thread現在表示ページの接続

- [x] 2.1 `BbsRouteScaffold.kt`へ、入力Modifier・settledTab・有効状態を受けてページModifierを返すComposable lambda引数を追加し、既存呼び出し元が未指定の場合は入力Modifierをそのまま返すdefaultを設ける。
- [x] 2.2 `BbsRouteScaffold.kt`のroot内で既存`Scaffold`と`BbsRouteStatusBarProtection`を新しい全画面page containerへまとめ、`BookmarkSheetHost`、`optionalSheetContent`、`UrlOpenDialog`をcontainer外の兄弟として維持する。
- [x] 2.3 page containerへ`settledTab`を渡し、`pagerState.isScrollInProgress == false`かつsettled indexが有効な場合だけページShared Boundsを有効にする。navigation routeとPager page itemをidentityに使用していないことを確認する。
- [x] 2.4 `BoardScaffold.kt`から既存scopeと`BbsPageSharedBoundsKey.Board(settledTab.boardUrl)`をpage modifier lambdaへ渡す。タイトルカード、Threadボタン、ActionsRowの既存コントローラーmodifierは変更しない。
- [x] 2.5 `ThreadScaffold.kt`から既存scopeと`BbsPageSharedBoundsKey.Thread(settledTab.id.value)`をpage modifier lambdaへ渡す。タイトルカード、Boardボタン、ActionsRowの既存コントローラーmodifierは変更しない。
- [x] 2.6 `BbsRouteScaffoldSelectionTest.kt`へroute=A / settledTab=CでCをidentityに使うケース、scroll中、settled index範囲外、空一覧で無効になるケースを追加する。
- [ ] 2.7 `BbsRouteScaffoldTest.kt`でpage containerが本文、下部ツールバー、ステータスバー保護を含み、前後Pager pageを個別共有せず、sheet / dialog / popupを含まないことをtest hookまたはsemanticsで検証する。

## 3. Tabsカードの接続

- [x] 3.1 `MainShellNavGraph.kt`のTabs inner destinationからRoot MainShellの`sharedTransitionScope`と`AnimatedVisibilityScope`を`TabsScaffold.kt`、`TabScreenContent.kt`、`TabsPagerContent.kt`へ明示的に伝播する。`sourceRoute`は初期ページとNavigation文脈だけに残す。
- [x] 3.2 `TabsPagerContent.kt`で既存`isSharedTransitionCandidate`を使い、settle済み現在ページかつ非scroll時だけ`OpenBoardsList.kt` / `OpenThreadsList.kt`のページShared Boundsを有効にする。
- [x] 3.3 `TabsPagerContent.kt`の`AnimatedListContent`で通常／検索のtarget display stateだけを有効にし、crossfade退出側へ同一page keyを登録しない。
- [x] 3.4 `OpenBoardsList.kt`の各`TabListCard` rootへ`BbsPageSharedBoundsKey.Board(tab.boardUrl)`を適用し、削除、drag、長押しPreview、複数選択中は無効化する。Shared Transition専用selected keyや待機stateを追加しない。
- [x] 3.5 `OpenThreadsList.kt`へ3.4と同じ接続を追加し、`BbsPageSharedBoundsKey.Thread(tab.id.value)`だけを使用する。
- [x] 3.6 Tabs Compose testでsettle済み現在ページ、非表示ページ、scroll中、通常／検索crossfade、削除、drag、長押しPreview、複数選択の候補排他を検証する。
- [ ] 3.7 実カードを使うShared Bounds testでBoard card↔Board page、Thread card↔Thread pageがmatchし、別カード、別種、viewport外の未composeカードが代替matchしないことを検証する。

## 4. contextual Tabsの単一可視transition

- [x] 4.1 実装開始前に導入済みNavigation Composeの公開APIで現在のback stack entry列を取得できることを確認し、`Tabs → Thread → Board`の連続entry判定方法を`NavigationExtensions.kt`のKDocとテストへ固定する。取得できなければ実装を止めてOpenSpec更新のblockerとして報告する。
- [x] 4.2 `showBoardScreenFromTabs` / `showThreadScreenFromTabs`を、entry ID guardと登録・選択確認後呼び出しを維持したまま、同種別選択でもsource destinationとTabsを`popUpTo(inclusive = true)`付き新destinationへ1回で置換する実装に整理する。
- [x] 4.3 Board→Tabs→Threadを、Tabsをinclusiveに除去する`popUpTo`付きThread navigate 1回へ変更し、最終stackが従来どおり`Board → Thread`になることをテストする。
- [x] 4.4 Thread→Tabs→Boardで直下がBoardの場合はTabsとThreadをBoardまで1回でpopし、直下がBoard以外の場合はsource ThreadとTabsをinclusiveに除去するBoard navigate 1回で置換する。
- [x] 4.5 `NavigationExtensionsTest.kt`でルートTabs、同種Board / Threadの同一・別identity置換、Board→Thread、Thread→Boardの直下Boardあり／なし、より古いBoardを誤再利用しないケースを検証し、既存と同じ最終entry構造を確認する。
- [x] 4.6 entry ID不一致、現在entryがTabs以外、非同期登録中Back、pop / navigate不能では古いcallbackが履歴を変更しないことを既存テストと追加ケースで確認する。

## 5. Tabs↔BBS transitionの置換

- [x] 5.1 `TransitionSpecs.kt`へTabs↔Board / Threadのroute組合せ判定と横移動を含まないfade-only enter / exit / pop transitionを追加し、非自明関数へKDocを付ける。
- [x] 5.2 `RootNavGraph.kt`のBoard、Thread、MainShell destinationで`BbsEntryTransition.TabsSharedBounds`時だけfade-onlyを使用し、既存横slideを適用しない。Board↔Thread判定、ImageViewerのnull、MainShell内Bookmark / BbsServiceGroupの`None`、その他default transitionの優先順位を維持する。
- [x] 5.3 `TransitionSpecsTest.kt`でTabs→Board、Tabs→Thread、Board→Tabs、Thread→Tabsのpush / popがfade-only対象になり、Board↔Threadとその他routeが従来判定のままであることを検証する。
- [ ] 5.4 実NavHost testでShared Bounds成立時に選択カードと最終page rootが同時にmatchし、contextual別種選択で中間Board / Thread画面が描画されないことを検証する。
- [ ] 5.5 Shared Bounds不成立時に横slideを発生させずfade-onlyで完了し、Navigationの最終stackと選択タブが正しいことを検証する。

## 6. 回帰・実機・品質確認

- [x] 6.1 既存Board↔Threadのタイトルカード、画面種別ボタン、ActionsRowのShared Bounds testを通し、ページkeyとの重複・ネスト競合がないことを確認する。
- [x] 6.2 ImageViewerのShared Element、Tabsのカード表示・検索・削除・並べ替え・長押し・複数選択、初期ページ・初期スクロール、選択key永続化の既存テストを通す。
- [ ] 6.3 実機でBoard / Thread各カードの拡大とBack縮小、同種／別種contextual選択、先頭・中央・末尾、検索結果、viewport外fallbackを確認する。
- [ ] 6.4 実機で拡縮対象が現在表示viewport、本文、背景、下部ツールバー、ステータスバー保護だけであり、前後Pager page、sheet、dialog、popup、アプリ共通chromeを含まないことを確認する。
- [ ] 6.5 実機のProfile GPU RenderingまたはMacrobenchmarkで全画面`scaleToBounds`のjankを確認し、許容できない場合は`RemeasureToBounds`へ変更せず計画更新のblockerとして報告する。
- [x] 6.6 追加・変更したclass/interfaceと非自明関数のKDoc、guard、fallback、長い関数のsection headerを確認し、Preview関数にdoc commentを追加していないことを確認する。
- [x] 6.7 `openspec validate add-tabs-bbs-page-shared-transition --strict`を実行し、proposal、delta specs、design、tasksの整合を確認する。
- [x] 6.8 CIで`./gradlew build`と`./gradlew test`を実行し、追加instrumented Compose testを含む全ジョブを成功させる。
