## 1. 位置指定タブ登録の永続化

- [x] 1.1 Thread tab DAOと`TabsRepository.ensureOpenThreadTab()`の既存query・transaction境界を確認し、既存タブでは順序不変、新規タブでは任意anchor直後へ挿入できるrepository契約を追加する。完了条件は、追加行と後続`sortOrder`だけが単一transactionで更新されること。
- [x] 1.2 repository/DAOテストへ、anchor直後追加、一意な`sortOrder`、再読込後の順序、anchor不在時の末尾追加を追加する。
- [x] 1.3 repository/DAOテストへ、既存対象のensureで重複行を作らず、全タブの順序・pin・scroll・解決済みmetadataを維持するケースを追加する。

## 2. ControllerとStoreの原子的なensure-and-select

- [x] 2.1 `ThreadTabsCoordinator.kt`のEnsure intentとpending projectionへ任意anchor keyを追加し、新規対象だけをeffective tabs上のanchor直後へ投影し、anchor不在時は末尾へfallbackする。
- [x] 2.2 `ThreadTabsCoordinator.kt`で既存対象を再利用して順序を変更せず選択し、repository失敗時はpending追加とselectionを既存canonical状態へ収束させる。
- [x] 2.3 pending reorder中の位置指定Ensureがanchor直後の相対順を維持し、canonical snapshot到着後も同じ順序へ収束するようprojectionとconfirmationを接続する。
- [x] 2.4 `TabSessionStore.registerAndSelectThreadRoute()`から、リンク選択時点のselected thread keyと正規化済みrouteを位置指定ensure-and-selectへ一度だけ委譲し、Store内ではリスト変更や追加のFlow待機を行わない。
- [x] 2.5 `ThreadTabsCoordinatorTest.kt`へ、新規anchor直後、既存重複防止、anchor不在、pending reorder競合、同時ensure、repository失敗の各テストを追加する。
- [x] 2.6 `TabSessionStoreTest.kt`へ、正規化済みrouteと選択時点anchor keyがCoordinatorへ渡され、成功時だけ対象indexを返すテストを追加する。

## 3. ThreadリンクのNavigation修正

- [x] 3.1 `ThreadScreen.kt`のレス本文Threadリンクcallbackで、正規化と登録・選択は維持し、成功後の`navigateToThreadScreen(normalizedRoute)`を実行しないよう変更する。
- [x] 3.2 `ThreadScaffold.kt`のReplyPopup内Threadリンクcallbackにも3.1と同じ契約を適用し、外部URL処理と失敗時の現在画面維持を確認する。
- [x] 3.3 Board、履歴、ブックマーク、タブ一覧、Deep LinkのThread起動経路を確認し、Thread destination外からの既存pushおよびThread→Boardのpop/replaceを変更していないことをNavigation testで検証する。
- [x] 3.4 Thread Aから新規・既存Thread Bを開いてもback stack entryが増えず、Android BackでThread進入前の画面へ戻るテストを追加する。

## 4. Pager同期と範囲安全性

- [x] 4.1 `BbsRouteScaffold.kt`へ、現在pageとselected pageの差をno-op、animate、instantへ分類する純粋関数を追加し、差0、差1、差2以上、範囲外を`BbsRouteScaffoldSelectionTest.kt`で検証する。
- [x] 4.2 selected key同期effectを、隣接時`animateScrollToPage()`、遠方時`scrollToPage()`、同一時no-opへ変更し、既存`animateToPageFlow`実行中の同期抑止を維持する。
- [x] 4.3 Pagerの`pageCount`、`key`、content、settled page解決が一つの最新immutable tabs snapshotを参照するstate holderへ統一する。
- [x] 4.4 `key`とcontentを含む全page index参照を境界検証し、範囲外key要求にはpageとpresentation revisionから一意なfallback key、範囲外content要求には副作用のないcontainerを返す。
- [x] 4.5 `BbsRouteScaffoldTest.kt`へ78件から79件の追加、選択タブ削除、連続追加・削除・reorderを含むCompose testを追加し、例外なしで選択対象contentへ収束することを検証する。
- [x] 4.6 新規タブが現在タブ直後へ追加された場合はアニメーションし、2ページ以上離れた既存タブは即時移動し、同一タブ再選択では移動しないCompose testを追加する。

