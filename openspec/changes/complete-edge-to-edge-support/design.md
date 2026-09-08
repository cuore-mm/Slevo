## Context

`MainActivity.kt` は `enableEdgeToEdge()` を呼び出しているが、Insets の適用は複数の階層へ分散している。ルートの `AppScaffold.kt` は `innerPadding` を一部ルートへ `parentPadding` として渡し、各画面は独自 Scaffold の既定 Insets、Material 3 コンポーネントの Insets、または個別 Modifier を組み合わせている。

現在の `AppScaffold.kt` は下部バーへ `navigationBarsPadding().height(56.dp)` を渡す。祖先の Insets padding は子の Material 3 Insets から消費されるため通常は二重加算されないが、固定 56dp は `NavigationBar` の標準最小高を圧縮する。また `BbsRouteScaffold.kt` は Scaffold の `innerPadding` を Pager 全体へ適用するため、Board／Thread のスクロール領域自体がシステムバーと下部バーの手前で切れる。

Activity は `MainActivity` のみで、`targetSdk` は35、`compileSdk` は36である。`adjustResize` は `MainActivity.kt` から実行時指定され、通常画面のシステムバー外観は status bar のみアプリテーマへ明示的に同期している。画像ビューアは `ImageViewerScreenEffects.kt` で独自の可視状態・外観・コントラスト設定を保存および復元している。

## Goals / Non-Goals

**Goals:**

- Insets の所有者をルート chrome、画面 chrome、スクロール内容、固定オーバーレイ、IME に分離する。
- Lazy リストの背景とスクロール領域をバーの背後まで延長し、先頭・末尾項目は安全位置に保つ。
- Material 3 アプリバーの標準寸法と Insets を使用する。
- 通常画面と画像ビューアの system bar 制御を競合させない。
- 各画面がどの padding を使用・消費するかをコード上の明示的な契約にする。

**Non-Goals:**

- Navigation 構成、画面遷移、ViewModel、データ層を変更しない。
- 下部ナビゲーションの項目、文言、アイコン、表示ルートを変更しない。
- 画像ビューアの没入表示ジェスチャーや画像描画仕様を変更しない。
- 新しいレスポンシブナビゲーションやタブレット向け複数ペインを導入しない。
- Material 3 または Compose の依存バージョン整理は本変更に含めない。

## Decisions

### 1. ルート Scaffold はアプリ chrome のみを所有する

`AppScaffold.kt` の `Scaffold` に限定して `contentWindowInsets = WindowInsets(0)` を設定する。このゼロ指定は全画面の安全領域を無効化するものではなく、ルート Scaffold が status/navigation bar Insets を本文へ付与しないための局所的な指定とする。

ルート Scaffold の content lambda が返す `PaddingValues` は、`parentPadding` ではなく「表示中のアプリ下部ナビゲーションが占有する領域」を表す `appChromePadding` として `AppNavGraph.kt` へ渡す。下部ナビゲーションを表示しないルートでは bottom 値は0となる。

代替案としてルート Scaffold の既定 Insets を維持する方式は、子 Scaffold の system bar Insets と `parentPadding` の意味が重なるため採用しない。アプリ全体へ `safeDrawingPadding()` を付ける方式は背景とスクロール領域を縮小するため採用しない。

### 2. Material 3 バーへ Insets と寸法を委譲する

`AppScaffold.kt` から下部バーへ渡している `navigationBarsPadding()` と `height(56.dp)` を同時に除去する。`NavigationBottomBar.kt` の `NavigationBar`、`SelectedBottomBar.kt` の `BottomAppBar` は既定 `windowInsets` を使用する。

`BbsRouteBottomBar.kt` でも通常・検索・選択状態の navigation bar Insets は `FlexibleBottomAppBar`／`BottomAppBar` に委譲する。外側 Modifier は IME 対応などコンポーネントが処理しない状態差分だけを持つ。`navigationBarsPadding()` だけを削除して固定高を残す変更は禁止する。固定高の内側へ Material 3 Insets が入り、内容領域をさらに圧縮するためである。

これにより下部ナビゲーションは現在のコンパクトな56dpから Material 3 標準寸法へ変わる。ユーザーは推奨案を承認しているため、標準寸法を製品決定として採用する。

### 3. 辺ごとに Scaffold padding の所有者を選択する

ネスト Scaffold 画面では padding を加算せず、次の優先規則で合成する。

