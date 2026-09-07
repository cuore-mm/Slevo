## Context

提案の背景は`proposal.md`を参照する。現在、`AppScaffold.kt`の単一`SharedTransitionLayout`が`AppNavGraph`を包み、`AppNavGraph.kt`は各Board/Thread destinationの`BoardScaffold`と`ThreadScaffold`へ`SharedTransitionScope`とdestination固有の`AnimatedVisibilityScope`を渡している。画像遷移とタイトルカード・画面種別ボタンのShared Boundsはこの構成を使用しているが、下部コントローラーの`BottomActionsRow`にはshared modifierが届いていない。Board/ThreadのNavigationは`TransitionSpecs.kt`の300ms slide+fadeである。

Board/Thread共通の`BbsRouteScaffold.kt`はタイトルカードをPagerの`currentPage - 1..currentPage + 1`の範囲で最大3枚composeする。タイトルカードはページごとの`Modifier`を`BoardTabTitleCard`または`ThreadTabTitleCard`から`TabTitleCard`のroot `Card`へ渡せる。一方、`TabToolBarHeader`から`TabDestinationIconButton`へ渡すModifierは内部で固定され、Board/Thread Scaffoldから注入できない。

Boardの安定タブidentityは`BoardTabInfo.boardUrl`、Threadの安定タブidentityは`ThreadTabInfo.id.value`である。ただしNavigation時には設定に応じて5ch.net routeを5ch.ioへ正規化し、保存済みnetタブと遷移先ioタブが別identityになる場合がある。Shared Transitionは装飾的機能として誤接続を避け、同じidentityが両側に存在しなければ既存Navigationのみを実行する。

## Goals / Non-Goals

**Goals:**

- 2組の視覚要素をタブidentity単位の`sharedBounds`で接続する。
- 位置・サイズ・異なる内部コンテンツの変形に適したcontainer transformを構成する。
- Pager内の重複キーを防ぎ、settle済みタイトルだけを候補にする。
- Board/Thread固有コードでshared key生成とModifier設定を重複させない。
- 既存のアクセシビリティラベル、クリック領域、無効状態を維持する。
- Board↔Thread間のNavigationからfadeだけを外し、既存方向・300msのslideを維持する。
- Board/Threadで内容が異なる下段ツール群をRow全体のクロスフェードで切り替える。

**Non-Goals:**

- `AppRoute.Board`と`AppRoute.Thread`の統合。
- Board↔Thread以外のNavHost transition、back stack操作、route引数の変更。
- TabSessionStore、各Coordinator、ViewModel、Pagerの選択・スクロール処理の変更。
- 同種タブPagerのドラッグをShared Transitionとして扱うこと。
- 検索・縮退アニメーション、ImageViewer、PostDialog、ReplyPopupのShared Transition変更。
- Shared Transitionを成立させるために保存済みnet/ioタブを統合すること。

## Decisions

### 1. 共有キーは表示対象種別を型で表す

`app/src/main/java/com/websarva/wings/android/slevo/ui/common/transition/BbsControllerSharedBounds.kt`を追加し、次の形のsealed keyを定義する。

```kotlin
sealed interface BbsControllerSharedBoundsKey {
    data class Board(val identity: String) : BbsControllerSharedBoundsKey
    data class Thread(val identity: String) : BbsControllerSharedBoundsKey
}
```

`destination`という名称は現在のNav destinationと誤解されるため使用しない。sealed型によりBoard種別へThread identityを渡すような組み合わせをレビューしやすくする。各型にはリポジトリのコメント規約に従うKDocを付ける。

対応表は次のとおりとする。

| 画面 | UI要素 | key |
|---|---|---|
| Board | settle済みBoardタイトルカード | `Board(tab.boardUrl)` |
| Board | 有効なThreadボタン | `Thread(selectedThread.id.value)` |
| Thread | 有効なBoardボタン | `Board(selectedBoard.boardUrl)` |
| Thread | settle済みThreadタイトルカード | `Thread(tab.id.value)` |

代替案の`"board"`と`"thread"`だけの固定String keyは、最大3枚のPagerタイトルが同時composeされるため採用しない。タイトル文字列もロード後の更新や重複があるため採用しない。

### 2. identityはTabInfoから直接取得し、キー層でURL正規化しない

