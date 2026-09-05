## Context

See `proposal.md` - Why. 現在の `BbsRouteScaffold` は `rememberPagerState` で同種タブ用状態を作り、`HorizontalPager` の各ページ内に `Scaffold`、`bottomBar`、本文、`BookmarkSheetHost`、`optionalSheetContent` を構成する。`TabToolBar` 全体がページに属するため、横移動時にはタイトルカードと下部アクションが一緒に動く。

本文には `consumeTabSwipeByDragDirection` が付き、Pager の `userScrollEnabled` は現在ページの `BaseUiState.isTabSwipeEnabled` に依存する。選択通知、固定表示対象、スクロール位置保存の active 判定はいずれも `PagerState.currentPage` を参照しており、`currentPage` がドラッグ途中で切り替わると settle 前に副作用が発生する。

`TabToolBar` のロード進捗はタイトル `Card` の外側で、`FlexibleBottomAppBar` と同階層の全幅 `LinearProgressIndicator` として描画されている。Board/Thread の検索 UI は `BbsRouteBottomBar` が通常ツールバーと `SearchBottomBar` を切り替え、縦スクロールによる縮退はページ内 Scaffold の `nestedScroll` が `rememberBottomBarActionVisibility` を更新する。

Compose Foundation は BOM 2026.02.00 配下の Pager を利用している。`PagerState` は `ScrollableState` であり、Pager 自身のユーザージェスチャーを無効化しても同じ状態を外部のスクロール入力から操作できる。

## Goals / Non-Goals

**Goals:**

- 本文 Pager を唯一のページ位置状態とし、下部コントローラーから直接操作する。
- 描画中の連続位置と確定済みページを分離し、settle 後だけ選択副作用を実行する。
- タイトルカードを独立して移動可能な表示単位にし、カード内情報とロード進捗を同じタブへ結び付ける。
- 固定コントローラーへ再編しても、検索、縦スクロール縮退、シート、ポップアップ、スクロール位置復元を維持する。
- 既存 route、タブの stable key、coordinator、repository の契約を変更しない。

**Non-Goals:**

- `AppRoute.Board` と `AppRoute.Thread` の統合または引数変更。
- `TabSessionStore` の selected key や canonical/pending reconciliation の再設計。
- タブ一覧画面の Board/Thread 切替 Pager の操作変更。
- Board の「スレ」ボタンからスレッド一覧の先頭や閲覧履歴を選ぶ新しい推薦処理。
- 検索、投稿、ブックマーク、更新処理そのものの変更。

## Decisions

### 1. 単一 Scaffold の content に本文 Pager、bottomBar に固定コントローラーを置く

`BbsRouteScaffold` のページ内 `Scaffold` を除去し、ルート直下に一つの `Scaffold` を構成する。`content` slot には既存 `HorizontalPager` とページ固有本文だけを置き、`bottomBar` slot には settled page の状態で描画する `BbsRouteBottomBar` を一つだけ置く。

Root は `Box` とし、単一 Scaffold の後に settled page の `BookmarkSheetHost` と `optionalSheetContent`、共通の `TabsBottomSheet` と `UrlOpenDialog` を描画する。これにより現在の「ページ固有 overlay が bottom bar も覆う」重なり順を維持する。Scaffold の `innerPadding` は各本文へ適用し、固定コントローラーの展開・縮退、検索 UI、IME、navigation bar insetに応じて本文末尾が隠れないようにする。

代替案のページ内 Scaffold を残したまま固定バーを重ねる方式は、本文 bottom padding、IME inset、overlay の z-order を手動で二重管理するため採用しない。

### 2. 本文 Pager の直接ジェスチャーを無効にし、固定コントローラーへ同じ PagerState の scrollable を付ける