- top/start/end: 画面 Scaffold が返す padding。
- bottom: `maxOf(画面 Scaffold の bottom, appChromePadding の bottom)`。
- Board／Thread の独自 BottomBar: その画面 Scaffold の bottom を使用し、ルート chrome は表示されないため加算しない。

この計算を重複実装しないよう、`ui/common` 配下に新しい `PaddingValues` 合成ユーティリティを追加する。RTL では `calculateStartPadding(layoutDirection)` と `calculateEndPadding(layoutDirection)` を使用する。ユーティリティ名とファイル名は実装開始時に既存の `ui/common` の命名規則を確認して確定し、単体テストで top/start/end の選択と bottom の最大値を検証する。

### 4. Lazy コンテナは padding ではなく contentPadding を使用する

`LazyColumn`／`LazyRow` は `Modifier.fillMaxSize()` で利用可能領域全体を占有し、合成済み `PaddingValues` を `contentPadding` に渡す。同時に `Modifier.consumeWindowInsets(combinedPadding)` を適用し、子孫が同じ値を再適用しないようにする。

優先対象は次の通り。

- `BoardScreen.kt`、`ThreadScreen.kt` と、それらへ padding を渡す `BbsRouteScaffold.kt`／Board・Thread の route/scaffold。
- `ServiceListScreen.kt`、`CategorisedBoardListScreen.kt`、`BoaredCategoryListScreen.kt` と `RegisteredBBSNavigation.kt`。
- Bookmark、History、Tabs の Lazy コンテナと各 Scaffold。

`BbsRouteScaffold.kt` は Pager 全体への `Modifier.padding(innerPadding)` を除去し、画面 Scaffold の padding を Board／Thread の content lambda まで渡す。Board／Thread の各 LazyColumn が contentPadding を所有する。これにより Pager とリスト背景は端まで描画され、静止時の項目だけが安全位置に置かれる。

### 4.1 タブ一覧の上部操作群と背景効果

`TabScreenContent.kt` の上部操作群は、背景の haze レイヤーと操作コンテンツの安全余白を分離する。haze レイヤーはステータスバーを含む上部全域から `topSearchHeight` の下端まで描画し、検索・その他ボタンだけに `contentPadding.calculateTopPadding()` を適用する。タブ一覧の Lazy コンテナには、同じ top inset、上部操作群の高さ、`listTopSpacing` を加えた値を `contentPadding.top` として渡し、最上部の先頭項目が操作群と重ならないようにする。

### 4.2 板・スレッドのスクロール補助UI

`BoardScreen.kt` と `ThreadScreen.kt` の LazyColumn は従来どおり `fillMaxSize()` と `contentPadding` を使用し、背景とスクロール領域をedge-to-edgeのまま維持する。通常スクロールバーはLazyColumnのcontent slotへ入れず、LazyColumnと兄弟の空contentオーバーレイとして配置する。オーバーレイのModifierに画面Scaffoldの `contentPadding.calculateTopPadding()` と `calculateBottomPadding()` だけを適用し、スクロールバーのトラックとつまみをステータスバーおよび下部ツールバーの内側へ収める。

スレッドの `MomentumBar` と隣接する `VerticalDivider` は、LazyColumnを縮めずに同じtop/bottom paddingを適用する。MomentumBarの描画、タップ、ドラッグ計算はpadding後の実測 `barHeight` を共通の座標系として使用し、一覧側のviewportを直接描画領域の高さとして扱わない。

### 4.3 板・スレッドのシステムバー保護と更新表示

`BbsRouteScaffold.kt` は画面全体の背景を端まで描画したまま、Scaffoldの上に操作を持たないステータスバー保護レイヤーを重ねる。レイヤーは `WindowInsets.statusBars` と同じ高さにし、テーマのsurface系色を半透明の縦グラデーションとして描画する。これによりステータスバーのアイコン視認性を保ちつつ、本文のedge-to-edge背景を切り詰めない。

`ThreadBottomRefreshIndicator.kt` はウィンドウ下端からの固定16dpではなく、`ThreadScreen.kt` が受け取った `contentPadding.calculateBottomPadding()` を下端余白として使用する。これには画面固有の下部ツールバーとnavigation barの占有領域が含まれるため、同じ値を別のbottom paddingとして加算しない。BoardのPull-to-refresh表示は上端側のcontentPaddingを考慮し、ステータスバーと重ならない位置に配置する。

### 5. 非 Lazy コンテンツは種類別に処理する

