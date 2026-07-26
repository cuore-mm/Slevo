## 1. Test contract を承認済み保証へ縮小

- [ ] 1.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinatorTest.kt` から `togglePinThreadTab_twoRapidTogglesAlternateAfterEachConfirmation`、`togglePinThreadTab_threeRapidTogglesAlternateAfterEachConfirmation`、`togglePinThreadTab_hundredRapidTogglesUsesOnlyTargetedWrites` を削除し、exact same-key ordering を assertion しない rapid toggle test 1 件へ集約する。新 test は targeted `setThreadTabPinned` だけが呼ばれ、必要な canonical pin emission 後に全 waiter が有限に完了し、`replaceOpenThreadTabsForBulkOperation` が 0 回であることだけを確認する。
- [ ] 1.2 同 test file から `pinCommitBeforeCallerCancellation_keepsPendingUntilMatchingFlow` と `pinCommitAndSynchronousCallerCancellation_keepsCommittedResultForReconciliation` を削除する。`cancelledDuringReadiness_doesNotWriteAndWorkerProcessesNextIntent`、repository wait/transaction 中 cancellation、`repositorySuccessBeforeCancellation_doesNotCompensateAndWorkerProcessesNextIntent` は受理境界と Controller ownership の回帰として維持する。
- [ ] 1.3 `assertRapidPinToggles` helper を新しい限定 contract test 専用 helper へ縮小するか、単一 test 内へ展開する。先行 command ごとの completion 順、偶奇回数の最終値、stale false→true→false の順序 assertion が残っていないことを検索で確認する。
- [ ] 1.4 `ensureConfirmation_requiresMergedMetadataMatch` を、Ensure/Info は対象 identity が存在すれば確認でき、Delete の不在条件と Pin の値一致条件は維持される pure test へ書き換える。
- [ ] 1.5 `ensureExistingTab_waitsForMatchingMetadataAfterUnrelatedRevision` を、post-write unrelated snapshot で command が完了可能であり、その後の matching canonical snapshot で新 metadata へ収束する test へ書き換える。Repository mock の write 成功と最終 metadata assertion は残す。
- [ ] 1.6 同 test file の `ensureThreadTab_waitsForInitialSnapshotBeforeDatabaseWrite`、`pendingAdd_survivesStaleSnapshotUntilCanonicalConfirmation`、`loadedEmpty_allowsMutationAfterInitialEmptyEmission`、`failedMutation_restoresCanonicalStateAndContinuesQueue`、`closeSelectedThreadTab_publishesPendingMissingUntilCanonicalConfirmation` を変更せず残したことを diff review で確認する。

## 2. Thread confirmation machinery を一体で簡素化

- [ ] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt` の `awaitConfirmation` を `snapshotVersionFlow.first` と `version > baselineVersion` と operation matcher だけに変更し、同一 commit/stage で `pendingStateRevision`、`pendingStateRevisionFlow`、`registerPending`／`removePending` の revision 更新を削除する。
- [ ] 2.2 同 file の `hasEarlierPendingOperation` と `awaitConfirmation` の predecessor 条件を削除する。`snapshotVersion`／`snapshotVersionFlow`、初回 `awaitLoadedState`、`pendingOperations` の acceptance-order projection は残す。
- [ ] 2.3 `ThreadTabPendingOperation.confirmationKey` が predecessor guard 削除後に selection 用別名としてしか使われないことを確認し、`selectionKey` へ直接 key を返す `when` を置いて不要 helper を削除する。`removePending` の referential `===` lookup は同値 command の誤削除防止のため維持する。
- [ ] 2.4 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsProjection.kt` の `isThreadTabOperationConfirmed` を Ensure/Info=`actual != null`、Delete=`actual == null`、Pin=`actual?.isPinned == requested` に変更し、`matchesThreadMetadata` を削除する。`projectThreadTabs` と `mergeThreadTabMetadata` の projection 利用は変更しない。
- [ ] 2.5 confirmation 変更の diff を確認し、delete absence、single-pin value match、new ensure identity presence より弱い「任意の次 emission」判定、timeout、retry、compensation、generation map が追加されていないことを確認する。

## 3. 未参照 Controller state と履歴を削除

- [ ] 3.1 `ThreadTabsCoordinator.kt` の `_controllerState` と `controllerState` を削除し、`setThreadTabState` と `publishThreadPresentation` にある mirror update 3 箇所を削除する。`_threadTabState`、`_threadLoaded`、`canonicalTabs`、`_openThreadTabs`、`_selectedThreadTabKey`、`_threadPresentationState` の既存公開経路は変更しない。
- [ ] 3.2 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/controller/TabControllerContracts.kt` の `TabControllerState.commandResults` を削除し、`app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/BoardTabsCoordinator.kt` の `finish` から map append だけを削除する。`pending.result.complete(result)` と pending removal は維持する。
- [ ] 3.3 `TabControllerContracts.kt` の未使用 `TabReducerTransition` を削除し、production/test/OpenSpec 以外の Kotlin 参照が 0 件であることを検索で確認する。
- [ ] 3.4 Board の `TabCommandId`、`TabCommandLifecycle`、authoritative `TabControllerState` と Thread/Board の `TabCommandResult` は使用中なので削除しない。Board Deep Link failure test と coordinator terminal result test が compile することを unit test で確認する。

