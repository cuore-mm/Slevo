## 1. Deterministic cancellation test seam

- [x] 1.1 `DatabaseWriteGateTest.kt`の既存dispatcher、barrier、timeout utilityを確認し、lock競合とreserved transitionを再現できる既存手段を列挙する。完了条件: production test seamが必要なwindowと不要なwindowをtest commentまたは実装メモで明確にする。
- [x] 1.2 `DatabaseWriteGate.kt`へstate mutationを行わないinternal lock-contention test seamを追加する。完了条件: JVM testがstate lockを保持したことと解放timingをbarrierで制御でき、public APIとHilt constructorは変わらない。
- [x] 1.3 signal受信後・`RESERVED -> RUNNING`前を既存utilityで停止できない場合だけinternal no-op hookを追加する。完了条件: testはwriterをRESERVED windowでdeterministicにcancelでき、production defaultは即returnである。
- [x] 1.4 test seamへKDocを追加する。完了条件: test専用、state mutation禁止、production default behaviorが記載されている。

## 2. NonCancellable state-lock境界

- [x] 2.1 `DatabaseWriteGate.kt`へ`NonCancellable`、`withContext`、`currentCoroutineContext`、`ensureActive`の必要importを追加する。完了条件: unused importがなくcompileする。
- [x] 2.2 private `withNonCancellableStateLock(action)` helperを追加する。完了条件: `withContext(NonCancellable)`内で`stateLock.withLock`を取得し、non-suspend actionだけを受ける。
- [x] 2.3 helperへKDocを追加する。完了条件: cancellation中もgate state cleanupを完了すること、user block/I/Oを渡さない制約が説明されている。

## 3. Writer ownershipの一元化

- [x] 3.1 `withWritePermit()`で`WriterWaiter`取得直後からqueue await、reserved transition、user blockを単一`try/finally`で囲む。完了条件: waiter取得後の全return/throw/cancel pathが同じfinallyへ到達する。
- [x] 3.2 queued writerの`signal.await()`専用catch cleanupを削除し、finallyから`cleanupWriter()`を1回だけ呼ぶ。完了条件: queued cancellationでqueue除去され、double cleanupがない。
- [x] 3.3 `RESERVED -> RUNNING`遷移を`withNonCancellableStateLock()`内へ移す。完了条件: transition lock競合中のcancellationでreservationが残留しない。
- [x] 3.4 transition直後・user block前にoriginal coroutine contextの`ensureActive()`を呼ぶ。完了条件: signal受信後にcancel済みwriterはuser blockを実行しない。
- [x] 3.5 `cleanupWriter()`を`withNonCancellableStateLock()`経由へ変更し、QUEUEDはidentityでqueue除去、RESERVED/RUNNINGはrelease、RELEASEDはno-opとする。完了条件: `activeWriters`を二重減算せず、idle時にqueueを前進させる。
- [x] 3.6 writerの正常return、通常exception、cancellationがcleanup後にそのまま伝播することを既存testsと新規testsで検証する。

## 4. Suspension ownershipの一元化

- [x] 4.1 `withWritesSuspended()`で`SuspensionWaiter`取得直後からqueue awaitとuser blockを単一`try/finally`で囲む。完了条件: waiter取得後の全return/throw/cancel pathが同じfinallyへ到達する。
- [x] 4.2 queued suspensionの`signal.await()`専用catch cleanupを削除し、finallyから`cleanupSuspension()`を1回だけ呼ぶ。完了条件: queued cancellationでqueue除去され、double cleanupがない。
- [x] 4.3 `cleanupSuspension()`を`withNonCancellableStateLock()`経由へ変更し、QUEUEDはidentityでqueue除去、ACTIVEはrelease、RELEASEDはno-opとする。完了条件: `suspensionActive`を確実にfalseへ戻し、idle時にqueueを前進させる。
- [x] 4.4 suspensionの正常return、通常exception、cancellationがcleanup後にそのまま伝播することを既存testsと新規testsで検証する。

## 5. Writer cancellation regression tests

- [x] 5.1 queued writerのcleanup lockをtest seamで競合させてcancelするtestを`DatabaseWriteGateTest.kt`へ追加する。完了条件: lock解放前はcancel jobがcleanup待ち、解放後はcancel完了し、fresh writerとfresh suspensionがtimeout内に完了する。
- [x] 5.2 running writer blockのrelease lockを競合させてcancelするtestを追加する。完了条件:元の`CancellationException`が伝播し、後続suspensionが開始する。
- [x] 5.3 signal受信後・reserved transition前でwriterをcancelするtestを追加する。完了条件: writer block未実行、reserved token解放、後続suspension完了を検証する。
- [x] 5.4 `RESERVED -> RUNNING`遷移後にcancel済みを観測するtestを追加する。完了条件: `ensureActive()`がuser block開始を防ぎ、active writer tokenが解放される。
- [x] 5.5 queued writerをFIFO queue中間からcancelするtestを追加する。完了条件: 残るwaiterの相対順序が維持される。

## 6. Suspension cancellation regression tests

- [x] 6.1 queued suspensionのcleanup lockをtest seamで競合させてcancelするtestを追加する。完了条件: queueから除去され、後続writerがtimeout内に完了する。
- [x] 6.2 active suspension blockのrelease lockを競合させてcancelするtestを追加する。完了条件: `suspensionActive`残留がなく、fresh writerとfresh suspensionがtimeout内に完了する。
- [x] 6.3 queued suspensionをFIFO queue中間からcancelするtestを追加する。完了条件: 残るwriter/suspensionが元の相対順序で進行する。
- [x] 6.4 cancellation testsで`runCurrent`/barrier/timeoutを使い、sleepとprobabilistic stressだけに依存しない。完了条件: 各target windowへ到達したことをassertしてからcancelする。

## 7. Existing gate contract regression

- [x] 7.1 連続writer groupが次のsuspensionより先に進む既存FIFO/batching testsを回帰実行する。完了条件: ordering assertionが変更なしで成功する。
- [x] 7.2 suspension中に新規writerが開始しない既存exclusivity testsを回帰実行する。完了条件: overlap assertionが成功する。
- [x] 7.3 writer/suspensionの正常return値、通常exception、nested cancellation testsを回帰実行する。完了条件: cleanup変更でpublic契約が変わらない。
- [x] 7.4 新規testsの最後に同じgate instanceでfresh writerとfresh suspensionを実行する。完了条件: cancellation後の再利用可能性を全主要windowで確認する。

## 8. Documentationと最終検証

- [x] 8.1 `DatabaseWriteGate.kt`のtype/function commentsを単一ownership cleanup構造へ更新する。完了条件: lifecycle、NonCancellable範囲、`ensureActive()` branch、lock内suspend禁止が説明されている。
- [x] 8.2 `openspec validate prevent-database-write-gate-cancellation-leak --strict`と`git diff --check`を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 8.3 `DatabaseWriteGateTest`を含む全unit testsをAndroid CIで実行する。完了条件: workflow実行前に記録したHEADと`headSha`が一致し、unit testsとCI APK buildが成功する。
- [x] 8.4 最終diffを確認する。完了条件: `DatabaseWriteGate.kt`、`DatabaseWriteGateTest.kt`、関連OpenSpec filesだけであり、Repository、Room schema、backup/restore、UI、dependency変更を混在させていない。
- [x] 8.5 implementationとtask completionをcommit/pushする。完了条件: remote branchへpush成功し、working treeがcleanである。
