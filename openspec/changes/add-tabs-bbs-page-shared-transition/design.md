## Context

`AppScaffold.kt`は`AppNavGraph`を単一の`SharedTransitionLayout`で包んでいる。Board / Threadでは`BbsControllerSharedBoundsKey`と`bbsControllerSharedBounds`がタイトルカード、画面種別ボタン、下段アクション行をBoard↔Thread切替時に接続するが、TabsカードとBBSページ全体を接続するkeyは存在しない。

`BbsRouteScaffold.kt`は1つのBoardまたはThread destination内に複数タブの`HorizontalPager`を持つ。navigation routeのidentityはdestinationを作成したタブを示す一方、実際の表示タブは`pagerState.settledPage`から解決した`settledTab`であり、同一destination内のタブ切替後は両者が異なり得る。現在のroot `Box`内では`Scaffold`と`BbsRouteStatusBarProtection`の後にBookmark sheet、任意overlay、URL dialogが兄弟として描画される。

Tabs↔Board / Threadには`TransitionSpecs.kt`の横slide＋fadeが適用される。contextual Tabsの同種選択はTabsをpopし、別種選択はTabsをpopした後に既存Board / Thread規則へ委譲するため、複数のback stack操作が連続する場合がある。

## Goals / Non-Goals

**Goals:**

- 選択したTabsカード1枚と、同じidentityを持つsettle済み現在表示ページのviewport全体をcontainer transformで接続する。
- 前後Pagerページやモーダルを含めず、現在画面に見えているページ本体だけを拡大・縮小する。
- Tabs↔BBSの横slideを除去し、Shared Boundsが成立しない場合も短いfadeで完了する。
- contextual Tabsの別種選択でも、選択カードと最終destinationを1回の可視Navigation transitionとして接続する。

**Non-Goals:**

- タイトルカードをTabsカードの個別遷移先にしない。
- Board↔Threadの既存タイトルカード、画面種別ボタン、下段アクション行のShared Boundsを置き換えない。
- `sourceRoute`、navigation route、list indexをページShared Boundsのidentityに使わない。
- Shared Transition専用の選択key、ViewModel state、フレーム待機、候補準備callbackを追加しない。
- Dialog、BottomSheet、Popup、Drawer、Snackbar、アプリ共通chromeを拡縮しない。
- route型、選択keyの永続化、Room / DataStore形式を変更しない。

## Decisions

### 1. コントローラーとは独立したページ専用keyとmodifierを追加する

`ui/common/transition/BbsPageSharedBounds.kt`を追加し、`BbsPageSharedBoundsKey.Board(identity)`と`BbsPageSharedBoundsKey.Thread(identity)`を定義する。Tabs板カードとBoard表示ページは正規化済みboard URL、TabsスレッドカードとThread表示ページは`ThreadId.value`を直接identityに使う。

既存`BbsControllerSharedBoundsKey`を流用すると、ページ内のタイトルカードや画面種別ボタンと同一keyの参加者が重複するため採用しない。String連結prefix、route、Pager indexをkeyにする案も、型分離とstable identityを失うため採用しない。

同ファイルに`Modifier.bbsPageSharedBounds`を追加する。内容が異なるカードとページコンテナを接続するため`sharedBounds`を使い、ページ全体を毎フレーム再測定せず画像的に拡縮する`scaleToBounds()`、約300msのbounds transform、短いenter / exit fadeを指定する。`enabled=false`では入力Modifierをそのまま返す。

### 2. Tabsでは各カードへ最初から一意なページkeyを付ける

`AppNavGraph.kt`のTabs destinationから既存`sharedTransitionScope`とNavHostの`this@composable`を、`TabsScaffold.kt`→`TabScreenContent.kt`→`TabsPagerContent.kt`→`OpenBoardsList.kt` / `OpenThreadsList.kt`へ伝播する。

