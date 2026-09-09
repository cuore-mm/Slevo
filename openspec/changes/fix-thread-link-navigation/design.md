## Context

`ThreadScreen.kt` と `ThreadScaffold.kt` のレス本文リンク処理は、正規化済みrouteを `TabSessionStore.registerAndSelectThreadRoute()` へ渡した後、現在もThread画面であるかを問わず `navigateToThreadScreen()` を呼ぶ。スレッドタブは `ThreadTabsCoordinator` とRoomを正本として管理される一方、新規タブは `TabsRepository.ensureOpenThreadTab()` により末尾へ保存される。

`BbsRouteScaffold.kt` は `TabPresentationState` から得た `tabs` を `rememberPagerState(pageCount = { tabs.size })`、`HorizontalPager.key`、page contentで個別に参照する。Pager内部のitem provider更新とpage count参照のタイミングがずれると、`key` またはcontentが古い78件の一覧へ`page = 78`を適用できる。現在の選択同期は `scrollToPage()`、下部コントローラーの隣接移動は別の`animateToPageFlow`で処理される。

## Goals / Non-Goals

**Goals:**

- Thread→Threadリンクを同一Thread destination内のタブ操作として完結させる。
- 新規リンク先を現在タブ直後へ永続化し、既存リンク先は並び順を変えず再利用する。
- 選択先が隣ならanimate、遠方ならinstantという決定的なPager同期を行う。
- Pager内部が一時的に古いindexを要求しても例外を発生させない。
- タブ登録失敗時は現在表示とNavigationを維持する。
- Thread→Boardのpop/replace経路で、pop操作と同じ右退出・左復帰の画面アニメーションを適用する。
- ThreadInfoBottomSheetの板ボタンも現在のdestination routeを共通遷移関数へ渡し、ThreadScaffoldの下部ボタンと同じpop/replace判断を使用する。

**Non-Goals:**

- Board→Thread、履歴、ブックマーク、Deep LinkからThread destinationへ入る既存のpush動作は変更しない。
- Android Backをタブ履歴として扱わない。
- タブ一覧UI、リンク表示テキスト、content description、リンク判定規則を変更しない。新規UIやPreviewは追加しない。
- Compose Foundationのバージョン変更や外部依存追加は行わない。
- Navigationのpop/push/replace自体の履歴操作、Board→Threadの進入方向、Board/Thread以外の画面遷移は変更しない。

## Decisions

### 1. Thread→Threadはタブ操作だけを実行する

`ThreadScreen.kt` と`ThreadScaffold.kt`の本文・ReplyPopupリンクcallbackでは、正規化とタブ登録・選択の成功後に`navigateToThreadScreen()`を呼ばない。Board等の別destinationからThreadを開く呼び出しは従来どおりnavigateする。現在routeの判定を各callbackへ複製せず、Thread画面専用callbackの契約としてNavigation非実行を固定する。

代替の`launchSingleTop`継続は、引数が異なるThread routeでentry追加を防ぐ契約にならず、destination再生成も残るため採用しない。

### 2. 現在タブkeyを伴う位置指定ensureをControllerのcommandにする

`TabSessionStore.registerAndSelectThreadRoute()`から`ThreadTabsCoordinator`へ、正規化済み対象routeと呼出時のselected thread keyを一つの要求として渡す。Controllerはcommand受理時のeffective tabsで対象keyとanchor keyを解決する。

- 対象が既存: metadata mergeの既存契約を維持し、sortOrderを変更せず対象を選択する。
- 対象が新規かつanchorが存在: anchor直後へpending projectionし、同じ順序をrepositoryへ保存する。
- 対象が新規かつanchorが不在: race時の決定的fallbackとして末尾へ追加する。
- ensureまたはcanonical確認が失敗: pending projectionを除去し、既存selectionへ収束してNavigationもPager移動も開始しない。

選択、追加位置、command resultをUI側の複数操作に分割しない。これにより重複タブ、追加後の再並び替え、途中selectionを避ける。

### 3. 位置指定追加はRoom transactionで順序ごと保存する

`TabsRepository.ensureOpenThreadTab()`の既存metadata mergeを維持したまま、位置指定追加経路ではThread tab DAOのtransactionから次を行う。

1. stable keyで既存対象を再確認し、存在すれば既存行をmergeして順序を維持する。
2. anchorの現在sortOrderを再確認する。
3. anchor後方の行へ衝突しないsortOrderを再割当てする。
4. 新規行をanchor直後のsortOrderで挿入する。
5. transaction終了後のRoom snapshotをControllerがcanonical確認する。

