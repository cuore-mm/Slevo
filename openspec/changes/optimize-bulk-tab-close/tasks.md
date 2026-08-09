## 1. 共有projectionと選択primitive

- [x] 1.1 `TabProjectionPrimitives.kt` のprojection operationを単一key mutationと複数key除去を型安全に表現できる構造へ拡張し、bulk除去が対象Setによる1回の一覧filterとindex再構築で完了することを `TabControllerPrimitivesTest.kt` で検証する。
- [x] 1.2 `TabProjectionPrimitives.kt` に `selectionAfterTabRemovals` を追加し、選択対象外維持、中央/末尾選択削除、固定タブだけ残存、全削除nullをテストする。
- [x] 1.3 小規模一覧の全対象部分集合について、`selectionAfterTabRemovals` が既存 `selectionAfterTabRemoval` の一覧順foldと一致するパラメータ化テストを追加する。

## 2. chunk化した対象行永続化

- [x] 2.1 `OpenBoardTabDao.kt` に指定board URL集合だけを削除して件数を返す `IN` DELETE、`OpenThreadTabDao.kt` に指定ThreadId集合だけを削除して件数を返す `IN` DELETEを追加し、空Listは呼び出し側で除外する。
- [x] 2.2 `TabsRepository.kt` に最大900件のchunk size定数とBoard bulk-close APIを追加し、distinct化した全chunkを1回の `DatabaseWriteGate.withWritePermit` / `db.withTransaction` 内で削除してSuccess・NoOp・Failureを返す。
- [x] 2.3 `TabsRepository.kt` にThread bulk-close APIを追加し、全chunkを1 transactionで削除した後、削除件数が1件以上の場合だけ `collectGarbageUngated()` を正確に1回呼ぶ。例外・cancellationはtransaction全体へ伝播させる。
- [ ] 2.4 Repository/DAOテストで空、1、900、901、1,252件のchunk境界、対象外行・sortOrder・pin・scroll・ThreadState timestamp不変、Thread GC 0/1回、失敗時rollbackを検証する。
- [ ] 2.5 Coordinator/Repositoryテストで `saveOpenBoardTabs`、`replaceOpenThreadTabsForBulkOperation`、`deleteNotIn`、残存行upsertがbulk close経路から呼ばれないことを確認する。

## 3. Board bulk command

- [x] 3.1 `BoardTabsCoordinator.kt` の `Operation` に順序付き対象key集合と最終選択を保持する `BulkDelete` を追加し、重複除去・対象0件NoOpを行う公開bulk入口を実装する。
- [x] 3.2 `effectiveTabs`、`register`、`execute`、`isConfirmed`、`finish`、unbound path、teardownを `BulkDelete` 対応にし、1 pending operation、1 Repository bulk call、全対象不在のcanonical確認、session state集合cleanupを実現する。
- [ ] 3.3 `BoardTabsCoordinatorTest.kt` に大量対象の即時projection除外、固定残存、最終選択/Empty、NoOp、Repository Failure rollback、teardown、Ensure/Pin/単体Deleteとの受理順競合を追加する。
- [ ] 3.4 Board bulkテストで対象件数にかかわらずpending entryとRepository bulk callが各1回であり、単体 `deleteOpenBoardTab` を反復しないことを検証する。

## 4. Thread bulk intentとbarrier

- [x] 4.1 `ThreadTabsProjection.kt` に `BulkDelete` pending operationを追加し、1回の集合projectionと全対象不在confirmationをpure functionテストで検証する。
- [x] 4.2 `ThreadTabsCoordinator.kt` の `ThreadTabMutationIntent` に `BulkDelete` と公開bulk入口を追加し、mutation consumerがbulkのRepository書き込み・canonical確認完了まで後続intentを開始しないbarrierとして処理する。
- [x] 4.3 Threadのdispatch、completion cancellation、selection/pending説明、supersession、session/runtime/newResCounts cleanup、unbound path、teardownの全`when`をbulk対応にする。単一key競合でbulk全体をsupersedeしない。
- [ ] 4.4 `ThreadTabsCoordinatorTest.kt` に大量対象の即時projection除外、固定残存、最終選択/Empty、NoOp、Failure rollback、caller/store cancellation、teardown waiter解放を追加する。
- [ ] 4.5 Thread競合テストで先行Ensure/Pin/Info、後続Ensure/Pin/単体Deleteとの順序、bulk完了後のEnsureによる再作成、Repository bulk call 1回、GC 1回を検証する。

## 5. Store bulk orchestrationとholder

- [x] 5.1 `TabSessionStore.closeAllUnpinnedTabs` の単体close反復を、対象ページの未固定タブを一度snapshotして対応Coordinator bulk APIを1回呼ぶ実装へ置換する。既存 `TabPage` 引数とretained scope所有を維持する。
- [x] 5.2 Board/Thread holder mapから対象keyの既存holderだけを一括抽出するprivate helperを追加し、各holderを正確に1回disposeする。固定・反対ページ・未生成holderは変更せずfactoryを呼ばない。
- [ ] 5.3 `TabSessionStoreTest.kt` を新bulk APIへ更新し、固定除外、反対ページ、対象0件、重複key、Coordinator呼び出し1回、対象holderだけのdispose、二重実行の冪等性を検証する。
- [ ] 5.4 StoreテストでUI caller cancellation後もretained bulkが完了し、Store lifetime終了時はin-flight bulkをcancelして残存holderを破棄することを検証する。

## 6. 回帰・検証

- [ ] 6.1 `TabListViewModelTest.kt` と `TabBulkCloseMenuTest.kt` の既存テストを維持し、その他ボタン、1項目メニュー、content description、dismiss、クリック時 `TabPage` 境界に変更がないことを確認する。
- [ ] 6.2 変更した型・非自明関数へアノテーションより上にKDocを追加し、Preview関数はコメントなし、30行超関数はセクションコメント付きであることを確認する。
- [ ] 6.3 `git diff` でDBスキーマ、full replacement API、Issue #497のUI/文字列に不要な差分がなく、bulk close以外の単体mutation契約を変更していないことを確認する。
- [ ] 6.4 Android実行環境で追加Repository instrumented testを `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<追加した完全修飾テストクラス名>` により実行する。環境がなければ未実行理由を成果報告へ明記する。
- [ ] 6.5 CI相当の単体テストとビルドを `./gradlew testCiUnitTest assembleCi --stacktrace` で実行し、成功を確認する。