Shared Transition用コードは`BoardTabInfo.boardUrl`と`ThreadTabInfo.id.value`をそのまま利用する。NavigationのURL正規化ロジックをキーファクトリへ複製せず、TabSessionStoreや永続タブのidentity契約も変更しない。

5ch.netから5ch.ioへのNavigation正規化によりsourceボタンとdestinationタイトルのidentityが異なる場合、Composeのキー照合を成立させず通常のNav transitionへフォールバックする。この場合でも同じ表示対象種別の別タブへ固定キーで誤接続しないことを優先する。

完全一致のために正規化後routeを事前計算してUI stateへ保持する案は、Navigation準備状態とTabSessionStore更新順を変更するため採用しない。

### 3. 共通Composable Modifierで`sharedBounds`設定を統一する

`BbsControllerSharedBounds.kt`に、`SharedTransitionScope`、`AnimatedVisibilityScope`、型付きkey、`enabled`を受け取るComposable Modifierヘルパーを定義する。ヘルパーは有効時だけ`rememberSharedContentState(key)`と`sharedBounds`を適用し、無効時は元のModifierを返す。

- 異なる内容を持つCard同士なので`sharedElement`ではなく`sharedBounds`を使用する。
- タイトルTextのリフローを避けるため`resizeMode = ScaleToBounds()`を明示する。
- `renderInOverlayDuringTransition`は指定せず、Shared Transitionの標準overlay描画を使用する。
- 初期実装では`zIndexInOverlay`、`clipInOverlayDuringTransition`、`renderInSharedTransitionScopeOverlay`を追加しない。実機またはテストで具体的なz-order/clip不具合を再現した場合だけ別変更として検討する。

shared modifierはタイトルカードと画面種別ボタンのroot `Card`に適用する。Shared Boundsの両側でModifier順序を揃え、shared modifierの後に`fillMaxSize`または`fillMaxHeight`などのサイズModifierが評価されるように組み立てる。内部のText、Icon、bookmark、refresh、progressには個別のshared modifierを付けない。

### 4. Pagerからsettle済み候補情報だけをタイトルslotへ渡す

`BbsRouteScaffold.kt`のタイトルカードslot引数へBooleanの候補情報を追加する。候補は`page == pagerState.settledPage && !pagerState.isScrollInProgress`の場合だけtrueとする。必要なら判定を既存の`BbsRouteScaffoldSelectionTest.kt`からテストできるinternal pure functionへ抽出する。

このBooleanはshared modifierの有効・無効にだけ使用する。`HorizontalPager`、`PagerState`、`snapshotFlow { settledPage }`、`onTabSelected`、`controllerModifier`、rubber-band overscrollの処理順や値は変更しない。

全タイトルへidentity付きkeyを付ける案はキー自体の重複を避けられるが、ドラッグ中の隣接カードがdestinationボタンと照合される可能性があるため採用しない。

### 5. 画面種別ボタン専用のModifier注入口を追加する

`TabToolBar.kt`の`TabToolBar`と内部`TabToolBarHeader`へ`destinationModifier: Modifier = Modifier`を追加し、`TabDestinationIconButton`のroot `Card`まで渡す。`TabDestinationAction`へModifierを格納するとアクション内容と描画関心が混在するため採用しない。

`BoardToolBar.kt`と`ThreadToolBar.kt`も`destinationModifier`を受け取り、共通`TabToolBar`へ委譲する。既存Previewとテスト呼び出しはデフォルト値で互換を保つか、明示的に`Modifier`を渡す。

Board/Thread Scaffoldでは、既に受け取っている2つのscopeと選択済みTabInfoから次を構築する。

- `BoardScaffold`: Boardタイトルに`Board(tab.boardUrl)`、Threadボタンに`Thread(selectedThread.id.value)`。
- `ThreadScaffold`: Boardボタンに`Board(selectedBoard.boardUrl)`、Threadタイトルに`Thread(tab.id.value)`。

ボタン側は対応TabInfoが解決済みで既存`canOpenBoard`または`canOpenThread`がtrueの場合だけ有効にする。タイトル側はDecision 4の候補Booleanがtrueの場合だけ有効にする。

### 6. 下段ツール群をRow全体で共有する

`BbsControllerSharedBoundsKey`に`data object ActionsRow`を追加する。`BottomActionsRow`は各destinationのsettled tabに対して1つだけcomposeされるため、タイトルPagerのような複数同時composeによるidentity衝突は発生しない。既存の`Board`/`Thread`キーとは型を分け、タイトルカードや画面種別ボタンと誤照合しない。

