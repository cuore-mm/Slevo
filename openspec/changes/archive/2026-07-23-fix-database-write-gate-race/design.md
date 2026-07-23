## Context

`DatabaseWriteGate` は `app/src/main/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGate.kt` にあり、通常の Room DB 書き込みを `withWritePermit`、バックアップなどの排他処理を `withWritesSuspended` で制御している。

現在の実装は以下の状態を持つ。

- `activeWriters`: 実行中 writer 数。
- `closed`: suspension 要求により writer の新規入場を止める flag。
- `pendingSuspensions`: 待機中 suspension の FIFO queue。
- `waitingWriters`: closed 中に待機している writer の FIFO queue。

Codex review で指摘された問題は、`waitingWriters` を `complete(Unit)` で再開した後、writer が `activeWriters` を増やす前に新しい `withWritesSuspended` が `activeWriters == 0 && !closed` を見て開始できる点である。この場合、再開済み writer と新しい suspension が同時に block を実行し、`DatabaseBackupExporter` が期待する DB snapshot の排他性が破れる。

主な関連ファイルは以下。

- `app/src/main/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGate.kt`
- `app/src/test/java/com/websarva/wings/android/slevo/data/database/DatabaseWriteGateTest.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/data/backup/export/DatabaseBackupExporter.kt`
- `app/src/test/java/com/websarva/wings/android/slevo/data/backup/export/DatabaseBackupExporterTest.kt`

既存の外部 API は維持する。

```kotlin
suspend fun <T> withWritePermit(block: suspend () -> T): T
suspend fun <T> withWritesSuspended(block: suspend () -> T): T
```

## Goals / Non-Goals

**Goals:**

- 再開済み writer と後続 `withWritesSuspended` の race を解消する。
- `withWritesSuspended` の block 中に `withWritePermit` の block が同時実行されないことを保証する。
- waiting writer が後続 suspension に追い越されない FIFO 方針を維持する。
- 通常時の `withWritePermit` 同士は gate で直列化しない既存仕様を維持する。
- block の例外・キャンセル時に gate 状態が復旧する既存仕様を維持する。
- Codex 指摘の interleaving を再現する unit test を追加する。

**Non-Goals:**

- Repository や DAO の呼び出し API は変更しない。
- `DatabaseBackupExporter` のバックアップ手順は変更しない。
- Room transaction や SQLite checkpoint の仕様は変更しない。
- DataStore 書き込みを `DatabaseWriteGate` の対象に追加しない。

## Decisions

### Decision 1: `waitingWriters` と `pendingSuspensions` を単一 FIFO queue に統合する

現在の二重 queue は、writer 再開と suspension 開始の順序を複数の状態で表現しており、再開済みだが `activeWriters` に未反映の writer が発生する。これを避けるため、writer と suspension の待機順序を 1 本の queue に統合する。

推奨する内部モデル:

```kotlin
private sealed interface Waiter {
    val signal: CompletableDeferred<Unit>
}

private data class WriterWaiter(
    override val signal: CompletableDeferred<Unit>,
) : Waiter

private data class SuspensionWaiter(
    override val signal: CompletableDeferred<Unit>,
) : Waiter

private data class State(
    val activeWriters: Int = 0,
    val suspensionActive: Boolean = false,
    val queue: List<Waiter> = emptyList(),
)
```

`closed` は `suspensionActive || queue.any { it is SuspensionWaiter }` に相当する状態として扱い、独立 flag としては持たない。

代替案として、現在の二重 queue のまま writer 再開後に `closed` を再確認する方法がある。しかしこの案は、再開された writer が後続 suspension に追い越される挙動になりやすく、既存仕様「待機中 writer は後続 suspension に追い越されない」と相性が悪い。そのため採用しない。

### Decision 2: writer を起こす前に `activeWriters` を予約する

queue の先頭が writer の場合、次の suspension までの連続 writer 群をまとめて取り出す。このとき `stateLock` 内で先に `activeWriters += writerCount` を行い、その後に writer の `signal.complete(Unit)` を呼ぶ。