## 4. 未使用 API を call graph の外側から削除

- [ ] 4.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt` の未使用 `registerAndSelectThreadRouteCommand` を削除し、production caller が使う `registerThreadRoute`、`isCanonicalThreadTab`、`selectThreadTab` と `ensureAndSelectThreadTab` は維持する。
- [ ] 4.2 `registerAndSelectThreadRouteCommand` だけが参照していた `ThreadTabsCoordinator.ensureThreadTabCommand` と `selectThreadTabCommand` を削除し、`DeepLinkHandler.handleThreadDeepLinkRoute` の registration→canonical check→selection→navigation 順を変更しない。
- [ ] 4.3 `app/src/main/java/com/websarva/wings/android/slevo/data/repository/TabsRepository.kt` の caller 0 API `ensureOpenThreadTabResult`、`deleteOpenThreadTabResult`、`setThreadTabPinnedResult`、`updateThreadStateResult` を削除する。実働する targeted Boolean/Unit API と Board `TabMutationResult` API は維持する。
- [ ] 4.4 `saveOpenBoardTabs` と `replaceOpenThreadTabsForBulkOperation` は削除せず、通常 production command call site が 0 件、test setup／明示 bulk 境界以外から到達しないことを検索する。full-list writer を代替追加してはならない。
- [ ] 4.5 削除対象 7 API の Kotlin call site が 0 件であることを検索し、見つかった production caller を adapter や duplicate delegation へ付け替えない。真の caller が見つかった場合は該当削除を止めて design の keep/remove 表を更新する。

## 5. Mandatory regression と UI delta の確認

- [ ] 5.1 `./gradlew testDebugUnitTest` を実行し、`TabControllerPrimitivesTest`、`BoardTabsCoordinatorTest`、`ThreadTabsCoordinatorTest`、`TabSessionStoreTest`、`TabScreenCloseCallbacksTest`、`DeepLinkHandlerTest`、`BbsRouteScaffoldSelectionTest` を含む unit suite が成功するまで本変更範囲内の問題を修正する。
- [ ] 5.2 `./gradlew assembleDebug` を実行し、削除した internal API/state の source call-site 漏れと KDoc/comment rule 違反がないことを確認する。
- [ ] 5.3 接続済み device/emulator で `TabsRepositoryThreadStateTest` と `BbsRouteScaffoldTest` を実行し、targeted persistence、metadata safety、Room Flow、atomic presentation の回帰がないことを確認する。device がない場合は未完のまま明示する。
- [ ] 5.4 production diff と call-site search で DAO/schema、resource、manifest、UI text/layout/icon/theme/accessibility/navigation、初回 Loading、normal close、Deep Link behavior に変更がなく、新しい writer、timeout、retry、callback、revision、guard、waiter が追加されていないことを確認する。
- [ ] 5.5 最終差分を量的に確認し、stored state holder 4 個、mirror Flow 1 個、unused type 1 個、unused API 7 個、predecessor function 1 個、metadata comparator 1 個が削除され、exact low-value test 5 件が限定 contract test 1 件へ集約され、metadata test 2 件が書き換えられたことを記録する。実コード差に相違があれば数値だけでなく keep/remove 理由も更新する。