`HorizontalPager.userScrollEnabled` は常に `false` とする。下部コントローラーの最外周に横方向の `Modifier.scrollable` を設定し、本文と同じ `PagerState` と `PagerDefaults.flingBehavior(pagerState)` を渡す。`enabled` は settled page の `isTabSwipeEnabled` と Thread popup の既存制約から導出する。

この方式ではコントローラー内のボタンやカードの click は touch slop 未満で成立し、横ドラッグへ移行した場合は同じ scrollable が処理する。本文の Pager を掴ませるための `consumeTabSwipeByDragDirection` は不要になるため、関数と適用箇所を削除する。

代替案の `pointerInput`、`dispatchRawDelta`、独自 velocity 計算は、RTL、nested scroll、fling、MutatorMutex、キャンセル処理を再実装するため採用しない。タイトル用の第二 PagerState も同期競合を生むため作らない。

### 3. 描画用ページと副作用対象ページを分離する

本文とタイトルカードの連続描画には `currentPage`、`currentPageOffsetFraction`、または `getOffsetDistanceInPages(page)` を使用する。次の処理は `snapshotFlow { pagerState.settledPage }` と `distinctUntilChanged` を通し、settle 後の index が現在の tabs の範囲内であることを確認してから実行する。

- `onTabSelected(tab)` による `TabSessionStore` の selected key 更新
- 固定ツール群が参照する tab と UiState の切替
- `ObserveScrollPositionPersistence.isActive` の切替と離脱時保存
- 現在タブ用 sheet/popup host の切替

selected key から Pager を同期する既存 `scrollToPage`、`animateToPageFlow` の `animateScrollToPage` は維持する。これらの programmatic 操作も最終的な settled page だけを同じ通知経路で確定する。`PendingMissing` では既存どおり programmatic scroll と選択通知を抑止し、最後に有効だった表示を維持する。

### 4. タイトルカード列を Pager の実ページ距離で平行移動する

固定コントローラーのタイトル領域は clip された viewport とし、現在ページと隣接ページのカードだけを stable key 付きで構成する。各カードの相対位置は同じ `PagerState.getOffsetDistanceInPages(page)` と本文 Pager の `layoutInfo.pageSize + pageSpacing` からピクセルへ変換する。カード自身の幅を移動単位にしてはならない。本文の一ページ分の移動距離を使うことで、本文とカードを指の移動へ一対一で追従させる。

高頻度の offset は可能な限り `graphicsLayer` または layout modifier の更新フェーズで読み、全コントローラーの再コンポーズを避ける。実装時に LTR と RTL の両方で本文と同方向へ動くことを確認し、方向変換は `LayoutDirection` と採用した scrollable の reverse direction に一箇所で集約する。

全タブの UiState Flow を常時購読するとタブ数に比例して負荷が増えるため、タイトルカードの構成対象は現在ページと前後一ページを基本とする。タブ増減直後に index が範囲外となるカードは描画しない。

### 5. タイトルカード内下端へロード進捗を重ねる

`TabToolBar.ExpandedTitleActions` の `Card` 内を `Box` とし、既存のブックマーク・タイトル・更新を含む `Row` と、`Alignment.BottomCenter` の `LinearProgressIndicator` を重ねる。進捗は `fillMaxWidth` でカード幅だけを使用し、Card の shape で clip する。Column の追加要素として高さを消費させず、縮退時の 56dp とタイトル垂直位置を維持する。

各カードはそのタブ自身の `isLoading` と `loadProgress` を受け取る。現在のツールバー全幅の進捗描画は削除する。これにより隣接カードが見えた場合も、ロード状態がカードと一緒に移動し、固定ツール群へ残らない。

### 6. タイトルカード外の要素は settled page に固定する

Board はタイトル viewport の右に「スレ」、Thread は左に「板」の固定ボタンを置く。既存の下段 `BottomActionsRow`、タブ一覧、投稿などタイトルカード外の操作要素も Pager offset を適用しない。ドラッグ中は最後に settle したタブの action callback と縮退 progress を維持し、settle 完了後に新しいタブへ一度に切り替える。