既存DAOに後続行の順序更新手段がなければ、実装時にThread tab DAOの現行query名を確認して、Threadタブだけを対象にしたtransactionメソッドを追加する。通常の既存ensureをfull-list replacementへ変更しない。既存タブのensureではsortOrder、pin、scroll、解決済みmetadataを保持する。

### 4. 選択同期effectが距離から移動方式を決める

`BbsRouteScaffold.kt`のselected key同期effectで、最新の整合したtabsからselected indexを解決し、`pagerState.currentPage`との差を評価する。

- 差0: 何もしない。
- 差1: `animateScrollToPage(selectedPage)`。
- 差2以上: `scrollToPage(selectedPage)`。

この規則はプログラムによる同種タブ選択へ共通適用する。既存の下部コントローラー隣接移動は`animateToPageFlow`とsettled pageによる選択更新を維持し、同一Pagerに二つの移動effectが同時発火しないよう、外部移動要求中の既存同期抑止条件を保持する。リンク操作は`animateToPageFlow`へ別イベントを送らず、selected key同期だけで移動する。

### 5. Pagerの全callbackを共有する最新スナップショットへ接続して境界検証する

`BbsRouteScaffold.kt`でPager向けタブ一覧を一つのstate holderとして保持し、`pageCount`、`key`、content、settled tab解決の各callbackは呼出時に同じholderからimmutable listを一度だけ取得する。`key`とcontentは`getOrNull(page)`で境界検証し、範囲外要求ではタブデータを読まない。

範囲外keyに既存タブのkeyや固定文字列を流用するとkey重複を起こすため、要求pageとpresentation revisionから一意になる内部fallback keyを使用する。contentの範囲外要求は副作用を行わず空のpage containerだけを返し、有効selectionの次回同期で解消する。これはユーザー向けempty stateではなくPager内部更新中だけの防御である。

単に`tabs[page]`をtry/catchで囲む案は、keyとcontentの不一致を隠し、安定キー重複を防げないため採用しない。

### 6. Thread→Boardはroute方向でpop準拠の画面transitionを選択する

`TransitionSpecs.kt`にBoard→ThreadとThread→Boardを分けて判定する純粋関数を追加する。Thread→Boardでは、NavControllerの操作が`popBackStack()`か`navigate + popUpTo(inclusive)`かに依存せず、Board側に`boardThreadPopEnterTransition()`、Thread側に`boardThreadPopExitTransition()`と同じslide-only specを適用する。これにより、Threadは右へ退出し、Boardは左から復帰する。

Board→Threadは既存の`boardThreadEnterTransition()` / `boardThreadExitTransition()`を維持する。ImageViewerとの遷移でアニメーションを無効にする既存条件、その他destinationのdefault transition、back stackのpop/replace契約は変更しない。

### 7. ThreadInfoBottomSheetの板遷移を共通化する

`ThreadInfoBottomSheet`へ任意の`currentScreenRoute`を渡せるようにし、板ボタンでは登録・選択後に`showBoardScreenForTabSelection(currentScreenRoute, route)`を一度呼ぶ。Thread画面から表示された場合はThread routeを渡すため、直前がBoardならpopし、それ以外はThreadをBoardへreplaceする。Board画面、タブ一覧画面、Previewなど既存の呼出し元はそれぞれBoard routeまたはnullを渡し、既存の動作を維持する。

## Implementation Contract

1. `ThreadScreen.kt`と`ThreadScaffold.kt`の本文・ReplyPopupのThreadリンクcallbackから、登録成功後の`navigateToThreadScreen(normalizedRoute)`だけを除去する。正規化、登録失敗判定、外部URL処理は維持する。
2. `TabSessionStore.registerAndSelectThreadRoute()`は呼出時のselected thread keyを取得し、位置指定ensure-and-selectを`ThreadTabsCoordinator`へ一度だけ委譲する。Store自身でtabsを並べ替えない。
3. `ThreadTabsCoordinator`のEnsure intent/pending projectionへ任意anchor keyを追加する。新規対象だけanchor直後へ挿入し、既存対象の順序は変更しない。anchor不在は末尾fallbackとする。
4. `TabsRepository.kt`とThread tab DAOへ位置指定追加のtransactionを実装する。新規挿入と後続sortOrder更新は同一transaction、既存ensureは順序不変とする。`upsertAll`と`deleteNotIn`による全件置換は使わない。
5. `BbsRouteScaffold.kt`のselected page同期を距離0/1/2以上でno-op/animate/instantに分岐する。分岐は純粋関数へ抽出してunit test可能にする。
6. Pagerの`pageCount`、`key`、content、settled page参照を共有snapshot holderへ統一し、すべてのindex参照前に範囲確認する。fallback keyはpageとrevisionに対して一意にする。
7. 追加・変更するclass/interface/data class/sealed typeにはKDocを付け、非自明関数にもKDoc、30行超の関数には処理区分コメントを付ける。Preview関数にはKDocを追加しない。
8. `TransitionSpecs.kt`のroute方向判定と`AppNavGraph.kt`のBoard/Thread transition選択を接続し、Thread→Boardではpop準拠、Board→Threadでは既存forward方向を使う。
9. `ThreadInfoBottomSheet.kt`の板ボタンを`showBoardScreenForTabSelection()`へ接続し、ThreadScaffold、BoardScaffold、TabScreenContentから呼出し元の`currentScreenRoute`を伝播する。