必須 invariant は「waiter が resume 可能になる前に、queue removal と active state reservation が `stateLock` 内で完了していること」である。`signal.complete(Unit)` は lock 内で実行してもよいし、予約済み waiter の signal を lock 内で収集して lock 解放直後に complete してもよい。ただし complete 前に他 coroutine から race を起こす state が観測されないよう、queue removal と reservation は必ず complete より先に行う。

```text
queue = [W1, W2, S1, W3]
advanceQueueLocked()
  -> W1/W2 を queue から外す
  -> activeWriters += 2
  -> W1/W2 を complete
```

これにより、writer coroutine が実際に resume される前から `activeWriters` が反映される。直後に新しい `withWritesSuspended` が到着しても `activeWriters > 0` を見るため、排他 block を開始できない。

連続 writer 群は同一 writer group としてまとめて再開する。FIFO は「古い writer group を後続 suspension や新規 writer が追い越さない」ことを保証するものであり、同一 group 内の writer block 開始順を厳密に直列化しない。通常時の writer 同士を gate で直列化しない既存仕様を維持する。

### Decision 3: suspension は `suspensionActive == false && activeWriters == 0` の場合のみ開始する

`withWritesSuspended` は以下の条件をすべて満たすときのみ即時開始する。

- `activeWriters == 0`
- `suspensionActive == false`
- `queue.isEmpty()`

それ以外の場合は `SuspensionWaiter` として queue に追加する。queue の先頭が `SuspensionWaiter` の場合、1 件だけ取り出し、`suspensionActive = true` にしてから `signal.complete(Unit)` する。

### Decision 4: `withWritePermit` の即時入場条件は「suspension が active でなく、queue が空」

通常 writer は、以下の場合に即時入場できる。

- `suspensionActive == false`
- `queue.isEmpty()`

この条件により、通常時の writer 同士は引き続き gate で直列化されない。一方、queue に古い待機 writer が残っている場合、新しい writer は即時入場せず queue の末尾に並ぶ。これにより、待機中 suspension が cancellation で取り除かれた後でも、新しい writer が古い queued writer を追い越さない。

### Decision 5: 即時入場と queue 再開の両方を active token/helper で解放する

`withWritePermit` と `withWritesSuspended` は、即時入場の場合も queue から再開される場合も同じ release helper を通す。呼び出し元が直接 `activeWriters--` または `suspensionActive = false` を行ってはならない。

即時 `withWritePermit` は `stateLock.withLock` 内で `WriterWaiter(state = RUNNING)` 相当の active token を作り、`activeWriters += 1` してから lock 外で `block()` を実行する。block の `finally` では `releaseReservedWriterLocked(writer)` 相当だけを呼ぶ。

即時 `withWritesSuspended` は `stateLock.withLock` 内で `SuspensionWaiter(state = ACTIVE)` 相当の active token を作り、`suspensionActive = true` にしてから lock 外で `block()` を実行する。block の `finally` では `releaseActiveSuspensionLocked(suspension)` 相当だけを呼ぶ。

queue から再開される writer/suspension も同じ token と release helper を使う。これにより、即時入場と queue 再開で cleanup owner が分岐しない。

`advanceQueueLocked()` は次の実行対象を予約/activate する関数であり、実行済み block の release は行わない。

`advanceQueueLocked()` の処理順序:

1. `suspensionActive == true` または `activeWriters > 0` の場合は何もしない。
2. queue が空なら何もしない。
3. queue の先頭が `SuspensionWaiter` なら 1 件だけ開始する。
4. queue の先頭が `WriterWaiter` なら、次の `SuspensionWaiter` までの連続 writer をまとめて開始する。

### Decision 6: cancellation は queue 待機中と予約済み writer を明示的に区別する

queue に残っている waiter がキャンセルされた場合は、queue から取り除き、必要なら `advanceQueueLocked()` を呼ぶ。