`BbsControllerSharedBounds.kt`へRow専用helperを追加し、`rememberSharedContentState(ActionsRow)`、`sharedBounds`、`enter = fadeIn(tween(300))`、`exit = fadeOut(tween(300))`、`boundsTransform = BoundsTransform { _, _ -> tween(300) }`、`resizeMode = scaleToBounds()`を共通設定する。`renderInOverlayDuringTransition`は指定せず、標準overlayを使う。Row helperはBoard/Thread ScaffoldからModifierとして注入し、共通`BottomActionsRow`へ無条件に付与しない。

Rowではshared modifierを先に置き、その後に`fillMaxWidth`、`height`、既存`graphicsLayer`を置く。`graphicsLayer`の`actionsProgress`によるalpha/translationYは維持し、`clampedProgress <= 0f`の早期returnと検索時の通常Toolbar非composeも変更しない。

### 7. Board↔ThreadだけNavigationをslide-onlyにする

`TransitionSpecs.kt`に既存300msと同じ方向のslide-only enter/exit/pop関数を追加する。`AppNavGraph.kt`では各Board/Thread destinationのenter、exit、popEnter、popExitで、初期destinationと対象destinationがBoard/Threadの組み合わせの場合だけslide-onlyを返す。Thread側の既存ImageViewer判定を先に評価し、ImageViewerのNavigation挙動を変えない。Board/Thread以外の組み合わせは既存`default*Transition()`を返す。

この条件はNav transitionのstateから直接判定し、visibleEntriesや独立したremember stateを追加しない。push、pop、replaceのNavigation操作とTabSessionStore、Pager、検索・縮退状態は変更しない。

### 8. Navigationと既存Shared Transitionの互換性

`AppScaffold.kt`の`SharedTransitionLayout`、`NavigationExtensions.kt`のpush/pop/replaceは変更しない。`AppNavGraph.kt`のBoard/Thread transition分岐だけはDecision 7に従って変更する。新しいshared key型は画像用`ImageSharedTransitionKeyFactory`とnamespaceを共有せず、画像系ファイルにも変更を加えない。

検索時に`BbsRouteBottomBar`が通常コンテンツをcomposeしていなければ対応keyは存在せず、Shared Boundsなしで既存遷移を続ける。縮退状態では表示中の同じroot Cardへ適用されるため、現在のサイズから遷移する。

## Data Flow

1. `AppNavGraph`がdestinationの2つのscopeを`BoardScaffold`または`ThreadScaffold`へ渡す。
2. Scaffoldが`TabPresentationState.Selected`から解決済み`selectedBoard`または`selectedThread`を取得する。
3. `BbsRouteScaffold`が各タイトルページについてsettle済み・非ドラッグ中かを算出し、タイトルslotへ渡す。
4. ScaffoldのタイトルslotがTabInfo identityとscopeからタイトルCard用Modifierを作る。
5. Scaffoldのbottom barが他画面種別の選択済みTabInfo identityとscopeからボタン用Modifierを作り、`BoardToolBar`または`ThreadToolBar`へ渡す。
6. Scaffoldが同じscopeからRow用Modifierを作り、`BoardToolBar`または`ThreadToolBar`経由で`BottomActionsRow`へ渡す。
7. Nav transition中、同じ型とidentityのkeyが両destinationに存在する場合だけComposeがタイトル・ボタンのroot Cardを照合し、`ActionsRow` keyが両destinationに存在する場合だけ下段Rowを照合する。
8. key不一致または対象非表示の場合、該当shared pairは成立せず、Board↔Threadではslide-only、その他では既存Nav transitionだけが進行する。

## Implementation Contract

