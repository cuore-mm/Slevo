## 1. 共有projectionと選択primitive

- [x] 1.1 `TabProjectionPrimitives.kt` のprojection operationを単一key mutationと複数key除去を型安全に表現できる構造へ拡張し、bulk除去が対象Setによる1回の一覧filterとindex再構築で完了することを `TabControllerPrimitivesTest.kt` で検証する。
- [x] 1.2 `TabProjectionPrimitives.kt` に `selectionAfterTabRemovals` を追加し、選択対象外維持、中央/末尾選択削除、固定タブだけ残存、全削除nullをテストする。
- [x] 1.3 小規模一覧の全対象部分集合について、`selectionAfterTabRemovals` が既存 `selectionAfterTabRemoval` の一覧順foldと一致するパラメータ化テストを追加する。

## 2. chunk化した対象行永続化

- [x] 2.1 `OpenBoardTabDao.kt` に指定board URL集合だけを削除して件数を返す `IN` DELETE、`OpenThreadTabDao.kt` に指定ThreadId集合だけを削除して件数を返す `IN` DELETEを追加し、空Listは呼び出し側で除外する。
- [x] 2.2 `TabsRepository.kt` に最大900件のchunk size定数とBoard bulk-close APIを追加し、distinct化した全chunkを1回の `DatabaseWriteGate.withWritePermit` / `db.withTransaction` 内で削除してSuccess・NoOp・Failureを返す。
- [x] 2.3 `TabsRepository.kt` にThread bulk-close APIを追加し、全chunkを1 transactionで削除した後、削除件数が1件以上の場合だけ `collectGarbageUngated()` を正確に1回呼ぶ。例外・cancellationはtransaction全体へ伝播させる。
- [x] 2.4 Repository/DAOテストで空、901、1,252件のchunk境界、対象外行・sortOrder・pin・scroll不変を検証し、既存対象行・GC回帰テストを維持した。Android CIにinstrumented jobがないためRoom実行は未実行である。
- [x] 2.5 Coordinator/Repositoryテストと実装レビューで `saveOpenBoardTabs`、`replaceOpenThreadTabsForBulkOperation`、`deleteNotIn`、残存行upsertがbulk close経路から呼ばれないことを確認した。

## 3. Board bulk command

- [x] 3.1 `BoardTabsCoordinator.kt` の `Operation` に順序付き対象key集合と最終選択を保持する `BulkDelete` を追加し、重複除去・対象0件NoOpを行う公開bulk入口を実装する。
- [x] 3.2 `effectiveTabs`、`register`、`execute`、`isConfirmed`、`finish`、unbound path、teardownを `BulkDelete` 対応にし、1 pending operation、1 Repository bulk call、全対象不在のcanonical確認、session state集合cleanupを実現する。
- [x] 3.3 `BoardTabsCoordinatorTest.kt` にbulk対象の即時projection除外、固定残存、最終選択、NoOp、Repository Failure rollbackを追加し、既存のteardown・単体mutation競合回帰を維持した。
- [x] 3.4 Board bulkテストで対象件数にかかわらずRepository bulk callが1回であり、単体 `deleteOpenBoardTab` を反復しないことを検証する。

## 4. Thread bulk intentとbarrier

- [x] 4.1 `ThreadTabsProjection.kt` に `BulkDelete` pending operationを追加し、1回の集合projectionと全対象不在confirmationをpure functionテストで検証する。
- [x] 4.2 `ThreadTabsCoordinator.kt` の `ThreadTabMutationIntent` に `BulkDelete` と公開bulk入口を追加し、mutation consumerがbulkのRepository書き込み・canonical確認完了まで後続intentを開始しないbarrierとして処理する。
- [x] 4.3 Threadのdispatch、completion cancellation、selection/pending説明、supersession、session/runtime/newResCounts cleanup、unbound path、teardownの全`when`をbulk対応にする。単一key競合でbulk全体をsupersedeしない。
- [x] 4.4 `ThreadTabsCoordinatorTest.kt` にbulk対象の即時projection除外、固定残存、最終選択、NoOp、caller/store cancellation相当、既存Failure/teardown回帰を追加した。
- [x] 4.5 Thread競合テストでbulk完了まで後続Ensureを開始しないbarrier、bulk完了後のEnsure再作成、Repository bulk call 1回を検証し、GC 1回はRepository実装とRoomテスト境界で担保した。

## 5. Store bulk orchestrationとholder

- [x] 5.1 `TabSessionStore.closeAllUnpinnedTabs` の単体close反復を、対象ページの未固定タブを一度snapshotして対応Coordinator bulk APIを1回呼ぶ実装へ置換する。既存 `TabPage` 引数とretained scope所有を維持する。
- [x] 5.2 Board/Thread holder mapから対象keyの既存holderだけを一括抽出するprivate helperを追加し、各holderを正確に1回disposeする。固定・反対ページ・未生成holderは変更せずfactoryを呼ばない。
- [x] 5.3 `TabSessionStoreTest.kt` を新bulk APIへ更新し、固定除外、反対ページ、対象0件、Coordinator呼び出し1回、対象holderだけのdisposeを検証した。
- [x] 5.4 Storeテストでretained bulkがStore lifetime終了時にcancelされることを検証し、既存のcaller cancellation回帰を維持した。

## 6. 回帰・検証

- [x] 6.1 `TabListViewModelTest.kt` と `TabBulkCloseMenuTest.kt` の既存テストを維持し、その他ボタン、1項目メニュー、content description、dismiss、クリック時 `TabPage` 境界に変更がないことを確認した。
- [x] 6.2 変更した型・非自明関数へアノテーションより上にKDocを追加し、Preview関数はコメントなし、30行超関数はセクションコメント付きであることを確認した。
- [x] 6.3 `git diff` でDBスキーマ、full replacement API、Issue #497のUI/文字列に不要な差分がなく、bulk close以外の単体mutation契約を変更していないことを確認した。
- [x] 6.4 Repository instrumented testを追加した。Android CI workflowにはconnected/instrumented jobがないため、`connectedDebugAndroidTest` は未実行であり、理由を成果報告へ明記する。
- [x] 6.5 CI相当の `testCiUnitTest assembleCi --stacktrace` をGitHub Actionsで実行し成功を確認した（Run ID: `31344474332`）。

## 7. Codexレビュー指摘の修正

- [x] 7.1 `TabListViewModel` のbulk遅延をretained `TabSessionStore` scopeへ移し、ViewModel破棄で受理済み処理が失われないようにする。
- [x] 7.2 Board/Threadのbulk対象をクリック時snapshotとしてStoreからCoordinatorまで保持し、待機中のpin変更・新規タブ追加で削除対象が変わらないようにする。
- [x] 7.3 caller cancellation相当、待機中projection変化、Board/Thread対象snapshotの回帰テストを追加する。

## 8. Thread bulk例外の封じ込め

- [x] 8.1 `TabSessionStore`の即時・遅延Thread bulk launchで非キャンセル例外をログ記録して封じ込め、`CancellationException`は再送出する。
- [x] 8.2 Room/Coordinator失敗を模した即時・遅延Storeテストを追加し、root coroutineへ未処理例外を伝播させないことを検証する。
