## Context

`AppScaffold.kt`は現在、単一の`NavHostController`、ルート`Scaffold`、`RenderBottomBar`、`PendingRestoreResultSnackbar`を所有する。`Scaffold.bottomBar`にあるNavigationBarは`SharedTransitionLayout`と旧単一graphの外側にあり、Board / Threadへ遷移するとdestinationより先に非表示となってNavHostへ渡す下余白を変更する。TabsカードからBBSページへのShared Boundsはこの再測定の影響を受ける。

旧`AppNavGraph.kt`は`AppRoute.Tabs`をstart destinationとし、Tabs、Bookmark、BBSサービス一覧、Board、Thread、Settings、History、ImageViewerを同じback stackで管理していた。Board / Threadから開くTabsは直前entryを`sourceRoute`として解決し、`NavigationExtensions.kt`がTabs選択後のpush / pop / replaceを行う。route定義は`AppRoute.kt`へ分離し、graph本体は`RootNavGraph.kt`と`MainShellNavGraph.kt`へ分割する。

`PendingRestoreResultSnackbar`はActivityスコープの`PendingRestoreResultViewModel`から供給され、現在のrouteに関係なく表示を継続するアプリ全体通知である。Board / Threadは`BbsRouteScaffold.kt`内に独自の下部ツールバーを持つため、Root Snackbarの回避対象はNavigationBarだけではない。

既存routeは`AppRoute`配下の`@Serializable`な具象型で、引数はprimitive、String、List等のNavigation標準型だけを使用する。runtimeのNavigation Compose 2.8.9では、別のroute data classを引数として包むとcustom `NavType`が必要になる。

## Goals / Non-Goals

**Goals:**

- RootNavHostの測定boundsをNavigationBarの表示状態から切り離す。
- NavigationBarをMainShellの一部としてRoot transitionへ参加させる。
- RootとMainShellの二つのback stackで既存の可視履歴と状態復元を再現する。
- Tabs↔Board / Thread、Board↔Thread、ImageViewerの既存Shared Transitionを同一`SharedTransitionLayout`内で維持する。
- contextual Tabsの直接選択だけを履歴統合し、Bookmark / BBS一覧へ移動した後の履歴を保持する。
- 復元結果SnackbarをRootに残し、アプリ内下部chromeを回避してもRootNavHostを再測定しない。

**Non-Goals:**

- タブ選択、登録、並び替え、検索、BookmarkまたはBBS一覧の表示内容を変更しない。
- Board / Thread内のPager、下部コントローラー、画面種別Shared Boundsを再設計しない。
- Navigation 3への移行、依存バージョン更新、custom `NavType`導入を行わない。
- ユーザー向け文言、NavigationBar項目、content description、フォーカス順を変更しない。
- Room、DataStore、バックアップ形式へmigrationを追加しない。

## Decisions

### 1. `AppScaffold`をRoot overlay containerへ変更する

外側`Scaffold`を廃止し、`AppScaffold.kt`を概ね次の構造へ変更する。

```text
Box
├─ SharedTransitionLayout
│  └─ RootNavGraph(rootNavController)
│     ├─ MainShell
│     ├─ Board
│     ├─ Thread
│     ├─ History / Settings / About
│     └─ ImageViewer
├─ PendingRestoreResultSnackbar / SnackbarHost
└─ MoreMenuDialog等のRoot overlay
```

RootNavHostへNavigationBar由来のcontent paddingを渡さず、常にwindow全体を使って測定する。`hasRootBottomBar`でRoot content paddingを切り替える方式は削除する。代替案として外側`Scaffold`を残す方法は、bottomBarの出入りで同じ再測定を起こすため採用しない。

### 2. MainShellが`Scaffold`とNavigationBarを所有する

新しい`MainShell.kt`は`Scaffold`の`bottomBar`で既存`NavigationBottomBar`を描画し、contentに`MainShellNavGraph`を置く。MainShellの`innerPadding`はMainShell内destinationだけへ渡す。Board / Threadは従来どおり`BbsRouteScaffold`自身の下部ツールバーとInsetsを所有する。

Root start destinationは新しい`AppRoute.MainShell`とする。通常のMainShellとBoard / Threadから開くcontextual MainShellを、serializableな`MainShellMode`で区別する。Board / Threadの既存ジェスチャー・オーバーフローメニューからBookmarkまたはBBS一覧を開く場合は、`MainShellStartDestination`をrouteへ保存して対象inner画面を初期表示する。

