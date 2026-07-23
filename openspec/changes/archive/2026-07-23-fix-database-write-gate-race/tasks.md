## 1. 事前確認

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGate.kt` を読み、現在の `State(activeWriters, closed, pendingSuspensions, waitingWriters)` と `withWritePermit` / `withWritesSuspended` の入退出処理を確認する。
- [x] 1.2 `app/src/test/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGateTest.kt` を読み、既存テストが保証している順序・cancellation・例外復旧の挙動を一覧化する。
- [x] 1.3 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/export/DatabaseBackupExporter.kt` を確認し、`withWritesSuspended` の呼び出し側 API を変更しないことを確認する。

## 2. DatabaseWriteGate の内部状態を単一 FIFO queue に変更

- [x] 2.1 `DatabaseWriteGate.kt` の private `State` を `activeWriters: Int`、`suspensionActive: Boolean`、`queue: List<Waiter>` を持つ形に変更する。
- [x] 2.2 `DatabaseWriteGate.kt` に private sealed interface `Waiter` と、`WriterWaiter` / `SuspensionWaiter` を追加し、それぞれ `CompletableDeferred<Unit>` を保持する。
- [x] 2.3 `closed`、`pendingSuspensions`、`waitingWriters` 依存の分岐を削除し、単一 queue と `suspensionActive` を使う分岐へ置き換える。
- [x] 2.4 `Waiter`、`WriterWaiter`、`SuspensionWaiter`、writer/suspension state enum など、新規 private type すべてに AGENTS.md の comment/documentation rules に従った KDoc を追加する。
- [x] 2.5 `DatabaseWriteGate` の KDoc を更新し、待機順序が単一 FIFO queue と active writer 予約により制御されることを説明する。

## 3. withWritePermit の入退出処理を修正

- [x] 3.1 `withWritePermit` の即時入場条件を `!suspensionActive && queue.isEmpty()` にする。
- [x] 3.2 即時入場時は `stateLock.withLock` 内で `WriterWaiter(state = RUNNING)` 相当の active token を作り、`activeWriters + 1` を設定してから lock 外で `block()` を実行する。
- [x] 3.3 即時入場できない場合は `WriterWaiter` を queue に追加し、lock 外で `signal.await()` する。
- [x] 3.4 `WriterWaiter` に `QUEUED` / `RESERVED` / `RUNNING` / `RELEASED` 相当の状態を持たせ、queue から取り出して `activeWriters` を予約した時点で `RESERVED` として記録する。
- [x] 3.5 queue 待機中に cancellation された場合は、queue に残っている `WriterWaiter` を取り除き、必要なら `advanceQueueLocked()` を呼ぶ。
- [x] 3.6 `releaseReservedWriterLocked(writer)` 相当の helper を作成し、`RESERVED` または `RUNNING` の writer だけを `RELEASED` に遷移させて `activeWriters` を 1 減らす。すでに `RELEASED` の場合は何もしない。
- [x] 3.7 queue から取り出され予約済みになった後に cancellation された場合は、`signal.await()` が成功する前でも `releaseReservedWriterLocked(writer)` 相当で予約済み `activeWriters` を解放する。
- [x] 3.8 signal await 成功後、`block()` 開始前に writer 状態を `RUNNING` にし、`block()` 完了までを `try/finally` で囲んで `releaseReservedWriterLocked(writer)` 相当を必ず呼ぶ。
- [x] 3.9 await 中 cancellation と block 中 cancellation の両方で release path が同一 helper に集約され、二重減算しないことを確認する。
- [x] 3.10 即時入場 writer の通常完了・例外・cancellation でも `releaseReservedWriterLocked(writer)` 相当を通り、直接 `activeWriters--` しないことを確認する。
- [x] 3.11 writer release 後の queue 前進は `releaseReservedWriterLocked(writer)` 相当に集約し、呼び出し元が追加で `advanceQueueLocked()` を呼ばないことを確認する。

## 4. withWritesSuspended の入退出処理を修正