## 5. 品質確認

- [x] 5.1 追加・変更した型と非自明関数へリポジトリ規約どおりKDocを付け、30行を超える関数へ処理区分コメントを付ける。Preview関数にはKDocを追加していないことを確認する。
- [x] 5.2 `./gradlew testDebugUnitTest`相当のCI Unit testを実行し、成功を確認した。
- [x] 5.3 `./gradlew assembleDebug`相当のCI buildを実行し、成功を確認した。
- [x] 5.4 現行CI workflowにはemulator/deviceを使うinstrumented test jobがないため、関連instrumented testは未実行とした。この理由を実装報告へ明記する。

## 6. Thread→Boardのpop準拠アニメーション

- [x] 6.1 `TransitionSpecs.kt`へBoard→ThreadとThread→Boardを分けるroute方向判定を追加し、Thread→Boardでは既存pop用slide-only spec（Thread右退出、Board左復帰、300ms）を選べる契約にする。
- [x] 6.2 `AppNavGraph.kt`のBoard/Threadのenter、exit、popEnter、popExit選択をroute方向へ接続し、popとreplaceの履歴操作を変更せず、Board→Threadと他destinationの既存transitionを維持する。
- [x] 6.3 `TransitionSpecsTest.kt`へ両方向の判定、類似routeの除外、Thread→Boardのtransition対象を追加し、Navigation経路の既存テストでpop/replace契約を維持することを確認する。
- [x] 6.4 CIでUnit testとCI APK buildの成功を確認した。現行CI workflowにemulator/deviceを使うinstrumented test jobがないため、関連instrumented testは未実行とし、その理由を実装報告へ明記する。

## 7. ThreadInfoBottomSheetの板遷移共通化

- [x] 7.1 `ThreadInfoBottomSheet.kt`の板ボタンから直接`navigateToBoardScreen()`を呼ばず、`currentScreenRoute`を使って`showBoardScreenForTabSelection()`を呼ぶ。ThreadScaffold、BoardScaffold、TabScreenContentから呼出し元routeを伝播する。
- [x] 7.2 共通Navigation関数の既存pop/replace/pushテストを再利用して、ThreadInfoBottomSheetの呼出し契約がThread画面ではpop/replace、タブ一覧ではpushになることを確認する。
- [x] 7.3 CIでUnit testとCI APK buildの成功を確認した。現行CI workflowにemulator/deviceを使うinstrumented test jobがないため、関連instrumented testは未実行とし、その理由を実装報告へ明記する。

## 8. Pagerの範囲外currentPageからの収束

- [x] 8.1 `BbsRouteScaffold.kt`の`pagerMoveBehavior()`で、currentPageが範囲外でもtargetPageが有効なら`Immediate`を返し、targetPageが無効な場合だけ`None`にする。
- [x] 8.2 `BbsRouteScaffoldSelectionTest.kt`と`BbsRouteScaffoldTest.kt`で、タブ削除後にcurrentPageが一時的に範囲外となっても有効な選択先へ即時同期して収束することを検証する。
- [x] 8.3 CIでUnit testとCI APK buildの成功を確認した。現行CI workflowにemulator/deviceを使うinstrumented test jobがないため、関連instrumented testは未実行とし、その理由を実装報告へ明記する。

## 9. Threadリンク切替後の既読対象修正

- [x] 9.1 `ThreadScaffold.kt`の`onLastRead`で、保持されたNavigation route由来の`routeThreadId`ではなく表示中`tab.id`を`updateThreadLastRead()`へ渡す。初期route検証と初期選択に使う`routeThreadId`は維持する。
- [x] 9.2 Thread A→B切替後の既読位置がBへ保存され、Aへ混入しないことを既存のThread既読状態テストへ追加して検証する。
- [ ] 9.3 CIでUnit testとCI APK buildを実行する。現行CI workflowにemulator/deviceを使うinstrumented test jobがない場合は、未実行理由を実装報告へ明記する。