- `verticalScroll()` を使う Column は、スクロール Modifier の外側へ全体 padding を付けず、内容の先頭・末尾に安全領域相当の Spacer または内側コンテナ padding を置く。
- スクロールしない設定フォームなどは、Scaffold 自身が端まで背景を描画していることを確認したうえで `Modifier.padding(innerPadding)` を維持できる。
- `ReplyPopup` など画面端へ固定する操作 UI は `WindowInsets.safeDrawing` を一度だけ適用する。
- `BottomAlignedDialog.kt` は `decorFitsSystemWindows = false`、bottom safeDrawing、IME 対応を維持する。
- `ImageViewerScreenContent.kt` の局所的な `WindowInsets(0)` と TopBar／ThumbnailBar の明示的 Insets は、没入表示用の独立設計として維持する。余分な status bar 高の背景 Spacer がないか実装時に描画条件を確認する。

### 6. IME resize を Manifest 契約にする

`AndroidManifest.xml` の `MainActivity` へ `android:windowSoftInputMode="adjustResize"` を追加し、`MainActivity.kt` の `window.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)` と関連 import を削除する。

検索 BottomBar、`PostDialog.kt`、`BottomAlignedDialog.kt` は既存の `imePadding()` の位置と祖先で消費される Insets を確認する。同じ階層で IME を含む `contentWindowInsets` と `imePadding()` を併用しない。検索 BottomBar はジェスチャー／3ボタン判定で navigation bar padding を分岐せず、Material 3 の navigation bar Insets と IME 差分だけで動作させる。

タブ一覧の `Scaffold` は `WindowInsets.safeDrawing` を直接指定せず、Material 3 の `ScaffoldDefaults.contentWindowInsets`（system bar／cutout 用、IMEを含まない）を使用する。これにより、URL入力用 `AlertDialog` がIMEを所有している間も、ダイアログ背後の `TabListBottomControls` は navigation bar の位置に留まる。ダイアログ内の入力欄と確定操作は、標準 Dialog のウィンドウと必要なIME用余白で保護する。

### 7. 通常画面の system bar 外観を MainActivity で一元化する

`MainActivity.kt` の既存 SideEffect を、アプリの `isDarkTheme` に応じて status bar と navigation bar の両方のアイコン外観を設定する処理へ拡張する。API 29 以降では通常の下部バー背景がナビゲーション領域まで描画されるよう `isNavigationBarContrastEnforced = false` を設定する。

画像ビューアでは `ImageViewerScreenEffects.kt` が通常画面の値を保存して上書きし、終了時に保存値へ戻す既存契約を維持する。通常画面の Effect から画像ビューアの可視状態を直接操作しない。

### 8. UI 文言とアクセシビリティ契約を維持する

本変更では表示文言、contentDescription、ナビゲーション項目、フォーカス順序を変更しない。標準 Material 3 寸法へ戻すことで下部ナビゲーション項目のタップ領域を確保する。大きなフォント、横画面、カットアウトでもタップ対象とテキストがシステムバーに遮られないことを確認する。

## Implementation Contract

実装担当は次の順序と制約を守る。

1. `AndroidManifest.xml` と `MainActivity.kt` の IME／system bar 設定を更新する。`enableEdgeToEdge()` は削除しない。
2. `AppScaffold.kt` に局所的な `contentWindowInsets = WindowInsets(0)` を設定し、`navigationBarsPadding()` と `height(56.dp)` を同時に削除する。`AppNavGraph.kt` の `parentPadding` は `appChromePadding` へ改名し、意味を KDoc または非自明処理のコメントで明示する。
3. Insets 合成ユーティリティとその unit test を追加する。bottom は加算ではなく最大値を選ぶ。
4. Board／Thread を最初に移行し、Pager の padding を外して LazyColumn の `contentPadding` と `consumeWindowInsets` へ移す。スクロールバー／ミニマップは一覧と分離したオーバーレイとして上下の画面Insets内に配置し、更新インジケータは下部ツールバーの占有領域を避ける。BbsRouteScaffoldにはステータスバー保護レイヤーを追加する。
5. Tabs、Bookmark、History、BBS 一覧を同じ契約へ移行する。ルート下部ナビゲーションの有無ごとに bottom 値を確認する。
6. 設定、About、ライセンス、Dialog、BottomSheet、固定オーバーレイを棚卸しし、背景が Scaffold 全体で描画される画面の安全な既存 padding は不要に書き換えない。
7. `BbsRouteBottomBar.kt` の navigation mode 判定を Insets のためだけに使用しない構造へ整理し、検索欄の IME 開閉を確認する。
8. `MainActivity.kt` と `ImageViewerScreenEffects.kt` の system bar 所有範囲を確認し、画像ビューア終了時の復元を維持する。
9. 新規または変更する class/interface と非自明関数にはリポジトリ規約どおり KDoc を付け、30行超の関数はセクションコメントで分割する。Preview関数にはKDocを付けない。
10. 実装中に `WindowInsets(0)` を追加できるのは責務境界を明示できる Scaffold／専用没入画面だけとし、画面全体の回避策として追加しない。