- [x] 4.1 `withWritesSuspended` の即時開始条件を `activeWriters == 0 && !suspensionActive && queue.isEmpty()` にする。
- [x] 4.2 即時開始時は `stateLock.withLock` 内で `SuspensionWaiter(state = ACTIVE)` 相当の active token を作り、`suspensionActive = true` を設定してから lock 外で `block()` を実行する。
- [x] 4.3 即時開始できない場合は `SuspensionWaiter` を queue に追加し、lock 外で `signal.await()` する。
- [x] 4.4 `SuspensionWaiter` に `QUEUED` / `ACTIVE` / `RELEASED` 相当の状態を持たせ、queue から取り出して `suspensionActive = true` にした時点で `ACTIVE` として記録する。
- [x] 4.5 queue 待機中に cancellation された場合は、queue に残っている `SuspensionWaiter` を取り除き、必要なら `advanceQueueLocked()` を呼ぶ。
- [x] 4.6 `releaseActiveSuspensionLocked(suspension)` 相当の helper を作成し、`ACTIVE` の suspension だけを `RELEASED` に遷移させて `suspensionActive = false` に戻し、queue 前進を行う。すでに `RELEASED` の場合は何もしない。
- [x] 4.7 signal await 成功後、block 開始前に cancellation された場合でも `releaseActiveSuspensionLocked(suspension)` 相当で `suspensionActive` を解放する。
- [x] 4.8 `withWritesSuspended` の `block()` 終了時は `finally` で `releaseActiveSuspensionLocked(suspension)` 相当を必ず呼ぶ。
- [x] 4.9 suspension release 後の queue 前進は `releaseActiveSuspensionLocked(suspension)` 相当に集約し、呼び出し元が追加で `advanceQueueLocked()` を呼ばないことを確認する。
- [x] 4.10 即時開始 suspension の通常完了・例外・cancellation でも `releaseActiveSuspensionLocked(suspension)` 相当を通り、直接 `suspensionActive = false` しないことを確認する。
- [x] 4.11 pending `SuspensionWaiter` の cancellation 後、queue に古い `WriterWaiter` が残っている場合は、新規 `withWritePermit` がそれらを追い越さないことを確認する。

## 5. queue 前進処理を実装

- [x] 5.1 `advanceQueueLocked()` を追加または置換し、`suspensionActive == true` または `activeWriters > 0` の場合は何もしないようにする。
- [x] 5.2 queue が空の場合は何もしないことを確認する。
- [x] 5.3 queue 先頭が `SuspensionWaiter` の場合は 1 件だけ queue から取り出し、`suspensionActive = true` にしてから `signal.complete(Unit)` する。
- [x] 5.4 queue 先頭が `WriterWaiter` の場合は次の `SuspensionWaiter` までの連続 writer をまとめて queue から取り出し、各 writer を予約済みにし、`activeWriters += writerCount` を設定してから各 signal を resume 可能にする。`signal.complete(Unit)` は lock 内、または lock 内で signal を収集して lock 解放直後のどちらでもよいが、queue removal と reservation は必ず complete より先に行う。
- [x] 5.5 `stateLock.withLock` の内側で `await()` や user-provided `block()` を呼んでいないことを確認する。
- [x] 5.6 `State`、queue、`activeWriters`、`suspensionActive`、waiter state enum の読み書きがすべて `stateLock` 内で行われていることを確認する。`await()` と user-provided `block()` は lock 外で実行する。
- [x] 5.7 `advanceQueueLocked`、`releaseReservedWriterLocked`、`releaseActiveSuspensionLocked`、cleanup helper などの非自明 helper に AGENTS.md の comment/documentation rules に従った doc comment を追加する。

## 6. 既存テストの維持と修正

- [x] 6.1 `DatabaseWriteGateTest` の既存テストを新しい内部実装に合わせて必要最小限に修正し、テスト名と検証意図は維持する。
- [x] 6.2 `withWritePermit_doesNotSerializeNormalWrites` が通常 writer 同士の非直列化を引き続き検証していることを確認する。
- [x] 6.3 `withWritesSuspended_queuedInFifoOrder_andWaitingWritersRunAfter` と `withWritesSuspended_doesNotPreemptWaitingWriter` が FIFO 挙動を引き続き検証していることを確認する。
- [x] 6.4 cancellation/exception 復旧系の既存テストが単一 queue 実装でも成功することを確認する。

