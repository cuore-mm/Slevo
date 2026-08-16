## Context

`add-bulk-delete-tabs` はIssue #497のUI、固定除外、表示中ページ境界を実装済みである。現在の `TabSessionStore.closeAllUnpinnedTabs` はBoardで `closeBoardTab`、Threadで `closeThreadTab` を対象件数分反復する。Boardはpending operationとprojection計算が大量に蓄積し、Threadは各DELETEについてRoom canonical確認とThreadState GCを待ってから次へ進むため、大量タブではDB往復とUI更新が対象件数に比例して増える。

既存の `saveOpenBoardTabs` と `replaceOpenThreadTabsForBulkOperation` は残存行の再書込み、sortOrder再採番、ThreadState timestamp更新を伴うfull replacementであり、この操作には使用できない。一方、`BoardRepository` には最大900件にchunk化した複数DAO呼び出しを1つの `DatabaseWriteGate.withWritePermit` と `db.withTransaction` で包む先行パターンがある。

このchangeは `add-bulk-delete-tabs` に依存する独立した性能・原子性改善である。Issue #497のUI仕様は変更しない。

## Goals / Non-Goals

**Goals:**

- Board/Threadごとに1つの専用bulk commandを受理し、対象集合をUI projectionから1操作で除外する。
- 指定ID集合を最大900件ずつ削除し、全chunkをbulk操作単位の1つのRoom transactionで原子的にcommitする。
- ThreadState遅延GCをThread bulk操作につき1回だけ実行する。
- 対象holderを1つのbulk破棄段階でmapから除去し、各holderを正確に1回disposeする。
- 同じ対象を現行実装の一覧順で単体closeした場合と同じ最終選択へ収束する。
- 固定除外、表示中ページ境界、retained lifetime、targeted persistence、canonical source of truthを維持する。

**Non-Goals:**

- その他ボタン、メニュー、文言、確認、Undo、進捗、アニメーションを変更しない。
- full replacement API、DBスキーマ、外部依存を変更しない。
- 単体close、pin、ensure、refreshの公開挙動を変更しない。
- ThreadStateをタブclose直後に削除したり、GCの30日TTL・最大100件制限を変更したりしない。
- wall-clock時間を固定値で保証しない。性能契約はcommand・projection・transaction・GC回数を対象件数から分離する構造で定義する。

## Decisions

### 1. `optimize-bulk-tab-close` を独立changeとして扱う

Issue #497の機能・UIは完成しており、本変更はCoordinator、DAO、Repository、Storeを横断する性能・原子性変更であるため、`add-bulk-delete-tabs` へ追記せず独立changeにする。実装順は `add-bulk-delete-tabs` の後とし、archive時も基礎changeを先に処理する。

### 2. 受理時スナップショットをretained scopeで実行する

`TabListViewModel` はクリック時の公開projectionから `isPinned == false` のタブを一覧順で一度だけ取得し、削除中keyをUIへ公開したうえで、対象リストを `TabSessionStore` へ渡す。Storeは対象リストを再取得せず、Activity-retained scope内でアニメーション待機を行い、Boardは `BoardTabsCoordinator.closeBoardTabs`、Threadは `ThreadTabsCoordinator.closeThreadTabs` へ対象リストを1回だけ渡す。これにより、ViewModel破棄や待機中の一覧変化で受理対象が失われたり置き換わったりしない。対象0件はCoordinator・Repository・holderを変更しないNoOpとする。

既存の `TabSessionStore.closeAllUnpinnedTabs(page)` は即時bulk呼び出しが必要な内部契約として残し、アニメーション経路は対象snapshotを受け取る遅延APIを使用する。ページは引き続き `TabScreenContent` がクリック時の `pagerState.currentPage` から選択し、routeや初期ページによる推測は追加しない。

### 3. bulk commandを1つのpending projectionとして表現する

`BoardTabsCoordinator.Operation` と `ThreadTabMutationIntent` / `ThreadTabPendingOperation` に `BulkDelete` を追加する。operationは重複を除いた元一覧順のIDと、受理時に計算した最終選択を保持する。

`TabProjectionPrimitives.kt` に単一key mutationと複数key除去を区別できるprojection operationを導入する。複数key除去は対象をSet化し、一覧を1回filterしてindexを1回再構築する。対象件数分のpending operationへ展開してはならない。

Boardの `effectiveTabs` とThreadの `projectThreadTabs` はpending順序を維持しながら `BulkDelete` を1操作としてfoldする。これにより受理直後に対象全件が公開projectionから消え、canonical未反映中も再表示しない。

### 4. final selectionの定義をpure functionで固定する

`TabProjectionPrimitives.kt` に `selectionAfterTabRemovals` を追加する。規範となる結果は、受理時のeffective一覧と選択keyに対し、対象IDを元一覧順で既存 `selectionAfterTabRemoval` へfoldした結果とする。

