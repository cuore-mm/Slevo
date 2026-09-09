## 1. 位置指定タブ登録の永続化

- [ ] 1.1 Thread tab DAOと`TabsRepository.ensureOpenThreadTab()`の既存query・transaction境界を確認し、既存タブでは順序不変、新規タブでは任意anchor直後へ挿入できるrepository契約を追加する。完了条件は、追加行と後続`sortOrder`だけが単一transactionで更新されること。
- [ ] 1.2 repository/DAOテストへ、anchor直後追加、一意な`sortOrder`、再読込後の順序、anchor不在時の末尾追加を追加する。
- [ ] 1.3 repository/DAOテストへ、既存対象のensureで重複行を作らず、全タブの順序・pin・scroll・解決済みmetadataを維持するケースを追加する。

## 2. ControllerとStoreの原子的なensure-and-select

- [ ] 2.1 `ThreadTabsCoordinator.kt`のEnsure intentとpending projectionへ任意anchor keyを追加し、新規対象だけをeffective tabs上のanchor直後へ投影し、anchor不在時は末尾へfallbackする。
- [ ] 2.2 `ThreadTabsCoordinator.kt`で既存対象を再利用して順序を変更せず選択し、repository失敗時はpending追加とselectionを既存canonical状態へ収束させる。
- [ ] 2.3 pending reorder中の位置指定Ensureがanchor直後の相対順を維持し、canonical snapshot到着後も同じ順序へ収束するようprojectionとconfirmationを接続する。
- [ ] 2.4 `TabSessionStore.registerAndSelectThreadRoute()`から、リンク選択時点のselected thread keyと正規化済みrouteを位置指定ensure-and-selectへ一度だけ委譲し、Store内ではリスト変更や追加のFlow待機を行わない。
- [ ] 2.5 `ThreadTabsCoordinatorTest.kt`へ、新規anchor直後、既存重複防止、anchor不在、pending reorder競合、同時ensure、repository失敗の各テストを追加する。
- [ ] 2.6 `TabSessionStoreTest.kt`へ、正規化済みrouteと選択時点anchor keyがCoordinatorへ渡され、成功時だけ対象indexを返すテストを追加する。

## 3. ThreadリンクのNavigation修正

- [ ] 3.1 `ThreadScreen.kt`のレス本文Threadリンクcallbackで、正規化と登録・選択は維持し、成功後の`navigateToThreadScreen(normalizedRoute)`を実行しないよう変更する。
- [ ] 3.2 `ThreadScaffold.kt`のReplyPopup内Threadリンクcallbackにも3.1と同じ契約を適用し、外部URL処理と失敗時の現在画面維持を確認する。
- [ ] 3.3 Board、履歴、ブックマーク、タブ一覧、Deep LinkのThread起動経路を確認し、Thread destination外からの既存pushおよびThread→Boardのpop/replaceを変更していないことをNavigation testで検証する。
- [ ] 3.4 Thread Aから新規・既存Thread Bを開いてもback stack entryが増えず、Android BackでThread進入前の画面へ戻るテストを追加する。

## 4. Pager同期と範囲安全性

- [ ] 4.1 `BbsRouteScaffold.kt`へ、現在pageとselected pageの差をno-op、animate、instantへ分類する純粋関数を追加し、差0、差1、差2以上、範囲外を`BbsRouteScaffoldSelectionTest.kt`で検証する。
- [ ] 4.2 selected key同期effectを、隣接時`animateScrollToPage()`、遠方時`scrollToPage()`、同一時no-opへ変更し、既存`animateToPageFlow`実行中の同期抑止を維持する。
- [ ] 4.3 Pagerの`pageCount`、`key`、content、settled page解決が一つの最新immutable tabs snapshotを参照するstate holderへ統一する。
- [ ] 4.4 `key`とcontentを含む全page index参照を境界検証し、範囲外key要求にはpageとpresentation revisionから一意なfallback key、範囲外content要求には副作用のないcontainerを返す。
- [ ] 4.5 `BbsRouteScaffoldTest.kt`へ78件から79件の追加、選択タブ削除、連続追加・削除・reorderを含むCompose testを追加し、例外なしで選択対象contentへ収束することを検証する。
- [ ] 4.6 新規タブが現在タブ直後へ追加された場合はアニメーションし、2ページ以上離れた既存タブは即時移動し、同一タブ再選択では移動しないCompose testを追加する。

## 5. 品質確認

- [ ] 5.1 追加・変更した型と非自明関数へリポジトリ規約どおりKDocを付け、30行を超える関数へ処理区分コメントを付ける。Preview関数にはKDocを追加していないことを確認する。
- [ ] 5.2 `./gradlew testDebugUnitTest`を実行し、全Unit testが成功するまで修正する。
- [ ] 5.3 `./gradlew assembleDebug`を実行し、Debug buildが成功するまで修正する。
- [ ] 5.4 利用可能なemulator/deviceで関連instrumented testを実行する。実行環境がない場合は、未実行のtest classと理由を実装報告へ明記する。