writer は再開前に `activeWriters` が予約されるため、`signal.await()` から戻る前後で coroutine がキャンセルされると、`block()` が実行されないまま予約分を解放する必要がある。実装では `WriterWaiter` に予約状態を持たせ、cleanup で「まだ queue 内にいる waiter」と「queue から取り出され active writer として予約済みの waiter」を区別する。

推奨モデル:

```kotlin
private enum class WriterWaiterState {
    QUEUED,
    RESERVED,
    RUNNING,
    RELEASED,
}

private data class WriterWaiter(
    override val signal: CompletableDeferred<Unit>,
    var state: WriterWaiterState = WriterWaiterState.QUEUED,
) : Waiter

private enum class SuspensionWaiterState {
    QUEUED,
    ACTIVE,
    RELEASED,
}

private data class SuspensionWaiter(
    override val signal: CompletableDeferred<Unit>,
    var state: SuspensionWaiterState = SuspensionWaiterState.QUEUED,
) : Waiter
```

`advanceQueueLocked()` が writer を queue から取り出すときは、同じ `stateLock.withLock` 内で以下を必ず行う。

1. `writer.state = WriterWaiterState.RESERVED`
2. `activeWriters += writerCount`
3. queue から writer 群を削除
4. `signal.complete(Unit)`

予約済み writer の解放は、1 箇所の helper でのみ行う。

```kotlin
private fun releaseReservedWriterLocked(writer: WriterWaiter) {
    if (writer.state == WriterWaiterState.RELEASED) return
    if (writer.state == WriterWaiterState.RESERVED || writer.state == WriterWaiterState.RUNNING) {
        writer.state = WriterWaiterState.RELEASED
        stateRef.set(stateRef.get().copy(activeWriters = stateRef.get().activeWriters - 1))
        if (stateRef.get().activeWriters == 0) advanceQueueLocked()
    }
}
```

実装時は上記の意図を保てば、実際の helper 名や `stateRef.set` の書き方は調整してよい。ただし `activeWriters` を減らす経路はこの helper に集約し、二重減算を防ぐこと。

queue advancement の所有者は `releaseReservedWriterLocked(writer)` に統一する。writer release の呼び出し元は `activeWriters` 減算後に追加で `advanceQueueLocked()` を呼ばない。これにより、writer cancellation path と通常終了 path のどちらでも queue 前進が一度だけ行われる。

`withWritePermit` の cancellation cleanup は以下の順序で処理する。

1. queue に waiter が残っている場合は queue から削除する。
2. queue に残っておらず `writer.state == RESERVED` または `RUNNING` の場合は、`releaseReservedWriterLocked(writer)` で予約済み active writer 数を 1 減らす。
3. 2 の path では呼び出し元から追加で `advanceQueueLocked()` を呼ばない。queue 前進は `releaseReservedWriterLocked(writer)` の責務とする。
4. 1 の queued waiter removal path では active writer は減らさない。gate が idle (`activeWriters == 0 && !suspensionActive`) の場合のみ `advanceQueueLocked()` を呼び、queue 内の後続要素を進める。

この cleanup は `signal.await()` が `CancellationException` を投げた場合にも実行する。つまり、予約済み writer の cleanup は「await 成功後」だけでなく「await 中に予約済みになった直後の cancellation」も覆う必要がある。

`signal.await()` が成功して `block()` を開始する直前には `writer.state = RUNNING` にする。`block()` の `finally` では必ず `releaseReservedWriterLocked(writer)` を呼ぶ。これにより、await 中 cancellation と block 中 cancellation のどちらも同じ release path を通り、二重 release は `RELEASED` state で抑止される。

activated suspension も同様に release ownership を 1 箇所へ集約する。queue から取り出され `suspensionActive = true` に設定された `SuspensionWaiter` は `ACTIVE` state とし、以下の helper 相当でのみ解除する。