```kotlin
@Keep
@Serializable
enum class MainShellMode { Base, ContextualTabs }

@Keep
@Serializable
enum class MainShellStartDestination { Tabs, BookmarkList, BbsServiceGroup }
```

各Root MainShell entryは自身の`rememberNavController()`を持つ。Rootのsaveable state holderにより、base MainShellとcontextual MainShellのinner back stackをentryごとに分離して保存・復元する。同じ`NavHostController`を同時に複数のMainShellへ接続する案は、Root transition中に二つのNavHostがcomposeされ得るため採用しない。

### 3. 既存`AppRoute`具象型へ所属markerを付ける

`AppRoute`の画面データを`RootRoute.Board(route: AppRoute.Board)`のように包まない。`RootRoute`と`MainShellRoute`をmarker interfaceとして追加し、既存具象型がどちらかを実装する。

```kotlin
sealed interface RootRoute
sealed interface MainShellRoute

@Serializable
data class Board(/* 既存引数 */, val entryTransition: BbsEntryTransition = Default) :
    AppRoute(), RootRoute

@Serializable
data object Tabs : AppRoute(), MainShellRoute
```

marker interface自体を`composable<T>`または`navigation<T>`のdestinationには使用しない。`composable<AppRoute.Board>`のように具象型だけを登録する。これにより既存のroute FQCNとデータ定義を維持し、custom `NavType`を不要にする。

### 4. destinationをRootとMainShellへ移す

旧`AppNavGraph.kt`のroute定義を`AppRoute.kt`へ分離し、graph本体を`RootNavGraph.kt`へ整理する。次をRootへ登録する。

- `AppRoute.MainShell`
- `AppRoute.Board`
- `AppRoute.Thread`
- History
- `SettingsRoute.kt`のSettings graph
- About / OpenSourceLicense
- ImageViewer

新しい`MainShellNavGraph.kt`へ次を登録する。

- `AppRoute.Tabs`
- Bookmark list
- `RegisteredBBSNavigation.kt`のBBSサービス、カテゴリ、板一覧graph

MainShell内画面へRoot controllerを直接渡さず、`onOpenBoard`、`onOpenThread`、`onOpenSettings`等のcallbackを渡す。Root Navigationのstack変換はRoot用extensionまたはcoordinatorへ集約し、MainShell内top-level切替はMainShell用extensionへ分離する。Board / ThreadからMainShell内のBookmarkまたはBBS一覧を開く操作は、Root controllerへの直接navigateではなく、初期inner destination付きMainShellをpushするcallbackへ接続する。

責務とViewModel ownerは次のとおり整理する。

| 所属 | destination | 主なComposable / ViewModel owner |
|---|---|---|
| Root | `MainShell` | `MainShell` entryごとのinner `NavHostController`、inner画面のViewModelは各inner entry |
| Root | Board / Thread | `BoardScaffold` / `ThreadScaffold` のRoot entry、各 `hiltViewModel()` |
| Root | History / Settings / About / ImageViewer | `HistoryListScaffold`、`addSettingsRoute`、About、ImageViewerの各Root entry |
| MainShell | Tabs | `TabsScaffold` / `TabScreenContent` のinner entry |
| MainShell | Bookmark | `BookmarkListScaffold` のinner entry |
| MainShell | BBSサービス・カテゴリ・板一覧 | `addRegisteredBBSNavigation` 内の各inner entry |

### 5. entryへ遷移文脈enumを保存する

Root transition lambdaはMainShellのinner destinationを直接参照しない。Board / Threadを開く要求時に次のenumをroute argumentへ保存する。

```kotlin
@Keep
@Serializable
enum class BbsEntryTransition {
    TabsSharedBounds,
    MainShellSlide,
    BoardThreadSlide,
    DeepLink,
    Default,
}
```

enumはNavigation標準の`NavType.EnumType`で扱えるためcustom `NavType`を追加しない。`@Keep`を付け、minify後もenum名を保持する。Board / Threadの既存引数は変更せず、末尾へdefault付きフィールドを追加する。

Root transitionはinitial / target entryの`BbsEntryTransition`とroute種別から決定する。

- MainShell(Tabs)↔BBS: Tabs Shared Bounds用の横slideなしtransition
- MainShell(Bookmark / BBS一覧)↔BBS: 既存default slide＋fade
- Board↔Thread: 既存slide-only
- Thread↔ImageViewer: 既存null Navigation transitionと画像Shared Element
- その他: 既存default

