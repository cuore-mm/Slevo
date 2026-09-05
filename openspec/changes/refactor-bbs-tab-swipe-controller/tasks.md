## 1. settle基準のページ状態

- [x] 1.1 `BbsRouteScaffold.kt` の `currentPage` ベースの `onTabSelected` Effect を `snapshotFlow { pagerState.settledPage }` と stable key の範囲確認へ置き換え、`PendingMissing` とselected-key同期中は通知しないことをコードと単体テストで確認する。
- [x] 1.2 `BbsRouteScaffold.kt` の固定表示対象、`ObserveScrollPositionPersistence.isActive`、ページ固有overlayの対象をsettled pageへ揃え、drag途中の`currentPage`反転でUiStateや保存対象が切り替わらないテストを追加する。
- [x] 1.3 `app/src/androidTest/.../BbsRouteScaffoldTest.kt` のpresentation harnessをproductionと同じsettled-page同期へ更新し、途中復帰、別ページへのsettle、`animateToPageFlow`完了、PendingMissingを検証する。

## 2. Scaffoldとタブ別一時状態の再編

- [x] 2.1 `BbsRouteScaffold.kt` をRoot `Box`と単一`Scaffold`へ再編し、`HorizontalPager`には本文だけ、Scaffoldの`bottomBar`にはsettled tabの固定コントローラーだけを構成する。本文末尾が展開・縮退・検索・IME時にも隠れないことをpreviewまたはUIテストで確認する。
- [x] 2.2 `BookmarkSheetHost`、Board/Threadの`optionalSheetContent`、`TabsBottomSheet`、`UrlOpenDialog`をRoot Box上の正しい描画順へ移し、ReplyPopupや各Sheetが固定コントローラーを覆うことをCompose UIテストで確認する。
- [x] 2.3 `BottomBarUtils.kt` と `BbsRouteScaffold.kt` の縮退progress/nested-scroll接続をstable tab key単位で保持し、各本文ページが自タブのprogressだけを更新し、新規タブは1f、削除タブは状態除去となるテストを追加する。
- [x] 2.4 `BbsRouteBottomBar.kt` の通常表示と`SearchBottomBar`切替を単一bottomBarへ接続し直し、`TextFieldValue.composition`、BackHandler、`imePadding`、navigation bar insetが既存どおりであることを既存テストと追加UIテストで確認する。

## 3. Pager連動タイトルカード

- [x] 3.1 `TabToolBar.kt` のタイトルカードと固定アクション群を分離し、既存の`ExpandedTitleActions`相当をsettled/current/隣接tabから再利用できるComposableに整理する。ブックマーク、タイトル、更新のcallbackと縮退時表示を既存同等に保つ。
- [x] 3.2 `BbsRouteScaffold.kt` と `TabToolBar.kt` の間へ同じ`PagerState`を渡し、current pageと前後一ページのカードだけをstable key付きで構成する。`getOffsetDistanceInPages`と本文Pagerの実page距離からtranslationを計算し、LTR/RTLで本文と同方向・同距離になるUIテストを追加する。
- [x] 3.3 `TabToolBar.kt` のツールバー全幅`LinearProgressIndicator`を削除し、各タイトル`Card`内の`Box`下端へCard幅のindicatorをoverlayする。縮退時の56dp高を増やさず、各カード自身の`isLoading`/`loadProgress`がカードと一緒に移動するテストを追加する。
- [x] 3.4 タイトルviewportだけをclipし、Board右側「スレ」、Thread左側「板」、下段アクション群にはPager offsetを適用しない。途中dragとfling中も固定要素の画面座標が変化しないUIテストを追加する。

## 4. 下部コントローラーによるPager操作

- [x] 4.1 `BbsRouteScaffold.kt` の`HorizontalPager.userScrollEnabled`を`false`へ固定し、下部コントローラー最外周へ同じ`PagerState`と`PagerDefaults.flingBehavior`を使う横方向`scrollable`を設定する。検索中と既存Thread popup条件では無効になることを検証する。
- [x] 4.2 本文上の横dragではページが動かず、コントローラーのカード・ボタン・下段ツール上の横dragでは本文とカードが指へ一対一追従するCompose UIテストを追加する。tapは既存click、touch slop超過後はdragとして成立することも検証する。
- [x] 4.3 `BbsRouteScaffold.kt` の`consumeTabSwipeByDragDirection`適用と実装、不要importを削除し、本文の縦スクロール、クリック、長押し、既存gesture処理が動作することを関連テストで確認する。

## 5. 画面種別ボタンと通常Navigation

- [x] 5.1 `strings.xml`へ表示文言「板」「スレ」とTalkBack用content descriptionを追加し、`TabToolBar.kt`、`BoardScaffold.kt`、`ThreadToolBar.kt`へ左右固定ボタンのenabled状態とcallbackを配線する。Compose semanticsテストで位置、ラベル、disabled状態を確認する。
- [x] 5.2 `BoardScaffold.kt` で`threadPresentationState`の`Selected`と同一snapshotの`ThreadTabInfo`から`AppRoute.Thread`を構築し、normalize→`registerAndSelectThreadRoute`→成功時`navigateToThreadScreen`の順でpushする。Loading/Empty/PendingMissing/登録失敗では遷移しない単体テストを追加する。
- [x] 5.3 `ThreadScaffold.kt` で`boardPresentationState`の`Selected`と同一snapshotの`BoardTabInfo`から`AppRoute.Board`を構築し、normalize→`registerAndSelectBoardRoute`→成功時`showBoardScreenForTabSelection(currentScreenRoute = threadRoute, route = boardRoute)`の順で現在Threadを置換する。Loading/Empty/PendingMissing/登録失敗では遷移せず、成功時はBackで破棄したThreadへ戻らないテストを追加する。
- [x] 5.4 `NavigationExtensionsTest.kt`へThread→Boardのreplaceテストを追加し、現在Threadが破棄されること、Deep Link等で背後にBoardがない場合もSelected Boardを表示できること、背後に別Boardがある場合はそのdestinationを変更しないことを確認する。既存タブ一覧シート・フルスクリーンタブ一覧のsurface置換も継続して検証する。

## 6. 回帰検証と品質確認

- [ ] 6.1 Board/Thread固有の検索、更新、ブックマーク、投稿、並び替え、自動スクロール、情報Sheet、ReplyPopupを操作し、callbackがsettled tabへだけ渡ることを追加テストまたは明記した手動確認手順で検証する。
- [ ] 6.2 drag中のtab削除・reorder、連続drag、drag cancel、1タブ、最初/最後のタブ、PendingMissing遷移で範囲外参照や暗黙のpage 0 fallbackが発生しないテストを追加する。
- [ ] 6.3 新規・変更class/interfaceと非自明関数へ規約どおりのKDocを付け、30行超の関数を区分コメントで整理したうえでAndroid Studio formatter相当の書式を確認する。
- [x] 6.4 `./gradlew compileDebugAndroidTestKotlin`、`./gradlew testDebugUnitTest`、`./gradlew assembleDebug`を順に実行し、全コマンド成功を記録する。
- [ ] 6.5 実機またはエミュレーターでLTR/RTL、gesture/3ボタンnavigation、IME表示、TalkBack、drag中の本文・カード追従と固定ツール群を確認し、specの全scenarioを満たすことを記録する。