```kotlin
private fun releaseActiveSuspensionLocked(suspension: SuspensionWaiter) {
    if (suspension.state == SuspensionWaiterState.RELEASED) return
    if (suspension.state == SuspensionWaiterState.ACTIVE) {
        suspension.state = SuspensionWaiterState.RELEASED
        stateRef.set(stateRef.get().copy(suspensionActive = false))
        advanceQueueLocked()
    }
}
```

`withWritesSuspended` は queued `SuspensionWaiter` の `signal.await()` が成功した後、block 開始前に cancellation される場合も `releaseActiveSuspensionLocked(suspension)` を実行する。block 開始後も `finally` で同じ helper を呼ぶ。これにより、activated suspension の cancellation で `suspensionActive = true` が残り続ける状態を防ぐ。

実装時は `stateLock.withLock` の内側で suspend 関数を呼ばないこと。`CompletableDeferred.await()` は必ず lock 外で実行する。

### Decision 7: pending suspension cancellation 後も queued writer の FIFO を守る

pending `SuspensionWaiter` が cancellation で queue から取り除かれた場合、queue 内に残っている古い `WriterWaiter` を新しい `withWritePermit` が追い越してはならない。

このため、`withWritePermit` の即時入場条件は `queue.isEmpty()` を含める。active writer がまだ実行中で `advanceQueueLocked()` が writer 群を開始できない場合でも、新しい writer は queue の末尾へ追加する。active writer が 0 になった時点で、queue 先頭から連続する writer 群をまとめて予約して再開する。

## Implementation Contract

実装担当者は以下を満たすこと。

1. `DatabaseWriteGate.kt` の public API signature を変更しない。
2. `State` は単一 queue モデルへ変更する。
3. writer を `complete(Unit)` する前に、同じ `stateLock.withLock` 内で `activeWriters` を予約する。
4. `withWritesSuspended` の block 開始前に `suspensionActive = true` を設定する。
5. `stateLock.withLock { ... }` の内側で `await()` や user-provided `block()` を呼ばない。
6. `withWritePermit` と `withWritesSuspended` の `block()` は lock 外で実行する。
7. 既存の `DatabaseWriteGateTest` のテストをすべて維持し、新しい race 再現テストを追加する。
8. `DatabaseBackupExporter.kt` の呼び出し側 API は変更しない。
9. 予約済み writer の cancellation cleanup は、`signal.await()` が成功する前に cancellation された場合も `activeWriters` を解放する。
10. pending suspension cancellation 後に queue に残った writer を、新規 writer が追い越さない。
11. activated suspension の cancellation cleanup は、`signal.await()` 成功後から block 開始前に cancellation された場合も `suspensionActive` を解放する。
12. writer release 後の queue 前進は `releaseReservedWriterLocked` 相当、suspension release 後の queue 前進は `releaseActiveSuspensionLocked` 相当に集約し、呼び出し元が重複して `advanceQueueLocked()` を呼ばない。
13. 即時入場 writer/suspension も active token を作成し、queue 再開 path と同じ release helper で cleanup する。
14. `withWritePermit` と `withWritesSuspended` は、成功時に wrapped `block()` の戻り値をそのまま返す。
15. `block()` が投げた exception は cleanup 後に同じ exception として再 throw する。別例外への wrap や握りつぶしをしない。
16. coroutine cancellation は cleanup 後も cancellation として伝播させる。`CancellationException` を通常失敗に変換しない。
17. 新規または変更する private type (`Waiter`、`WriterWaiter`、`SuspensionWaiter`、state enum など) と非自明 helper (`advanceQueueLocked`、release/cleanup helper など) には AGENTS.md の comment/documentation rules に従って KDoc/doc comment を付ける。
18. `State`、queue、`activeWriters`、`suspensionActive`、waiter state enum の読み書きはすべて `stateLock` 内で行う。例外は `CompletableDeferred.await()` と user-provided `block()` の実行のみで、これらは必ず lock 外で行う。

## Risks / Trade-offs

- [Risk] 単一 queue への置換で既存 FIFO 仕様を崩す可能性がある。  
  → 既存 `DatabaseWriteGateTest` を維持し、writer/suspension の開始順を event log で検証する。