## 7. race 再現テストの追加

- [x] 7.1 `DatabaseWriteGateTest` に、停止区間終了で再開された writer の block 中に後続 `withWritesSuspended` が開始しないことを検証するテストを追加する。このテストは、先行 suspension が writer を解放した後、再開 writer が完了する前に後続 `withWritesSuspended` を要求し、後続 suspension の block が writer 完了まで未開始であることを assert する。
- [x] 7.2 `DatabaseWriteGateTest` に、複数 writer が再開される場合、後続 `withWritesSuspended` が全 writer 完了まで開始しないことを検証するテストを追加する。
- [x] 7.3 `DatabaseWriteGateTest` に、予約済み writer が block 開始前または開始直後に cancellation されても後続 operation が詰まらないことを検証するテストを追加する。
- [x] 7.4 `DatabaseWriteGateTest` に、予約済み writer が await 復帰前・block 開始前・block 実行中のいずれかで cancellation されても active writer 予約が一度だけ解放されることを検証するテストを追加する。
- [x] 7.5 `DatabaseWriteGateTest` に、queued suspension が active 化された後、block 開始前に cancellation されても `suspensionActive` が解放され、後続 operation が詰まらないことを検証するテストを追加する。
- [x] 7.6 `DatabaseWriteGateTest` に、active 化された suspension が block 開始前・block 実行中のいずれかで cancellation されても active 状態が一度だけ解放されることを検証するテストを追加する。
- [x] 7.7 `DatabaseWriteGateTest` に、即時入場 writer が通常完了・例外・cancellation のいずれでも active writer 予約を一度だけ解放することを検証するテストを追加または既存テストで確認する。
- [x] 7.8 `DatabaseWriteGateTest` に、即時開始 suspension が通常完了・例外・cancellation のいずれでも active 状態を一度だけ解放することを検証するテストを追加または既存テストで確認する。
- [x] 7.9 `DatabaseWriteGateTest` に、pending suspension が active writer 待機中に cancellation され、queue に残った古い writer を新規 writer が追い越さないことを検証するテストを追加する。
- [x] 7.10 `DatabaseWriteGateTest` に、`withWritePermit` と `withWritesSuspended` が block の成功戻り値をそのまま返すことを検証するテストを追加または既存テストで確認する。
- [x] 7.11 `DatabaseWriteGateTest` に、`withWritePermit` と `withWritesSuspended` が block の例外を cleanup 後に同じ例外として伝播することを検証するテストを追加または既存テストで確認する。
- [x] 7.12 `DatabaseWriteGateTest` に、`withWritePermit` と `withWritesSuspended` の cancellation が cleanup 後も cancellation として伝播することを検証するテストを追加または既存テストで確認する。
- [x] 7.13 新規テストでは `CompletableDeferred` などの barrier を使い、event list の順序だけに依存しない deterministic な検証にする。

## 8. バックアップ連携の確認

- [x] 8.1 `DatabaseBackupExporterTest` で、`DatabaseBackupExporter` が gate の `withWritesSuspended` 経由で export 処理を実行する既存テストを特定し、変更後も成功することを確認する。
- [x] 8.2 8.1 に該当する既存テストが存在しない場合は、`DatabaseBackupExporter` が `withWritesSuspended` semantics を維持していることを検証する regression test を追加する。
- [x] 8.3 バックアップ export 中に writer が待機し、export 完了後に writer が再開されることを、既存テストまたは新規テストのいずれかで必ず確認する。

## 9. 検証

- [x] 9.1 GitHub Actions の Android CI で unit test と build を実行し、`DatabaseWriteGateTest` と `DatabaseBackupExporterTest` が成功することを確認する。local Gradle は明示的に許可された場合のみ補助的に使う。
- [x] 9.2 CI failure が出た場合は、失敗ログを確認して `DatabaseWriteGate` の順序制御または test synchronization を修正する。
- [x] 9.3 `openspec validate fix-database-write-gate-race --strict` を実行し、OpenSpec validation が成功することを確認する。