現在inner routeをmutable stateでtransition lambdaから参照する案は、Back、連続操作、プロセス再生成で遷移元が失われるため採用しない。

### 6. TabsページShared BoundsにはRoot scopeを渡す

`SharedTransitionLayout`は`AppScaffold`に一つだけ維持し、RootNavHost全体を包む。Rootの`composable<AppRoute.MainShell>`から得る`AnimatedVisibilityScope`を、次の経路でTabsカードまで渡す。

```text
RootNavGraph MainShell destination scope
→ MainShell
→ MainShellNavGraph
→ TabsScaffold
→ TabScreenContent
→ TabsPagerContent
→ OpenBoardsList / OpenThreadsList
→ TabListCard.bbsPageSharedBounds
```

MainShellNavHost自身の`AnimatedVisibilityScope`はMainShell内部transitionだけに使い、BBSページShared Boundsへ渡さない。Board / Thread側はRoot destinationのscopeを使うため、両端が同じRoot AnimatedContent transitionへ参加する。

`BbsControllerSharedBoundsKey`とImageViewer keyは従来どおりRoot SharedTransitionScope内で扱う。MainShellのNavigationBar、Root Snackbar、DialogはBBSページShared Boundsへ含めない。

### 7. contextual MainShellは直接Tabs選択だけを統合する

Board / ThreadからTabsを開くと、Rootへ`AppRoute.MainShell(MainShellMode.ContextualTabs)`をpushし、そのentryのMainShellNavHostをTabsから開始する。Tabs選択要求はRoot MainShell entry ID、inner Tabs entry ID、選択元`MainShellBbsOrigin`を持つ。

```kotlin
enum class MainShellBbsOrigin { Tabs, Bookmark, BbsServiceGroup }
```

Root stackが`X → Board A → MainShell(ContextualTabs)`で、現在inner entryがTabsかつ`origin == Tabs`なら、Board Bへのnavigateで`popUpTo(Board A, inclusive = true)`を指定し、最終stackを`X → Board B`にする。Thread同種および別種選択は既存`NavigationExtensions.kt`の最終stack規則を同じRoot stack上で再現する。

contextual MainShell内でBookmarkへ移動した場合、inner stackを`Tabs → Bookmark`として保持する。BookmarkからBoard Bを開く要求は`origin == Bookmark`なので通常pushし、Root=`X → Board A → MainShell(ContextualTabs) → Board B`を維持する。BackはBoard BをpopしてBookmarkを表示し、次のBackでinner BookmarkをpopしてTabs、さらにBackでcontextual MainShellをpopしてBoard Aへ戻る。

contextualであることだけを履歴統合条件にせず、Root MainShell entry ID、現在inner entry ID、`origin == Tabs`をすべて確認する。登録中にどちらかのentryが変わった場合はNavigationを中止する。

### 8. base MainShellとcontextual MainShellでtop-level履歴規則を分ける

base MainShellのNavigationBarは既存の`launchSingleTop`、`saveState`、`restoreState`を維持する。contextual MainShellではTabsからBookmark / BBSサービスへの移動をinner back stackへ積み、BackでTabsへ戻れるようにする。これによりユーザー確定済みの`X → Board → Tabs → Bookmark → Board`を畳み込まない。

NavigationBarの選択表示は各MainShell entryの現在inner destinationから導出する。contextual MainShellからbase MainShellのcontrollerを直接操作しない。

### 9. 復元結果SnackbarをRoot overlayとして維持する

`pendingRestoreSnackbarHostState`、`PendingRestoreResultSnackbar`、acknowledge callbackは`AppScaffold`に残す。MainShellの`Scaffold.snackbarHost`へ移動しない。Root overlayの`SnackbarHost`はRootNavHostと兄弟にして、MainShell、Board、Thread間のNavigation中も同じinstanceを維持する。

Root overlay専用の下端回避状態を設ける。値は現在Root destinationに応じてMainShell NavigationBarまたは`BbsRouteBottomBar`の占有高さとsafe drawing bottomを表す。Snackbarの`Modifier.padding`または`offset`だけに適用し、RootNavHostまたはMainShellのcontent paddingには使用しない。下部chromeの実測値を通知する場合は、同値更新を抑止し、Snackbar非表示時の更新もRoot contentを再測定しない構造にする。

MainShell固有Snackbarは実際の通知要件が追加されるまで作らない。将来追加する場合もRoot Snackbarとは別の`SnackbarHostState`を使う。