縦スクロール縮退はタブごとに保持する。`BottomBarUtils.kt` の action visibility state/connection を、stable tab key で管理できる形へ分離し、各本文ページの nested scroll connection が自タブの progress だけを更新する。固定コントローラーは settled tab key の progress を読む。タブ削除時は不要な一時状態を除去し、新規タブは 1f の全表示で開始する。

検索モードでは既存の `BbsRouteBottomBar` による `SearchBottomBar` 切替を維持し、`isTabSwipeEnabled == false` によりコントローラーの横スクロールを停止する。IME composition は既存の `TextFieldValue` をそのまま渡す。

### 7. 「スレ」はpush、「板」は現在Threadを破棄する置換遷移とする

Board の「スレ」は `TabSessionStore.threadPresentationState` の同一 snapshot から `Selected` key と一致する `ThreadTabInfo` を取得し、完全な `AppRoute.Thread` を構築する。既存パターンと同じく `normalizeThreadRouteForNavigation`、`registerAndSelectThreadRoute` を順に完了し、index が 0 以上の場合だけ `navigateToThreadScreen` を呼ぶ。`Loading`、`Empty`、`PendingMissing` ではボタンを disabled とし、不完全 route や先頭タブ fallbackを作らない。

Thread の「板」は `TabSessionStore.boardPresentationState` の同一snapshotから `Selected` key と一致する `BoardTabInfo` を取得し、完全な `AppRoute.Board` を構築する。`normalizeBoardRouteForNavigation`、`registerAndSelectBoardRoute` を完了した後、`showBoardScreenForTabSelection(currentScreenRoute = threadRoute, route = boardRoute)` を呼ぶ。既存の `replaceCurrentScreen` が現在Threadを `popUpTo(inclusive = true)` で破棄してSelected Boardを表示するため、`navigateToBoardScreen`によるpushは行わない。`Loading`、`Empty`、`PendingMissing`ではボタンをdisabledとする。

Board「スレ」は `navigateToThreadScreen` によりback stackへ積み、戻る操作で元Boardへ戻れるようにする。Thread「板」はタブ一覧の別種別選択と同じ `showBoardScreenForTabSelection` により現在Threadだけを置換し、破棄したThreadへ戻らない。Threadの背後に別Boardが存在する場合、その背後destinationは変更せず、Selected Boardを現在Threadの置換先として表示する。Deep Link等で背後にBoardがない場合も同じreplace経路を使用する。クリックの多重実行はnavigation helperの`launchSingleTop`と登録完了待ちに従い、登録または選択が失敗した場合は遷移しない。

表示文字列「板」「スレ」と content description は resource 化する。短い表示ラベルだけに依存せず、TalkBack で遷移先の画面種別が分かる説明を付ける。disabled 時も状態を意味的に公開する。

## Implementation Contract

実装担当は次の境界を維持すること。

1. `BbsRouteScaffold.kt` の `rememberPagerState` は一つだけとし、本文 `HorizontalPager`、コントローラー `scrollable`、タイトル offset の全てへ同一 instance を渡す。
2. `HorizontalPager.userScrollEnabled` を `false` にし、`consumeTabSwipeByDragDirection` の呼び出しと実装を削除する。本文に別の横ドラッグ切替を追加しない。
3. `currentPage` は連続描画にだけ使用する。`onTabSelected`、固定 bar の tab/UiState、scroll persistence active、page固有 overlay の切替には有効な `settledPage` を使用する。
4. `TabPresentationState.PendingMissing` 中は既存表示を保持し、page 0 fallback、selected key 上書き、反対種ボタンからの不完全 route 遷移を行わない。
5. `TabToolBar.kt` の全幅 progress indicator を削除し、各タイトル Card 内の bottom overlay として移す。ブックマーク・更新・タイトルの既存 callback と loading semantics を保持する。
6. タイトル offset 用の別 `PagerState`、別 Pager、offset同期用 coroutineを追加しない。表示対象は stable key で識別し、tab reorder/close時に誤った UiState を再利用しない。
7. `BbsRouteBottomBar` の検索切替、`BottomBarUtils.kt` の縦縮退、`BookmarkSheetHost`、Board/Thread の `optionalSheetContent` を単一 Scaffold 構造へ接続し直し、固定 bar より上に overlay を描く。
8. Board の「スレ」はSelected `ThreadTabInfo`だけを対象とし、normalize、register-and-select、push navigateの順序を省略しない。Threadの「板」はSelected `BoardTabInfo`だけを対象とし、normalize、register-and-select、`showBoardScreenForTabSelection`による現在Threadの置換順序を省略しない。
9. 新規または変更する class/interface、非自明関数にはリポジトリの KDoc 規約を適用し、30行を超える関数は処理区分コメントで分割する。