## Error Cases and Compatibility

- API 24〜28では navigation bar contrast API を呼ばず、利用可能な system bar icon APIだけを使用する。
- API 29以降では3ボタンナビゲーションのコントラスト強制を無効化した状態でも、下部バー背景とアイコンが判読可能でなければならない。
- IME Insets が0へ戻るアニメーション中も固定検索欄を画面下端へ飛ばさず、Material 3 の navigation bar Insetsへ戻す。
- タブ一覧でURL入力ダイアログを表示してIMEが開いている間も、背後の固定下部操作群はIME高さをbottom paddingへ取り込まず、表示前と同じnavigation bar位置を維持する。
- 横画面では start/end の display cutout を画面 Scaffold側の paddingとして保持する。
- `appChromePadding.bottom` と画面 Scaffold の bottom を加算すると過剰余白になるため、必ず最大値を選ぶ。
- 画像ビューアの DisposableEffect が終了した場合は、変更前に保存した可視状態、アイコン外観、contrast値へ戻す。

## Testing Strategy

- Insets 合成ユーティリティを JUnit 4 で検証する。LTR／RTL、画面 bottom のみ、app chrome bottom のみ、両方ありの各ケースを含める。
- Compose instrumented test で下部 NavigationBar の項目が表示・クリック可能であり、固定56dp制約がないことを検証する。
- Board／Thread／Tabs／Bookmark／BBS一覧について、先頭・末尾項目を表示した状態の bounds が上部・下部操作領域と重ならないことを検証する。
- 検索 BottomBar と投稿 UI は IME 表示／非表示で入力欄と主要操作が表示領域内にあることを検証する。
- 画像ビューアを開閉し、通常画面の system bar 可視状態とアイコン外観が復元される既存テストまたは新規 instrumented test を追加する。
- 手動確認は API 29、34、35/36、ジェスチャー／3ボタン、ライト／ダーク、縦／横、カットアウト、最大フォントを含む。
- 最終確認として `./gradlew :app:assembleDebug :app:testDebugUnitTest` を実行し、接続端末またはエミュレータが利用できる場合は `./gradlew :app:connectedDebugAndroidTest` を実行する。

## Risks / Trade-offs

- [下部ナビゲーションが56dpから標準高へ変わり、本文表示量が減る] → 標準 Material 3 のタップ領域と Insets を優先し、主要ルートのスクリーンショットで差分を確認する。
- [PaddingValues の単純加算で余白が二重になる] → 辺ごとの所有規則と合成ユーティリティに限定し、組合せ単体テストを追加する。
- [Pager から padding を外した後、Board／Thread の初期項目がバーに隠れる] → padding を各 LazyColumn の contentPadding へ到達させる API 変更を同一コミット単位で行う。
- [IME と navigation bar のアニメーションで下部バーが跳ねる] → navigation mode 分岐を排し、IME差分と Material 3 Insets の単一経路にする。両ナビゲーション方式で開閉を確認する。
- [通常画面と画像ビューアが system bar 外観を競合更新する] → 通常状態は MainActivity、画面固有の一時上書きは ImageViewerScreenEffects とし、保存・復元境界を維持する。
- [Material 3 alpha版で既定 Insets 実装が変わる] → publicな `windowInsets` 契約を利用し、実装時に解決済み依存のAPIと実機表示を確認する。

## Migration Plan

1. Activity／ルート Scaffold／共通合成ユーティリティを導入する。
2. Board／Threadを移行し、独自 BottomBar と IME を検証する。
3. ルート BottomBar を使う Tabs／Bookmark／BBS一覧を移行する。
4. History、設定、About、Dialog、画像ビューアを回帰確認する。
5. unit test、instrumented test、端末マトリクスを完了して一括リリースする。

問題が発生した場合は、画面単位で旧 `Modifier.padding(innerPadding)` へ戻すのではなく、変更全体を戻して Insets 所有契約を一貫させる。永続データの移行はない。