実装はO(n+k)の閉形式を利用してよいが、テストでは小規模一覧の全対象部分集合について規範foldと一致させる。選択keyが対象外なら維持し、対象内なら削除前indexに対応する残存タブ、範囲外なら末尾、残存0件ならnull/Emptyとなる。

### 5. canonical confirmationは全対象不在を1回確認する

Boardの `isConfirmed` とThreadの `isThreadTabOperationConfirmed` は、canonical一覧のkey集合と対象Setが交差しないことをbulk完了条件にする。Repository成功後も全対象不在の新しいRoom snapshotを確認するまでpendingを維持する。

Repository失敗またはcancellation時はbulk pending全体を除去し、Room canonical一覧を再投影する。単一transactionであるため部分削除を公開してはならない。

### 6. 競合は受理順と既存単体Delete規則を集合へ拡張する

対象集合はbulk受理時に固定する。先行してprojectionへ反映済みのpinは対象判定へ反映される。bulk受理後の操作は対象集合を変更しない。

- Board: `BulkDelete` は既存 `Delete` と同様にsupersession keyを持たず、pending受理順でEnsure/Pin等とfoldする。
- Thread: `BulkDelete` は対象IDに対する先行Ensure/Pin/Infoを既存Deleteと同じ規則でsupersedeする。後続Ensureはbulk全体をcancelせず、bulk完了後に対象タブを再作成する。Thread mutation consumerはbulkをbarrierとして完了まで待ち、後続intentをbulkと並行実行しない。
- 同一IDの重複closeや既に不在の行はNoOpとして収束する。

単一IDとの交差だけでbulk operation全体をsupersedeしてはならない。bulk中の後続操作を並行化する最適化は本changeの範囲外とする。

### 7. targeted ID DELETEを最大900件へchunk化し、1 transactionでcommitする

`OpenBoardTabDao` に `deleteByBoardUrls(List<String>): Int`、`OpenThreadTabDao` に `deleteByThreadIds(List<ThreadId>): Int` 相当の `DELETE ... WHERE id IN (:ids)` を追加する。空ListをDAOへ渡さない。

`TabsRepository` にbulk-close専用APIを追加する。

- Board: 対象をdistinct化し、1回の `DatabaseWriteGate.withWritePermit` と `db.withTransaction` 内で `chunked(900)` を順に削除する。削除件数0はNoOp、1件以上はSuccess、例外はFailureとして既存Board契約へ合わせる。
- Thread: 同じ境界でchunk削除し、削除件数が1件以上の場合だけ `collectGarbageUngated()` をtransaction末尾に1回呼ぶ。空/全件不在はfalse/NoOp、例外は既存Thread mutationと同様に伝播する。

chunkはSQLite bind上限回避のSQL分割であり、transaction分割ではない。900件を超えてもbulk操作全体がall-or-nothingとなる。full replacement、`deleteNotIn`、残存行upsertは呼び出さない。

### 8. holderはStoreのbulk破棄段階でまとめてmapから除去する

Storeは対象keyをSet化し、対応する既存holderを各mapから先にremoveしてローカル一覧へ移し、その一覧を1回走査してdisposeする。holderを遅延生成してはならず、固定・反対ページ・既に不在のholderを破棄しない。同一keyは最大1回disposeする。

既存単体closeと同じく、holder破棄はbulk command受理時に行う。DB失敗でタブがcanonicalから再表示された場合、揮発holderは必要時に再生成される。このセッション状態喪失は既存close失敗時と同じ互換境界であり、永続タブ行の部分削除は発生しない。

### 9. UIは変更しない

`TabListSearchControls.kt`、`AnchoredTabActionMenu.kt`、`TabScreenContent.kt`、文字列リソースは原則変更しない。ViewModelのmenu dismiss順序と `TabPage` 委譲も維持する。必要な変更はStore以降の実行経路のみである。

## Implementation Contract

