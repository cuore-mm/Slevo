## 1. 実装前の契約確認

- [ ] 1.1 `./gradlew :app:dependencyInsight --dependency androidx.compose.material3:material3 --configuration debugRuntimeClasspath` で解決済み Material 3 バージョンを記録し、`NavigationBar`、`BottomAppBar`、`FlexibleBottomAppBar` の `windowInsets` API が設計どおり利用可能であることを確認する。
- [x] 1.2 `AppNavGraph.kt`、`RenderBottomBar.kt`、各 route/scaffold の BottomBar 表示条件を再確認し、ルート BottomBar を表示するルートと画面固有 BottomBar を表示するルートの一覧を実装コメントまたはテストケースへ反映する。

## 2. Activity とシステムバー基盤

- [x] 2.1 `app/src/main/AndroidManifest.xml` の `MainActivity` に `android:windowSoftInputMode="adjustResize"` を追加し、`MainActivity.kt` の `window.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)` と不要 import を削除する。Manifest merge結果または起動時のIME resizeで宣言が有効なことを確認する。
- [x] 2.2 `MainActivity.kt` の通常画面用 SideEffect で、`isDarkTheme` に応じて status bar と navigation bar のアイコン外観を設定し、API 29以降だけ `isNavigationBarContrastEnforced = false` を設定する。API 24〜28を参照できる分岐になっていることを単体ビルドで確認する。
- [ ] 2.3 `ImageViewerScreenEffects.kt` の保存・上書き・復元処理を通常画面の新しい値と照合し、ビューア終了時に status/navigation bar の可視状態、アイコン外観、contrast値が復元されるテストを追加または更新する。

## 3. ルート Scaffold と共通 Padding 契約

- [x] 3.1 `AppScaffold.kt` のルート `Scaffold` だけに `contentWindowInsets = WindowInsets(0)` を設定し、下部バーへ渡す `navigationBarsPadding()` と `height(56.dp)` を同時に削除する。`NavigationBottomBar.kt` と `SelectedBottomBar.kt` が Material 3 の既定 Insets と標準高で描画されることを PreviewまたはUIテストで確認する。
- [x] 3.2 `AppScaffold.kt`、`AppNavGraph.kt`、`TabsScaffold.kt`、Bookmark／History／RegisteredBBS の route引数にある `parentPadding` を `appChromePadding` へ改名し、「ルート下部ナビゲーションの占有領域だけ」を表すことをKDocまたは非自明処理のコメントで明示する。全呼び出し元がコンパイルすることを確認する。
- [x] 3.3 `ui/common` の既存命名を確認して `PaddingValues` 合成ユーティリティを追加し、top/start/endは画面Scaffold、bottomは `maxOf(screen, appChrome)` を返すよう実装する。LTR／RTLを `calculateStartPadding`／`calculateEndPadding` で扱う。
- [x] 3.4 `app/src/test` に合成ユーティリティのJUnit 4テストを追加し、LTR、RTL、screen bottomのみ、app chrome bottomのみ、両方あり、両方0の各ケースで辺ごとの値を検証する。

## 4. Board／Thread と画面固有 BottomBar

- [x] 4.1 `BbsRouteScaffold.kt` の Pagerへ付けた `Modifier.padding(innerPadding)` を除去し、Scaffoldの `PaddingValues` をBoard／Threadのcontent lambdaまで明示的に渡す。Pager背景がウィンドウ端まで描画されることを確認する。
- [x] 4.2 Board側のroute/scaffoldと `BoardScreen.kt` を更新し、受け取ったpaddingを `LazyColumn.contentPadding` と `Modifier.consumeWindowInsets` に適用する。先頭と末尾の板項目がstatus barとBottomBarに隠れず、スクロール描画がバー背後まで続くことを確認する。
- [x] 4.3 Thread側のroute/scaffoldと `ThreadScreen.kt` を更新し、受け取ったpaddingを `LazyColumn.contentPadding` と `Modifier.consumeWindowInsets` に適用する。先頭と末尾のレス、ミニマップ、`ReplyPopup` がstatus bar、BottomBar、safeDrawing領域と重ならないことを確認する。
- [x] 4.4 `BbsRouteBottomBar.kt`、`TabToolBar.kt`、`SearchBottomBar.kt`、選択BottomBarから外側の navigation bar paddingと固定高を除去し、Material 3バーへ委譲する。Insets目的の3ボタン／ジェスチャー分岐を削除し、通常・選択・検索の全状態でバー背景が画面下端まで描画されることを確認する。
- [ ] 4.5 Board／Threadの検索モードでIMEを開閉し、検索欄がIME表示中と非表示時の両方で操作可能かつ余分な下余白を残さないCompose UIテストを追加する。