## Implementation Contract

- 編集開始前に`AppScaffold.kt`、`AppRoute.kt`、`RootNavGraph.kt`、`MainShellNavGraph.kt`、`RenderBottomBar.kt`、`NavigationExtensions.kt`、`RegisteredBBSNavigation.kt`、`SettingsRoute.kt`の最新route登録とcontroller受け渡しを再確認する。
- `SharedTransitionLayout`は`AppScaffold.kt`に一つだけ置き、RootNavHostを包む。MainShellNavHost内へ二つ目を追加しない。
- RootNavHostへNavigationBar由来のpaddingを渡さず、MainShellの`Scaffold.innerPadding`はMainShell内画面だけへ適用する。
- `pendingRestoreSnackbarHostState`と`PendingRestoreResultSnackbar`はRoot/AppScaffold所有を維持し、MainShellへ移動しない。
- Root Snackbarのbottom offset変更をRootNavHostのsize、padding、constraintsへ反映しない。
- 既存`AppRoute`具象型を別route data classへ包まない。marker interfaceを所属確認に使い、custom `NavType`を追加しない。
- `BbsEntryTransition`と`MainShellMode`には`@Keep`と`@Serializable`を付け、既存route引数へdefault値を追加する。
- `MainShellStartDestination`には`@Keep`と`@Serializable`を付け、既存の`AppRoute.MainShell()`呼び出しがTabs開始のままになるdefault値を持たせる。
- Root/MainShell graphへ登録するのは具象routeだけとし、sealed marker interfaceをdestinationとして登録しない。
- base MainShellとcontextual MainShellは別Root entryとして別々の`NavHostController`を所有し、一つのcontrollerを複数NavHostへ同時接続しない。
- TabsページShared BoundsへRoot MainShell destinationの`AnimatedVisibilityScope`を明示伝播し、MainShellNavHost destination scopeで置き換えない。
- Tabsからの直接選択を統合する前に、Root MainShell entry ID、inner Tabs entry ID、現在route、登録確認結果を検証する。
- Bookmark / BBSサービス起点のBoard / Thread Navigationではcontextual sourceをpopせず通常pushする。
- Board↔Thread、ImageViewer、Deep link、タブ登録・選択順序、Pager selection source of truthを変更しない。
- 新しいclass/interface/enumにはKDocを付け、非自明関数にはKDoc、guard、fallback、stack変換のコメントを付ける。Preview関数にはdoc commentを追加しない。
- 新しいユーザー向け文言やcontent descriptionは追加しない。NavigationBarの既存ラベル、選択セマンティクス、タップ領域を維持する。

## Error Cases / Compatibility

- Rootまたはinner entry IDが一致しない非同期完了は、選択key更新済みでもNavigationを実行しない。
- contextual MainShellの直下が期待するBoard / Threadでない場合、推測で古いentryを探索せずNavigationを中止してテストで検出する。
- プロセス再生成でMainShell inner back stackが復元できない場合、contextual履歴を誤ってbaseへ統合せず、保存済みRoot entryの初期routeへフォールバックする。
- Shared Boundsのsourceカードまたはtargetページがcomposeされていない場合、別identityへ接続せずTabs↔BBSの通常fallback transitionを使う。
- routeへdefault付きenumを追加する。旧プロセス状態の復元互換性はinstrumented testで確認し、decode不能なら新route型を包まず既存引数との互換serializer方針を計画更新してから実装を続行する。
- runtime Navigation Compose 2.8.9とnavigation-testingのバージョン差による挙動差を避けるため、production graphを用いたinstrumented testでも検証する。
- Root Snackbarの下端回避量を取得できない場合はsafe drawing bottomへフォールバックし、Root contentを動かさない。下部アプリバーとの重なりはUI testで失敗として検出する。

## Testing Strategy