1. `add-bulk-delete-tabs` の全タスク完了実装を前提に作業し、UI仕様を追加しない。
2. Storeは対象ページの未固定タブを一覧順で一度だけsnapshotし、各Coordinatorのbulk APIを1回だけ呼ぶ。
3. `BulkDelete` は1 pending entryかつ1つの複数key projection operationとして表現し、対象件数分の単体Deleteを登録しない。
4. projection除外はSet membershipによる1回の一覧走査で実装し、canonical確認も対象Setとの非交差で判定する。
5. 最終選択は `selectionAfterTabRemovals` の規範foldと一致させる。
6. Repositoryは対象IDだけを最大900件へchunk化し、全chunkを1 write permit・1 Room transactionで処理する。
7. ThreadState GCは削除成功時にtransaction内で正確に1回呼ぶ。GC上限・TTLは変更しない。
8. `saveOpenBoardTabs`、`replaceOpenThreadTabsForBulkOperation`、`deleteNotIn`をbulk closeから呼ばない。
9. 対象holderは既存mapから一括抽出し、各holderを1回disposeする。holder factoryを呼ばない。
10. Thread bulk intentはbarrierとして後続intentより先に完了させ、単一key競合でbulk全体をsupersedeしない。
11. 新規/変更型と非自明関数はアノテーションより上にKDocを置き、PreviewにはKDocを追加せず、30行超関数はセクションコメントで分割する。
12. アニメーション待機を含むbulk対象snapshotはActivity-retained Store scopeで保持し、caller UIやViewModelの破棄でキャンセルしない。
13. Thread bulkの非キャンセル例外はStoreのretained launch境界でログ記録して封じ込め、`CancellationException`だけはStore lifetime終了として再送出する。

## Error Cases and Compatibility

- 対象0件: pending、transaction、GC、holder破棄を行わない。
- 対象ID重複: 最初の一覧順を維持してdistinct化し、二重DELETE・二重disposeを防ぐ。
- 一部IDが既に不在: 存在する対象だけを削除し、全対象不在のcanonical確認へ収束する。
- DAO/transaction失敗: 全chunkをrollbackし、bulk pendingを除去してcanonical一覧を再表示する。
- caller UI/ViewModel破棄: Store retained scopeが待機中を含む受理済みbulkを完了する。
- Thread bulk例外: Coordinatorがpendingを除去してcanonical一覧へ戻した後、Storeは非キャンセル例外をログ記録し、root coroutineへ伝播させない。
- Store lifetime終了: in-flight commandをcancelし、transactionをrollbackし、waiterをFailureで完了してハングさせない。
- bulk後のEnsure: bulk完了後に通常Ensureとして再作成できる。
- 単体close APIと既存full replacement APIの公開契約は変更しない。

## Testing Strategy

- Primitive JVM test: `selectionAfterTabRemovals` と逐次foldの全組合せ一致、複数key projectionが1操作で順序・変換を維持すること。
- Coordinator JVM test: Board/Thread各bulkでpending entryが1件、全対象即時除外、固定残存、canonical全対象不在確認、最終選択、Empty、NoOp、Failure rollback、teardown、Ensure/Pin/単体Delete競合を検証する。
- Store JVM test: Coordinator bulk APIが1回だけ呼ばれ、対象holderだけが正確に1回disposeされ、固定・反対ページ・未生成holderが維持されること。
- Store delayed bulk test: caller cancellationや公開projectionの変化後も、受理時のBoard/Thread対象snapshotが遅延後に一度だけCoordinatorへ渡されること。
- Store error test: Thread bulkの即時・遅延経路で非キャンセル例外をログ記録して封じ込め、Store lifetimeのCancellationExceptionは再送出すること。
- Repository/Room instrumented test: 0件、1件、900件、901件、1,252件を検証し、対象行だけ削除、固定/反対テーブル不変、1 transactionのrollback、ThreadState GC一回、full replacement非呼び出しを確認する。
- 負荷回帰: 大量件数でもRepository bulk call、pending operation、canonical confirmation、GCが対象件数分に増えないことを回数で検証し、wall-clock閾値は設定しない。
- CI: `./gradlew testCiUnitTest assembleCi --stacktrace`。connected test環境がある場合は追加Repository/Compose対象を `connectedDebugAndroidTest` で実行し、CIに環境がなければ未実行理由を報告する。

## Risks / Trade-offs

- [長い単一transactionが他writeを待たせる] → SQLを900件chunkに限定し、transaction内ではDAO DELETEとGC以外の処理を行わない。
- [bulk中の後続操作との順序が曖昧になる] → Threadはbarrier、Boardは既存pending受理順を契約化し、競合テストを追加する。
- [holderを先に破棄してDB失敗時に揮発状態を失う] → 既存単体closeと同じ互換境界として明示し、永続行はtransaction rollbackで復元する。
- [GC一回では古いorphanを最大100件しか回収しない] → 既存の遅延GC上限を維持し、未回収分は後続GCへ委ねる。
- [projection primitive変更が通常commandへ回帰を与える] → 既存単体Ensure/Delete/Pin/Info/Scrollの全Coordinatorテストを維持する。

## Migration Plan

DB migrationは不要。primitive、DAO/Repository、Coordinator、Storeの順に実装し、全テスト成功後に現行Storeの単体close反復をbulk API呼び出しへ置換する。rollback時はStore入口を既存反復へ戻し、新しいbulk経路を未使用にできる。

## Open Questions

なし。1操作projection、最大900件chunk、bulk単位の単一transaction、GC一回、holder bulk破棄、逐次close相当の最終選択は承認済みである。