`OpenBoardCard`の`TabListCard` root modifierには`Board(tab.boardUrl)`、`OpenThreadCard`には`Thread(tab.id.value)`を付ける。全カードが固有keyで参加しても、遷移先のsettled pageと同じkeyだけが自動的にmatchするため、タップ対象keyを別状態として保持しない。

HorizontalPagerのsettle済み現在ページだけを有効化するため、`TabsPagerContent.kt`は既存`isSharedTransitionCandidate(page, settledPage, isScrollInProgress)`を再利用する。検索用`AnimatedListContent`のcrossfade中はtarget display stateの通常一覧または検索一覧だけを有効にし、同一カードkeyが退出側と進入側へ重複しないようにする。カード単位では削除中、drag中、長押しPreview中、複数選択中を無効化する。

### 3. BBS側ではrouteではなくsettledTabで表示viewportを識別する

`BbsRouteScaffold.kt`へ、呼び出し元がページModifierを作るためのComposable lambdaを追加する。lambdaは既存generic `TabInfo`、Pagerがidleかを受け取り、入力ModifierへページShared Boundsを追加して返す。BoardScaffoldは`Board(tab.boardUrl)`、ThreadScaffoldは`Thread(tab.id.value)`を渡す。

`BbsRouteScaffold`は既に解決している`settledTab = tabs[pagerState.settledPage]`をlambdaへ渡す。`boardRoute` / `threadRoute`は使用しない。Pagerの横ドラッグ中、settled indexが一時的に範囲外、またはタブ一覧が空の場合はページShared Boundsを無効化する。

現在のroot `Box`内に新しい表示ページコンテナ`Box`を置き、その内側へ既存`Scaffold`と`BbsRouteStatusBarProtection`を移す。ページShared Bounds modifierはこのコンテナへ適用するため、viewport内の本文Pager、背景、下部ツールバー、タイトルカード、ステータスバー保護が一体で拡縮される。`BookmarkSheetHost`、`optionalSheetContent`、`UrlOpenDialog`は表示ページコンテナの後ろではなく、rootの兄弟として外側に残す。

`HorizontalPager`の前後ページは内部で事前composeされてもviewportにclipされ、個別のページShared Bounds keyを持たない。共有参加者はsettledTabで識別された表示ページコンテナ1つだけである。

### 4. `sourceRoute`はNavigation文脈に限定する

`sourceRoute`はTabs初期ページ、contextual Tabs判定、最終back stack変換にのみ使用する。Board / Thread側のpage keyは常に`settledTab`、Tabs側は常にカード自身から導出する。

これによりroute=Aで開いたdestination内のPagerがCへ移動した後にTabsを開いても、`Board(C)` / `Thread(C)`同士が照合される。route由来keyをfallbackにせず、settledTabを解決できなければ通常fadeへフォールバックする。

### 5. contextual Tabsの最終stackを1回の可視transitionで作る

`NavigationExtensions.kt`の`showBoardScreenFromTabs` / `showThreadScreenFromTabs`は、既存entry ID guardと最終stackを維持しつつ、選択カードから最終destinationまでの中間destinationを描画しない操作へ整理する。

| 遷移 | Navigation操作 | 最終stack |
|---|---|---|
| ルートTabs→Board / Thread | 既存navigate | `Tabs → target` |
| Board→Tabs→Board | Tabsを1回pop | 元Boardを再利用 |
| Thread→Tabs→Thread | Tabsを1回pop | 元Threadを再利用 |
| Board→Tabs→Thread | Tabsをinclusiveに除去する`popUpTo`付きThread navigate | `Board → Thread` |
| Thread→Tabs→Board、直下がBoard | TabsとThreadをBoardまで1回でpop | 直下Boardを再利用 |
| Thread→Tabs→Board、直下がBoard以外 | source ThreadとTabsをinclusiveに除去する`popUpTo`付きBoard navigate | ThreadをBoardへ置換 |

