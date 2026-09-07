## 1. Shared Bounds共通契約

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/common/transition/BbsControllerSharedBounds.kt`を追加し、KDoc付きの`BbsControllerSharedBoundsKey.Board(identity)`と`Thread(identity)`を定義する。`destination`というプロパティ名、固定String key、URL正規化処理を含まないことをコードレビューで確認する。
- [x] 1.2 同ファイルへKDoc付きComposable Modifierヘルパーを追加し、有効時だけ`rememberSharedContentState`と`sharedBounds`を適用する。`ScaleToBounds()`を明示し、`renderInOverlayDuringTransition`を指定していないこと、無効時に入力Modifierをそのまま返すことを確認する。
- [x] 1.3 `app/src/test/java/com/websarva/wings/android/slevo/ui/common/transition/`にkeyテストを追加し、同じ型・identityの等価性、Board/Thread型の非等価性、異なるidentityの非等価性を検証する。

## 2. settle済みタイトルカードの限定

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffold.kt`に、ページが`pagerState.settledPage`と一致し、かつ`pagerState.isScrollInProgress`がfalseの場合だけShared Transition候補になる判定を追加する。既存のPager構築、settle監視、選択通知、rubber-band処理のコードを変更していないことを差分で確認する。
- [x] 2.2 `BbsRouteScaffold`、`PagerTitleCards`、`PagerTitleCardPage`のタイトルslot引数へ候補Booleanを伝播し、Board/ThreadのタイトルslotがページindexやPagerStateを直接参照しなくても候補を判定できるようにする。全呼び出し元とテストハーネスがコンパイルできるよう更新する。
- [x] 2.3 `app/src/test/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffoldSelectionTest.kt`に、settle済み非ドラッグページだけがtrue、隣接ページ・非settleページ・ドラッグ中ページがfalseになるテストを追加する。
- [x] 2.4 `app/src/androidTest/java/com/websarva/wings/android/slevo/ui/bbsroute/BbsRouteScaffoldTest.kt`を更新し、タイトルslot引数追加後もドラッグ途中の選択通知、settle後の選択、タイトルカード外ツール群の固定が従来どおりであることを既存テストまたは追加テストで確認する。

## 3. 画面種別ボタンのModifier経路

- [x] 3.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/common/TabToolBar.kt`の`TabToolBar`と`TabToolBarHeader`へ`destinationModifier: Modifier = Modifier`を追加し、Start/Endどちらの`TabDestinationIconButton`でもroot `Card`へ渡す。既存のclickable、enabled、alpha、semantics、配置順を変更しない。
- [x] 3.2 `app/src/main/java/com/websarva/wings/android/slevo/ui/board/components/BoardToolBar.kt`と`ui/thread/components/ThreadToolBar.kt`へ`destinationModifier`を追加して`TabToolBar`へ委譲し、Previewを含む既存呼び出しを更新する。Preview関数へKDocを追加していないことを確認する。
- [x] 3.3 `app/src/androidTest/java/com/websarva/wings/android/slevo/ui/common/TabToolBarTest.kt`を更新し、任意Modifierを渡した後もBoard/Threadボタンの可視ラベル、読み上げ用説明、クリック、disabled semanticsが維持されることを検証する。

## 4. Board/ThreadへのShared Bounds適用

- [x] 4.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/board/screen/BoardScaffold.kt`で、候補となるBoardタイトルカードのroot Modifierへ`Board(tab.boardUrl)`を適用する。shared modifierの後に既存サイズModifierが適用され、候補外では通常Cardのままであることを確認する。
- [x] 4.2 `BoardScaffold.kt`で、`selectedThread`を解決できて`canOpenThread`がtrueのときだけThreadボタンのroot Modifierへ`Thread(selectedThread.id.value)`を適用し、`BoardToolBar`へ渡す。既存の`openSelectedThread`、route正規化、登録・選択、Navigation処理を変更しない。
- [x] 4.3 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`で、候補となるThreadタイトルカードのroot Modifierへ`Thread(tab.id.value)`を適用する。shared modifierの後に既存サイズModifierが適用され、候補外では通常Cardのままであることを確認する。
- [x] 4.4 `ThreadScaffold.kt`で、`selectedBoard`を解決できて`canOpenBoard`がtrueのときだけBoardボタンのroot Modifierへ`Board(selectedBoard.boardUrl)`を適用し、`ThreadToolBar`へ渡す。既存の`openSelectedBoard`、route正規化、登録・選択、pop/replace処理を変更しない。
- [x] 4.5 Board/Thread両側のModifier順を比較し、Shared Boundsが`TabTitleCard`と`TabDestinationIconButton`のroot `Card`だけに付き、内部Text、Icon、bookmark、refresh、progress、下段アクションには付いていないことを差分で確認する。

## 5. Shared Transitionと回帰テスト

- [x] 5.1 `app/src/androidTest/`に`SharedTransitionLayout`と`AnimatedContent`を使うコントローラーShared Boundsテストを追加し、同一`Board(identity)`および同一`Thread(identity)`で開始・途中・終了boundsが変化することをテストクロックで検証する。
- [x] 5.2 同テストでBoard/Thread型が異なる場合、identityが異なる場合、または`enabled=false`の場合に別要素へ誤接続しないことを検証する。
- [x] 5.3 複数タイトルを構成するテストケースを追加し、settle済みタイトルだけが候補となり、隣接タイトルやドラッグ中タイトルがdestinationボタンとShared Boundsを形成しないことを検証する。
- [x] 5.4 `AppScaffold.kt`、`AppNavGraph.kt`、`NavigationExtensions.kt`、TabSessionStore、Coordinator、`ImageSharedTransitionKeyFactory.kt`および画像Shared Transition適用箇所に意図しない差分がないことを`git diff`で確認する。

## 6. ビルドと受け入れ確認

- [x] 6.1 CI-hostedの`testCiUnitTest`（Run ID `34098571977`）で、新規key・候補判定テストを含む全unit testが成功することを確認する。
- [x] 6.2 CI-hostedの`assembleCi`（Run ID `34098571977`）でアプリがビルドできることを確認する。既存CIはinstrumented test sourceをコンパイルしないため、androidTestの実行確認とは分けて扱う。
- [ ] 6.3 実機またはemulatorでBoard→Thread push、Thread→Board pop、Thread→Board replaceを展開・縮退状態で確認し、2組のCardがデフォルトoverlay上で自然に位置・サイズ変形することを記録する。
- [ ] 6.4 実機またはemulatorでPagerドラッグ中、検索中、遷移先ボタンdisabled、既存back stackのPager同期前、5ch.net→5ch.io正規化時を確認し、誤った要素へ接続せず既存操作または通常Nav transitionへフォールバックすることを記録する。
- [ ] 6.5 Thread画像からImageViewerを開いて戻り、既存画像Shared Transitionのkey照合、overlay順、画面遷移に視覚回帰がないことを確認する。