## Error Cases / Compatibility

- URL正規化失敗、タブ登録失敗、canonical確認失敗では現在selection、表示、back stackを維持する。
- 同時削除でanchorが消えた場合は末尾追加へfallbackし、対象タブを失わない。
- 同時ensureで対象が先に作成された場合は既存タブとしてmergeし、重複行と順序変更を防ぐ。
- pending reorder中でもanchorをeffective orderから解決し、Room収束後に同じ相対順を維持する。
- Boardタブの追加位置、Board→Threadの履歴、Thread→Boardのpop/replace、Deep Link初期遷移は互換動作を維持する。
- Thread→Boardのpop/replaceは履歴操作を維持したまま、見た目だけ同じpop準拠のslide-only transitionへ揃える。
- 可視テキストとアクセシビリティsemanticsは変更しない。移動アニメーションは既存Pager APIを使用し、システムのアニメーション無効化環境で操作結果を失わない。

## Testing Strategy

- `ThreadTabsCoordinatorTest`: 新規対象のanchor直後projection、既存対象の重複防止・順序維持、anchor不在fallback、pending reorder競合、repository失敗時rollback。
- `TabsRepositoryTest`または既存repository test: transaction後の一意sortOrder、再読込順、既存ensure時の順序・pin・scroll・metadata保持、同時ensureの重複防止。
- `TabSessionStoreTest`: 正規化済みroute、anchor key、ensure-and-select resultの委譲を確認する。
- `NavigationExtensionsTest`およびThreadリンクcallbackのテスト: Thread→Threadでnavigateを呼ばず、Board→Threadではpushを維持し、BackがThread進入前へ戻ることを確認する。
- `TransitionSpecsTest`: Board→ThreadとThread→Boardの方向判定、Thread→Boardのpop準拠transition選択対象、他destinationの除外を確認する。
- `NavigationExtensionsTest`とThreadInfoBottomSheetの呼出し経路: Thread画面から板を選択した場合に共通関数のpop/replace契約を使い、直接navigateしないことを確認する。
- `BbsRouteScaffoldSelectionTest`: 距離0/1/2以上の移動方式をpure decisionとして検証する。
- `BbsRouteScaffoldTest`: 78件から79件への追加、選択タブ削除、連続追加・削除・reorderで例外がなく、対象contentへ収束することをCompose testで確認する。
- 実装後に`./gradlew assembleDebug`と`./gradlew testDebugUnitTest`を実行する。関連instrumented testは利用可能なemulator/deviceで実行し、実行できない場合は未実行理由を明記する。

## Risks / Trade-offs

- [位置指定追加で複数行のsortOrder更新が必要になり、通常末尾追加よりDB writeが増える] → スレッドリンク由来の新規追加だけに限定し、単一transactionと対象範囲更新を使う。
- [Pager fallback pageが一瞬空になる可能性] → 範囲外要求時だけ副作用なしcontainerを返し、selected key同期を継続するCompose testで可視状態への収束を確認する。
- [隣接判定中にtabs順序が変わる] → currentPageとselected keyを同一snapshotへ解決し、範囲外なら移動せず次のstate更新を待つ。
- [既存の下部コントローラーアニメーションとselected同期が競合する] → 既存の同期抑止条件を維持し、リンク経路から別のanimation flowを発行しない。

## Migration Plan

DB schema列の追加は不要で、既存`sortOrder`を再利用する。アプリ更新後に追加されるリンク由来タブから新しい挿入規則を適用し、保存済みタブの既存順序は移行しない。問題があればThread→Threadのnavigate抑止を維持したまま位置指定ensureを通常ensureへ戻せるが、Pager境界防御は独立して残す。