## 5. ルート BottomBar 配下の一覧画面

- [x] 5.1 `TabScreenContent.kt` と `TabsScaffold.kt` を更新し、局所的なInsets責務を明示したうえで、タブ一覧の先頭・末尾と固定Top／Bottom controlsへ `appChromePadding` と画面Insetsを一度だけ適用する。スクロール領域がバー背後まで続くことを確認する。
- [x] 5.2 `BookmarkListScaffold.kt` とお気に入りのLazyコンテナを更新し、合成paddingを `contentPadding` と `consumeWindowInsets` に移す。通常・選択モードでTopAppBarとルート／選択BottomBarに項目が隠れないことを確認する。
- [x] 5.3 `RegisteredBBSNavigation.kt`、`ServiceListScreen.kt`、`CategorisedBoardListScreen.kt`、`BoaredCategoryListScreen.kt` を更新し、画面Scaffoldと `appChromePadding` の合成値を各 `LazyColumn.contentPadding` へ渡す。3画面すべてで先頭・末尾と横画面のstart/end安全領域を確認する。
- [x] 5.4 `HistoryListScaffold.kt` と履歴のLazyコンテナを更新し、ルートBottomBar非表示時は画面Scaffoldのbottom Insets、選択モード時は選択BottomBarの占有領域だけが適用されることを確認する。

## 6. その他画面とオーバーレイの棚卸し

- [x] 6.1 Settings系7画面、`AboutScreen.kt`、`OpenSourceLicenseScreen.kt` のスクロール方式と背景所有者を確認し、LazyコンテナはcontentPaddingへ移行し、`verticalScroll`は内容内の先頭・末尾余白へ移行する。非スクロール画面はScaffold背景が端まで描画される場合のみ既存paddingを維持する。
- [ ] 6.2 `BottomAlignedDialog.kt`、`PostDialog.kt`、TextFieldを含む標準Dialog／AlertDialog、`SlevoBottomSheet.kt` を確認し、safeDrawingとIMEが同じ辺へ二重適用されないよう修正する。各入力欄と主要確定操作がIME表示中に到達可能であることをUIテストまたは端末確認で検証する。
- [x] 6.3 `ThreadScaffold.kt` の `ReplyPopup` など固定オーバーレイを全件確認し、重要なタップ対象だけに `safeDrawing` を一度適用し、背景コンテナへ全体safe paddingを追加していないことをコード検索で確認する。
- [x] 6.4 `ImageViewerScreenContent.kt`、`ImageViewerTopBar.kt`、`ImageViewerThumbnailBar.kt` の局所的なゼロInsetsと明示Insetsを維持し、status bar高の背景要素が重複描画されていないか確認して必要な場合だけ条件を修正する。

## 7. 回帰テストとアクセシビリティ

- [x] 7.1 `app/src/androidTest` にedge-to-edge用Composeテストを追加し、下部NavigationBarの全項目が表示・クリック可能で、標準Material 3高を56dpへ制約するModifierがないことを検証する。
- [ ] 7.2 Board、Thread、Tabs、Bookmark、BBS一覧の代表ケースで、先頭・末尾項目のboundsがTopAppBar、BottomBar、システム操作領域と重ならないテストを追加する。
- [ ] 7.3 既存の文言、contentDescription、フォーカス順序が変わっていないことを関連Composeテストで確認し、最大フォントでも下部ナビゲーション項目と主要操作が切り取られないことを確認する。
- [ ] 7.4 API 29、34、35または36の端末／エミュレータで、ジェスチャー／3ボタン、ライト／ダーク、縦／横、カットアウト、IME開閉の確認表を実施し、各組合せで背景連続性、重要UI、二重余白、system bar視認性を記録する。

## 8. 最終検証

- [x] 8.1 `WindowInsets(0)`、`navigationBarsPadding`、`statusBarsPadding`、`safeDrawingPadding`、`imePadding`、`Modifier.padding(innerPadding)` をコード検索し、各残存箇所がdesign.mdの所有規則に一致することを確認する。
- [x] 8.2 変更したclass/interfaceと非自明関数のKDoc、30行超関数のセクションコメント、Preview関数にKDocを付けない規約を確認する。
- [x] 8.3 `./gradlew :app:assembleDebug :app:testDebugUnitTest` を実行して成功させる。CI workflow の `testCiUnitTest`／`assembleCi` 相当検証で成功を確認した。
- [ ] 8.4 接続端末またはエミュレータで `./gradlew :app:connectedDebugAndroidTest` を実行して成功させる。実行環境がない場合は未実施理由と手動確認結果を記録する。

> 8.4 未実施記録: 現在の Android CI workflow に接続テスト job がなく、利用可能な接続端末／エミュレータもないため実行していない。手動端末確認も未実施。
