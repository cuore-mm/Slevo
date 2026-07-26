## 1. Deterministic regression tests

- [ ] 1.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinatorTest.kt` に、初期 snapshot と最終 snapshot だけを emit する rapid same-Thread Pin test を追加する。3 回以上の targeted pin write が受理順の値で呼ばれ、中間 emit なしで先行 waiter が終端し、最終 emit 後に全 waiter と projection が canonical 値へ収束し、`replaceOpenThreadTabsForBulkOperation` が 0 回であることを assertion する。
- [ ] 1.2 同 test file に Ensure→Delete test を追加する。Ensure write と Delete write の間に存在 snapshot を emit せず、successful Delete 後に Ensure が `-1`、最終 absence emit 後に Delete が完了し、対象 tab、selection、session/runtime state が削除状態になることを assertion する。
- [ ] 1.3 同 test file に Delete→Ensure test を追加する。Delete write と Ensure write の間に不在 snapshot を emit せず、successful Ensure が Delete waiter を終端しても selection repair と対象 session/runtime cleanup を行わず、最終 presence emit 後に Ensure が canonical index を返すことを assertion する。
- [ ] 1.4 同 test file に successor failure test を追加する。先行 successful Pin と後続 failed Pin を制御し、後続 failure が先行を supersede せず、先行 canonical emit で先行 waiter だけが成功し、後続 waiter だけが例外になり pending が残らないことを assertion する。
- [ ] 1.5 同 test file に different-Thread independence test を追加する。Thread A の latest canonical confirmation を保留したまま Thread B の rapid mutation を最終 emit で完了させ、B の supersession が A の waiter、projection、result を変更しないことを assertion する。

## 2. Minimal coordinator state

- [ ] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt` に、`ThreadTabPendingOperation`、one-shot supersession signal を保持する private pending entry と private `Confirmed` / `Superseded` resolution を追加する。command ID、generation、map、Job ownership、historyを追加していないことを source review で確認する。
- [ ] 2.2 `pendingOperations`、`registerPending()`、`removePending()`、`publishProjectedTabs()`、selection-key 判定を pending entry 対応に変更する。projection へ受理順の operation だけを渡し、entry を referential identity で 1 件だけ除去し、既存 projection / atomic presentation test が同じ期待値を保つことを確認する。
- [ ] 2.3 `awaitConfirmation()` を、既存の post-baseline operation 条件と entry の supersession signal の先着から resolution を返す処理へ変更する。Pin value、Delete absence、Ensure/Info identity presence の条件を変更せず、pending revision Flow、predecessor barrier、timeoutを追加していないことを確認する。

## 3. Operation-aware resolution

- [ ] 3.1 `ThreadTabsCoordinator.kt` に、successful current entry より前の同一 Thread entry だけを走査する exhaustive supersession helper を追加する。Pin→prior Pin、Delete→prior Ensure/Pin/Info、Ensure→prior Delete のみ signal し、Info、異なる Thread、後続 entry、両立する operation は signal しない unit assertions を 1.x の tests で満たす。
- [ ] 3.2 `processPin()` と `processInfo()` を entry/resolution 対応にし、repository success 後だけ supersession helper を呼び、`Superseded` を `Unit` 完了にする。effective projected pin から target を導出する既存処理、targeted repository call、no-target no-op を維持する。
- [ ] 3.3 `processEnsure()` を entry/resolution 対応にし、successful Ensure で prior Delete を signalし、`Superseded` Ensure は `-1` を返す。`Confirmed` Ensure だけが canonical `_openThreadTabs` の index を返し、metadata exact-match 条件を復元しない。
- [ ] 3.4 `processDelete()` を entry/resolution 対応にし、successful Delete で prior Ensure/Pin/Info を signal する。`Confirmed` Delete だけが adjacent selection repair と `_newResCounts` / `_threadSessionStates` / `_threadRuntimeStates` cleanup を実行し、`Superseded` Delete は cleanup なしで `Unit` 完了する。
- [ ] 3.5 各 process function の exception path が自身の entry だけを referential removal して自身の waiter だけを失敗させ、success 前には先行 signal を発行しないことを failure test と source review で確認する。

## 4. Scope and regression verification

- [ ] 4.1 `git diff -- app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinatorTest.kt` を確認し、production 変更が `ThreadTabsCoordinator.kt` の private implementation に限定され、必要な場合だけ `ThreadTabsProjection.kt` に最小変更があることを確認する。Board、Repository、DAO、DB schema、resources、deferred P2 file に差分があれば除去または OpenSpec 再評価する。
- [ ] 4.2 既存 `ThreadTabsCoordinatorTest`、`TabSessionStoreTest`、`DeepLinkHandlerTest` の metadata merge、retained close lifetime、Deep Link failure/non-navigation、atomic presentation、targeted persistence test が削除・弱体化されていないことを test diff で確認する。
- [ ] 4.3 `./gradlew testDebugUnitTest` を実行し、追加 test と既存 unit test がすべて成功するまで coordinator/test だけを修正する。
- [ ] 4.4 `./gradlew assembleDebug` を実行して成功を確認し、Kotlin compile、KDoc、non-trivial function comment、長い function の section header に repository 規約違反がないことを source review する。