- アプリ・テストコードの変更は上記の対象ファイルと必要な呼び出し元に限定する。
- `BbsControllerSharedBoundsKey`のプロパティ名に`destination`を使用しない。
- Board identityは`BoardTabInfo.boardUrl`、Thread identityは`ThreadTabInfo.id.value`から取得し、タイトル文字列、ページindex、raw route引数を使用しない。
- Shared Transition用コード内で5ch.net/io正規化を実行しない。
- shared keyが一致しない場合のフォールバックのために、別identityのkeyへ置換しない。
- `sharedBounds`には`ScaleToBounds()`を指定し、overlayはデフォルト設定を維持する。
- shared modifierは`TabTitleCard`と`TabDestinationIconButton`のroot `Card`だけへ付ける。
- タイトルのshared modifierはsettle済みかつ非ドラッグ中の場合だけ有効にする。
- ボタンのshared modifierは対応するTabInfoを解決でき、既存の遷移可否が有効な場合だけ付ける。
- `AppRoute`、Navigation helper、TabSessionStore、Coordinator、ViewModel、Pagerのselection処理を変更しない。NavHost transitionはBoard↔Threadのslide-only分岐だけを追加する。
- ImageViewer、PostDialog、ReplyPopupのshared key、scope伝播、有効化条件、overlay設定を変更しない。
- 新しいclass、interface、data class、sealed interfaceと非自明関数にはリポジトリ規約に従うKDocを付け、Preview関数にはKDocを付けない。

## Error Cases / Compatibility

- [選択済みTabInfoが未解決] → ボタンは既存どおりdisabledとし、shared modifierを付けない。
- [Pagerドラッグ中] → タイトル候補を無効にし、Pagerのドラッグとsettleだけを実行する。
- [検索UIが通常ツールバーを置換] → composeされていない要素を補完せず、Shared Boundsなしで遷移する。
- [下段Rowが完全縮退] → 既存の早期returnを維持し、Row用Shared BoundsなしでBoard↔Threadのslide-onlyを継続する。
- [net/io正規化でidentity不一致] → 誤マッチさせず通常Nav transitionへフォールバックする。
- [既存back stack destinationのPagerが目的ページへ未同期] → 対応タイトルが存在しなければそのpairをスキップし、Pager同期処理は変更しない。
- [複数タブ] → 型とstable identityの組み合わせでキーを一意にし、固定キーを使用しない。
- [アクセシビリティ] → 既存Cardのclickable、enabled、ラベル、content descriptionを保持し、shared modifierによる新しい操作要素を追加しない。
- [API互換性] → 変更対象はアプリ内Composable APIであり、追加引数には可能な箇所でデフォルト値を設ける。永続形式と外部APIは変更しない。
- [Navigationの対象外destination] → Board↔Thread判定に一致しない場合は既存のdestination別transitionを返す。

## Testing Strategy

### JVM unit tests

- 新しいkey型について、同じ型・identityは等しく、型またはidentityが異なれば等しくないことを検証する。
- `BbsRouteScaffoldSelectionTest.kt`で、settle済みかつ非ドラッグ中のページだけが候補になり、隣接ページとドラッグ中ページは候補にならないことを検証する。
- 既存のTabSessionStore、Board/Thread Coordinator、Pager計算テストを変更せず成功させる。

### Compose instrumented tests

- `SharedTransitionLayout`と`AnimatedContent`を使う小さなテストハーネスで、Board key同士およびThread key同士が開始・途中・終了時にbounds変形することを検証する。
- 異なる型またはidentityではshared pairが成立しないことを検証する。
- `TabToolBarTest.kt`でdestination Modifier追加後もクリック、disabled semantics、可視ラベル、タイトルカード操作が維持されることを検証する。
- `BbsRouteScaffoldTest.kt`で横ドラッグ中の選択通知回数、settle後のタブ、タイトルカード外ツール群の固定を回帰確認する。
- `TabToolBarTest.kt`でRow用Modifierが下段Rowへ届き、個別action buttonへ付与されないことを検証する。
- `AppNavGraph`のtransition判定について、Board↔Threadの4方向がslide-only、他destinationとImageViewerが従来分岐であることを検証する。

### Build and manual acceptance

- `./gradlew :app:testDebugUnitTest`でunit testを実行する。
- `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`でアプリとinstrumented test sourceをビルドする。
- 実機またはemulatorでBoard→Thread push、Thread→Board pop、Thread→Board replaceを、展開・縮退の両状態で確認する。
- Pagerドラッグ中、検索中、遷移先ボタンdisabled、net/io正規化発生時に既存操作が維持されることを確認する。
- Thread画像→ImageViewer→Threadの往復を確認し、既存画像Shared Transitionに視覚回帰がないことを確認する。

## Migration Plan

永続データ移行は不要。共通key/helper、Composable引数、Board/Thread適用、テストの順に実装する。問題が発生した場合は新しいshared modifierと引数伝播を削除すれば、既存Navigationと状態管理だけの動作へ戻せる。