## Error Cases and Compatibility

- tabs が drag/animation 中に削除・reorderされた場合、page index を stable key へ再解決し、範囲外 index の callback、UiState取得、タイトル描画を行わない。
- settle前に presentationが `PendingMissing` へ移行した場合は選択通知を抑止し、coordinator の補正後 snapshot から再同期する。
- 反対種タブがLoading/Empty/PendingMissingの場合、Board「スレ」またはThread「板」を無効化する。最後の既知tabやindex 0を暗黙に使用しない。
- normalize/register-and-select が失敗した場合は navigation を実行せず、既存画面とselected keyを維持する。
- `AppRoute.Board` / `AppRoute.Thread` の型と引数、既存 Deep Link、タブ一覧のreplace遷移は互換のまま維持する。
- 固定 bar の inset は単一 Scaffold に集約し、3ボタン navigation、gesture navigation、IME表示時に本文 paddingを二重適用しない。

## Testing Strategy

- `BbsRouteScaffoldTest.kt` の presentation harness を `settledPage` 基準へ更新し、途中の `currentPage` 変化では選択callbackが発火せず、settle後に一度だけ発火することを検証する。
- Compose UI テストで本文drag非反応、コントローラーdrag、途中復帰、fling、既存 animateToPageFlow、タイトルカードと本文の追従、固定ツール群を検証する。
- タイトルカードテストでブックマーク・タイトル・更新・ロード進捗が同じ semantics subtree/移動単位に属し、進捗がCard下端かつCard幅に収まることを検証する。
- Board/Thread両方で展開・縮退、検索開始・終了、IME入力、popup中のスワイプ無効、タブ別縮退状態、スクロール位置保存・復元を検証する。
- NavigationテストでBoard「スレ」のSelected/Loading/Empty/PendingMissing、push後のBack復帰、Thread「板」のSelected/Loading/Empty/PendingMissing、登録失敗、現在Threadの破棄を検証する。
- LTR/RTL、ドラッグキャンセル、連続drag、drag中tab削除、TalkBack向けラベルとdisabled semanticsをinstrumented testまたは手動確認項目に含める。
- 実装後に `./gradlew assembleDebug` と `./gradlew testDebugUnitTest` を実行し、両方成功させる。

## Migration Plan

1. 先に settled page 選択確定とテストを導入し、既存Pager構造のまま副作用タイミングを安定させる。
2. タイトルカードを固定ツール群から分離し、Card内ロード進捗とoffset描画を追加する。
3. `BbsRouteScaffold` を単一 Scaffold へ再編し、外部 scrollable、各overlay、inset、タブ別縮退状態を接続する。
4. Board/Threadの画面種別ボタンとnavigationを追加し、最後に不要な本文側gesture抑制を削除する。
5. 全テストと手動確認完了後に提供する。問題時は単一コミット単位で新コントローラー変更を戻せば、データ移行なしで旧ページ内Scaffoldへ戻せる。
