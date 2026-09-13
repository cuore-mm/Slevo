## Why

現在のNavigationBarは`AppScaffold`の外側`Scaffold`にあり、Board / ThreadへのNavigation開始時に先にcompositionから外れるため、NavHostの下余白とTabsカードのShared Bounds始点が遷移中に再測定される。NavigationBarをMainShell destinationの一部としてRoot遷移へ参加させ、画面履歴、Shared Transition、アプリ全体通知を維持したままこのレイアウトずれを解消する。

## What Changes

- Navigationを、MainShell / Board / Thread / Settings等を管理するRootNavHostと、Tabs / Bookmark / BBSサービス一覧群を管理するMainShellNavHostへ分割する。
- MainShellを`Scaffold`、NavigationBar、MainShellNavHostの所有者とし、NavigationBarをMainShell全体と同じRoot transition lifecycleで表示・退出させる。
- RootNavHostを一定の全画面boundsで測定し、NavigationBarの有無によって遷移中のRoot content paddingを変更しない。
- 既存の具体的な`AppRoute`型へRoot/MainShell所属を示すmarker interfaceを追加し、routeデータの二重定義、複雑なネストroute引数、カスタム`NavType`を導入しない。
- Board / Thread routeへserializableな遷移文脈を追加し、MainShell内の遷移元がTabsならShared Bounds、Bookmark / BBS一覧なら既存slideをBackおよび状態復元後も決定可能にする。
- 単一の`SharedTransitionLayout`をRootNavHostの外側に維持し、Tabsカードにはinner NavHostではなくRootのMainShell destinationの`AnimatedVisibilityScope`を渡してBoard / Threadページと照合する。
- Board / Threadから開いたcontextual Tabsの直接選択だけを既存source destinationへ畳み込み、TabsからBookmarkへ移動してBoard / Threadを開いた場合は中間履歴を保持する。
- pending restore結果などのアプリ全体SnackbarはRoot overlayで維持し、MainShell固有SnackbarおよびNavigationBarの所有権から分離する。
- Root Snackbarの下端回避量は表示中destinationのアプリ内下部chromeに応じてoverlayだけへ適用し、RootNavHostの測定boundsを変更しない。

## Capabilities

### New Capabilities

- `main-shell-navigation`: 二階層NavHost、MainShellによるNavigationBar所有、route所属、Root/MainShell間Navigation、およびMainShellを含むRoot遷移を定義する。

### Modified Capabilities

- `separated-board-thread-tab-navigation`: 二階層back stack上でも既存のBoard / Thread push・pop・replaceとcontextual Tabsの直接選択／Bookmark経由選択を区別して維持する。
- `pending-restore-result-notification`: MainShell外のBoard / Threadを含む全Root destinationで、Navigation中も単一のroot-level Snackbar通知を継続する。
- `edge-to-edge-layout`: Root SnackbarをNavigationBarまたはBoard / Thread下部ツールバーに遮られないoverlayとして配置し、下部chromeの変化でRootNavHostを再測定しない。

## Impact

- 主な対象は`AppScaffold.kt`、`AppNavGraph.kt`、`NavigationExtensions.kt`、`TransitionSpecs.kt`、`RenderBottomBar.kt`、`NavigationBottomBar.kt`、`TabsScaffold.kt`、`BbsRouteScaffold.kt`、`RegisteredBBSNavigation.kt`、`SettingsRoute.kt`、Deep linkおよびRoot/MainShell間Navigationの呼び出し元である。
- 既存の`AppRoute.Board` / `AppRoute.Thread`の画面データ引数は維持し、永続データ、Room、DataStoreのmigrationは追加しない。遷移文脈にはNavigation Composeが標準対応するenumだけを追加する。
- Root/MainShellの二つのback stack、entry ID guard、プロセス再生成、Hilt ViewModel owner、Shared Transition scope、Snackbar overlayの統合テストが必要になる。
- `add-tabs-bbs-page-shared-transition`および`replace-tabs-bottom-sheet-with-fullscreen-tabs`の未完了計画と実装を前提に、route履歴・Shared Bounds・テスト記述を本変更へ整合させる。