直下Boardの判定はTabs選択時のNavController back stackで`Tabs → Thread → Board`の連続entryを確認する。より古いBoardを探索・再利用しない。実装時は現在導入済みNavigation Compose APIでback stack snapshotを取得できることを先に確認し、公開APIで取得できない場合は実装を開始せず計画更新のblockerとして報告する。

タブ登録・選択は引き続きNavigation前に完了させる。操作中にTabsを離れた場合はentry ID guardで履歴変更を抑止する。連続タップ抑止が必要な場合もページkeyへ状態を混在させず、既存の操作guardまたは別UI操作状態として扱う。

### 6. Tabs↔BBSだけ横slideをfadeへ置き換える

`TransitionSpecs.kt`へTabsとBoard / Threadのroute組合せを判定する関数と、横移動を含まない短いfade-only transitionを追加する。`AppNavGraph.kt`のBoard、Thread、Tabs各destinationで、Tabs↔BBSの場合はenter / exit / popEnter / popExitにfade-onlyを選ぶ。

Board↔Threadの判定を優先して既存slide-onlyを維持し、ImageViewerのnull transition、Bookmark / BbsServiceGroupとTabs間の`None`、その他のdefault slide＋fadeを変更しない。Shared Boundsがmatchした要素は共有overlay上でbounds変形し、matchしない場合はfade-onlyがフォールバックになる。

## Implementation Contract

- `AppScaffold.kt`の既存`SharedTransitionLayout`を唯一の共有領域として使い、新しい`SharedTransitionLayout`を追加しない。
- ページkeyは新規`BbsPageSharedBoundsKey`に分離し、既存`BbsControllerSharedBoundsKey`、`bbsControllerSharedBounds`、`bbsControllerActionsSharedBounds`を変更しない。
- Board page identityは`settledTab.boardUrl`、Thread page identityは`settledTab.id.value`とし、`sourceRoute`、`boardRoute`、`threadRoute`、Pager indexを使わない。
- TabsカードはBoard=`tab.boardUrl`、Thread=`tab.id.value`を使い、全カードへ固有keyを付ける。Shared Transition専用selected keyをUiStateへ追加しない。
- `TabListCard.kt`内部を変更せず、`OpenBoardCard` / `OpenThreadCard`から既存root modifierへページShared Boundsを連結する。padding、shape、graphicsLayer、gesture、semanticsの既存値を変更しない。
- `BbsRouteScaffold.kt`では`Scaffold`と`BbsRouteStatusBarProtection`だけをページ共有コンテナへ含め、sheet、optional overlay、URL dialogを含めない。
- ページ共有コンテナは1destinationにつき1つとし、HorizontalPagerの各page itemへページkeyを付けない。
- Tabs PagerとBBS Pagerがscroll中、key解決不能、カード操作中、検索crossfade退出側ではShared Boundsを無効化する。
- Navigationの最終entry列、sourceRouteによる初期ページ、登録・選択順序、Tabs entry ID guardを維持する。内部操作を統合しても既存`NavigationExtensionsTest.kt`の最終stack期待を変えない。
- Tabs↔BBSでは横slideを使用しない。Board↔Thread、ImageViewer、その他destinationのtransitionを変更しない。
- 新しいclass/interfaceにはKDoc、非自明関数にはKDocとguard / fallbackコメントを付け、Preview関数にはdoc commentを追加しない。

## Error Cases / Compatibility

- settledTabを解決できない場合はページShared Boundsを付けず、fade-onlyで遷移する。
- Back先カードがviewport外、検索で除外、削除済み、または未composeの場合は別カードへ照合せずfade-onlyで戻る。
- 通常一覧と検索一覧がcrossfade中でもtarget側だけを有効にし、同一keyの複数参加者を作らない。
- BBS Pager横ドラッグ中は表示ページkeyを変更せずShared Boundsを無効にし、settle後のタブを次のidentityとする。
- contextual別種選択のstack統合に失敗した場合は中間destinationを表示する処理へ戻さず、Navigationを実行しないでエラーをテストで顕在化させる。
- ImageViewerのString key、ページkey、コントローラーkeyは型が異なるため相互照合しない。
- Dialog / BottomSheetはCompose Shared Transitionの対象外としてページコンテナ外に維持し、表示中にTabsを開けない既存操作条件を変更しない。