- Root/MainShellのroute所属を純粋なgraphテストで検証し、MainShell controllerからRoot route、Root controllerからMainShell内部routeを直接navigateしないことを確認する。
- 実`NavHostController`を使い、base MainShellのTabs / Bookmark / BBSサービス切替でRoot stackが増えず、saveState / restoreStateされることを確認する。
- `NavigationExtensionsTest.kt`を二階層stackへ拡張し、`X → Board → contextual Tabs → Board`が`X → Board`、`X → Board → contextual Tabs → Bookmark → Board`が保持されることとBack順を検証する。
- Board / Thread同種、別種、直下Boardあり／なし、Root Tabs、entry ID不一致、登録中Backの全既存ケースを移行する。
- `TransitionSpecsTest.kt`で`BbsEntryTransition`の全値、push / pop、Board↔Thread優先、ImageViewer例外を検証する。
- Compose UI testでNavigationBarのboundsを遷移開始前後に測定し、MainShell→Board / Thread中にNavigationBarだけが先に消えず、RootNavHost boundsが変わらないことを確認する。
- Shared Transition harnessまたは実NavHost testで、TabsカードとBBSページがRoot scopeでmatchし、inner scopeでは照合しないことを確認する。
- Root SnackbarをMainShell、Board、Threadで表示し、遷移中も同じ通知が継続すること、NavigationBar / BBS下部ツールバー / system navigation領域と重ならないことをbounds assertionで確認する。
- Activity再生成と可能ならprocess recreation testでbase/contextual双方のinner stack、`BbsEntryTransition`、Root Snackbar候補を復元する。
- 実機でジェスチャーナビゲーション、3ボタン、縦横画面についてNavigationBarの退出、Shared Bounds、Snackbar offset、Back順を確認する。
- 実装後は`./gradlew build`と`./gradlew test`を成功させ、追加したinstrumented testを対象CIまたは接続端末で実行する。

## Risks / Trade-offs

- [Root transition中に同じMainShell controllerが二つのNavHostへ接続される] → Root entryごとにcontrollerを生成し、base/contextual stateを分離する。
- [inner scopeを使ってTabs Shared Boundsが不成立になる] → Root MainShell scopeを型付きparameterで明示伝播し、match testで固定する。
- [二階層化で既存flat stack変換を再現できない] → Root/innerを合成した期待履歴表をNavigation testの正本にし、直接Tabsケースだけを統合する。
- [route enum追加で保存済みback stackを復元できない] → default値、`@Keep`、再生成testを先に追加し、互換性を確認してから全呼び出し元を移行する。
- [Root Snackbarが下部chromeと重なる] → destination別bottom occlusionをoverlayだけへ反映し、bounds testを追加する。
- [Snackbar offset stateが頻繁に更新される] → 同値更新を抑止し、RootNavHostから独立した小さいoverlay subtreeだけを再composeする。
- [Hilt ViewModel ownerがRootからinner entryへ変わる] → 各destinationの現行ownerを一覧化し、期待するentryで`hiltViewModel`を取得する統合テストを追加する。
- [既存アクティブOpenSpecと記述が競合する] → Shared Boundsと全画面Tabsの未完了taskを本変更のRoot/MainShell構成へreconcileしてから実装完了扱いにする。

## Migration Plan

1. route marker、MainShell route、遷移文脈enumとserializer / graph単体テストを追加する。
2. RootNavGraphとMainShellNavGraphを作成し、NavigationBarをMainShellへ移動する。Root SnackbarはAppScaffold overlayへ残す。
3. Tabs / Bookmark / BBSサービス群をMainShell graphへ、Board / Thread / Settings / History / ImageViewerをRoot graphへ接続する。
4. Root/MainShell間callbackと二階層Navigation extensionを追加し、base MainShellのtop-level state restorationを移行する。
5. contextual MainShellと直接Tabs選択のstack統合、Bookmark / BBS経由の履歴保持、entry ID guardを実装する。
6. Root MainShellのAnimatedVisibilityScopeをTabsカードへ伝播し、既存BBSページ、Board↔Thread、ImageViewer Shared Transitionを再接続する。
7. Root transitionを`BbsEntryTransition`で分岐し、既存transition回帰テストを移行する。
8. Root Snackbarのbottom occlusion overlayを実装し、MainShell / Board / ThreadのInsetsとboundsを検証する。
9. Deep link、Activity再生成、process state restoration、実機のBackと遷移を検証する。
10. `add-tabs-bbs-page-shared-transition`と`replace-tabs-bottom-sheet-with-fullscreen-tabs`の設計・taskを新しい二階層構成へreconcileする。

ロールバック時はRoot/MainShell graph分割、marker、MainShell route、遷移文脈を除去し、単一`AppNavGraph`と外側`Scaffold.bottomBar`へ戻す。永続データmigrationはない。route引数追加後の旧版へのロールバックではAndroid保存済みNavigation stateを引き継げない可能性があるため、リリース前に復元互換性を確認し、問題がある場合は当該変更を同一リリース内でロールバックする。