- [Risk] 予約済み writer の cancellation 処理を誤ると `activeWriters` が残り、suspension が永久待機する。  
  → `WriterWaiter.reserved` などの予約状態を持ち、`signal.await()` が cancellation で失敗した場合も予約分を減算するテストを追加する。
- [Risk] pending suspension cancellation 後に queue 内の古い writer を新しい writer が追い越す可能性がある。  
  → `withWritePermit` の即時入場条件に `queue.isEmpty()` を含め、queue が空でない限り新規 writer は末尾に並ぶ。
- [Risk] activated suspension の cancellation 処理を誤ると `suspensionActive` が残り、全 writer/suspension が永久待機する。  
  → `SuspensionWaiterState.ACTIVE` と `releaseActiveSuspensionLocked` 相当を使い、await 成功後から block 終了まで同じ release path を通す。
- [Risk] 通常 writer 同士を意図せず直列化すると UI 操作や既存 repository 書き込みに不要な待機が増える。  
  → `withWritePermit_doesNotSerializeNormalWrites` を維持し、並行 writer が同時に block へ入れることを検証する。
- [Risk] test scheduler で race を deterministic に再現しにくい。  
  → `CompletableDeferred` と `Mutex`/barrier を使い、writer が再開待ち状態になったこと、suspension が開始しようとしたことを明示的に制御する。

## Migration Plan

1. `DatabaseWriteGate.kt` の内部状態を単一 queue に変更する。
2. 既存テストを実行し、現行仕様の回帰がないことを確認する。
3. race 再現テストを追加する。
4. `DatabaseBackupExporterTest` を実行し、バックアップ側の gate 利用が維持されていることを確認する。
5. GitHub Actions の Android CI で unit test と build を確認する。local Gradle は明示的に許可された場合のみ補助的に使う。

Rollback は単一ファイルの実装差し戻しで可能。ただし Codex 指摘の race が再発するため、rollback する場合は代替修正を同時に入れる。

## Testing Strategy

実装後は以下を確認する。

- GitHub Actions の Android CI。local Gradle (`./gradlew testDebugUnitTest`) は明示的に許可された場合のみ補助的に使う。
- `DatabaseWriteGateTest` の既存全ケース。
- `DatabaseBackupExporterTest` の既存全ケース。少なくとも `DatabaseBackupExporter` が `withWritesSuspended` を通じて export 処理を実行することを検証する既存テストを特定し、成功を確認する。該当テストが存在しない場合は、`DatabaseBackupExporter` が gate の `withWritesSuspended` semantics を維持していることを検証する regression test を追加する。
- 新規テスト:
  - 再開 writer が後続 suspension に追い越されない。
  - 再開 writer の block 中に後続 suspension が開始しない。
    - main regression test は、先行 suspension が待機 writer を解放した後、再開 writer が完了する前に後続 `withWritesSuspended` を要求し、その後続 suspension の block が writer 完了まで開始していないことを明示的に assert する。
  - 複数 writer 再開時に後続 suspension は全 writer 完了まで待つ。
  - 予約済み writer が cancellation されても gate が詰まらない。
  - 予約済み writer の release が一度だけ行われ、`activeWriters` の二重減算やリークが発生しない。
  - activated suspension が block 開始前に cancellation されても `suspensionActive` が解放され、queue が継続する。
  - 即時入場 writer/suspension が通常完了・例外・cancellation のいずれでも同じ release helper により cleanup される。
  - 両 API の成功時戻り値が `block()` の戻り値と一致する。
  - 両 API の block exception が cleanup 後に同じ exception として伝播する。
  - 両 API の cancellation が cleanup 後も cancellation として伝播する。
  - pending suspension cancellation 後、古い queued writer を新規 writer が追い越さない。
- `openspec validate fix-database-write-gate-race --strict`。

## Open Questions

- なし。実装方針は単一 FIFO queue と writer 予約で固定する。