## Testing Strategy

- `BbsPageSharedBoundsKeyTest.kt`でBoard / Threadの同一identity、種別不一致、identity不一致、コントローラーkeyとの型分離を検証する。
- Android Compose testで小さいカードrootと全画面サイズのページrootが同じpage keyでmatchし、disabled / key不一致ではmatchしないことを検証する。
- `BbsRouteScaffoldSelectionTest.kt`でroute=A、settledTab=Cの場合にCをページidentityへ渡すこと、scroll中・範囲外では無効になることを検証する。
- `BbsRouteScaffoldTest.kt`で本文、下部ツールバー、ステータスバー保護がページコンテナ内、前後pageとsheet / dialog / popupが対象外であることをtest tagまたはmodifier hookで検証する。
- Tabs Compose testでsettle済み現在ページ、通常／検索target側、操作中除外を検証し、crossfade中に同一page keyが重複しないことを`isMatchFound` harnessで確認する。
- `TransitionSpecsTest.kt`でTabs↔Board / Threadだけがfade-only判定、Board↔Threadとその他routeが従来判定になることを検証する。
- `NavigationExtensionsTest.kt`で表の全stack変換、直下Boardあり／なし、entry ID不一致、pop失敗、非同期中Back、ルートTabsを検証する。
- 実NavHostまたは実機でBoard / Thread各カードの拡大、Back縮小、同種／別種contextual選択、先頭・中央・末尾・検索結果カード、viewport外fallbackを確認する。
- Macrobenchmarkまたは実機のProfile GPU Renderingで全画面overlayのjankを確認し、既存Board↔ThreadとImageViewerを回帰確認する。
- CIで`./gradlew build`と`./gradlew test`を実行し、追加したinstrumented Compose testも対象CIで成功させる。

## Risks / Trade-offs

- [全画面ページをoverlayで拡縮する描画負荷] → `scaleToBounds`を使い、実機フレーム確認でjankを検出する。
- [routeと表示タブのidentity不一致] → page keyをsettledTabだけから導出し、route=A / settled=Cを自動テストする。
- [前後Pagerページを含むように見える] → per-page itemではなくclipされたviewportコンテナ1つを共有し、Pager scroll中は無効化する。
- [ページkeyと既存タイトルkeyのネスト競合] → 型を分離し、Tabs↔BBSとBoard↔Threadを別々に統合テストする。
- [contextual別種選択の連続back stack操作で中間画面が見える] → 同じ最終stackを作る単一popまたは`popUpTo`付きnavigateへ統合する。
- [カードの角丸から矩形ページへのshape変化が自動補間されない] → 初回は既存カードclipとsharedBounds crossfadeを維持し、角の不連続が受入不能なら別changeでoverlay clip補間を設計する。
- [対応カードが未composeで逆遷移できない] → 自動スクロールや代替keyを使わずfade-onlyへフォールバックする。

## Migration Plan

1. ページ専用key / modifierとkey・matchテストを追加する。
2. `BbsRouteScaffold`の表示ページ本体とモーダル兄弟を分け、settledTab由来modifierを接続する。
3. Tabsへscopeを伝播し、各カードrootへpage keyと候補guardを追加する。
4. contextual Tabsのstack操作を1回の可視transitionへ統合し、最終stack回帰テストを通す。
5. Tabs↔BBSの横slideをfade-onlyへ置換し、全方向のCompose / NavHost testを追加する。
6. CIと実機で拡縮範囲、Back、fallback、jank、既存Shared Transitionを確認する。

ロールバック時はTabsとBBS page rootのpage modifier、page key型、Tabs↔BBS transition分岐、stack操作統合を除去し、既存Navigation関数と横slideへ戻す。永続データmigrationは不要である。
