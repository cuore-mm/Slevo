## Context

`DatabaseWriteGate.kt`は単一FIFO queueと`stateLock: Mutex`で通常writerと`withWritesSuspended`を調停する。writer lifecycleは`QUEUED -> RESERVED -> RUNNING -> RELEASED`、suspension lifecycleは`QUEUED -> ACTIVE -> RELEASED`である。

現在は次のcancellation windowがある。

1. queued writerの`signal.await()`がcancelされ、`cleanupWriter()`内の`stateLock.withLock`もcancelされる。
2. running writerのuser blockがcancelされ、`finally`内の`stateLock.withLock`もcancelされる。
3. queued suspensionの`signal.await()`がcancelされ、`cleanupSuspension()`内のlock待ちもcancelされる。
4. active suspensionのuser blockがcancelされ、`finally`内のlock待ちもcancelされる。
5. queued writerがsignalを受けて`RESERVED`になった後、`RESERVED -> RUNNING`用lock待ちでcancelされ、block用`try/finally`へ到達しない。

`Mutex.withLock`はlockが空いていればcancel済みcontextでも即時成功し得るが、lock競合時はcancellable suspensionとなり`CancellationException`を投げる。そのため既存testsは非競合時に通っても、production contentionでは`activeWriters`、`suspensionActive`、queue entryが残留できる。

## Goals / Non-Goals

**Goals:**

- waiter/tokenを取得した全coroutineが、任意のcancellation pointから必ずidempotent cleanupへ到達する。
- cleanup/releaseに必要な`stateLock`取得とstate mutationをcancellationから保護する。
- `RESERVED -> RUNNING`遷移前後のcancellationでもwriter reservationを解放する。
- cleanup後に元の`CancellationException`、例外、戻り値を維持する。
- lock競合下のcancellation後も後続writer/suspensionが進行することをdeterministic testsで固定する。

**Non-Goals:**

- FIFO grouping、writer batching、suspension exclusivityの変更。
- public API、Repository call site、Room transaction、backup/export処理の変更。
- user blockやDB I/Oを`NonCancellable`にすること。
- timeout、retry、fairness policyの追加。

## Decisions

### 1. Waiter取得直後から単一ownership `try/finally`を開始する

`withWritePermit()`は`WriterWaiter`をstateへ登録した直後から、queue await、`RESERVED -> RUNNING`、user blockを1つの`try/finally`で囲む。個別の`signal.await()` catch cleanupは削除し、finallyから1回だけwriter cleanupを呼ぶ。

概念構造:

```kotlin
val writer = admitWriter()
try {
    if (writer.state == QUEUED) {
        writer.signal.await()
        withNonCancellableStateLock {
            if (writer.state == RESERVED) writer.state = RUNNING
        }
    }
    currentCoroutineContext().ensureActive()
    return block()
} finally {
    cleanupWriter(writer)
}
```

`cleanupWriter()`はstate別にidempotent処理する。

- `QUEUED`: queueからidentity一致で除去し、idleならqueueを進める。
- `RESERVED`/`RUNNING`: `releaseReservedWriterLocked()`で1 token解放する。
- `RELEASED`: no-op。

これによりsignal受信後、transition lock待ち、transition完了直後、user block内のどこでcancelされても同じcleanupへ到達する。

`withWritesSuspended()`も`SuspensionWaiter`取得直後からqueue awaitとuser blockを単一`try/finally`で囲み、finallyから`cleanupSuspension()`を1回だけ呼ぶ。

**代替案:** catch/finally/transitionの5箇所を個別に`NonCancellable`化する方法は動作するが、新しいstateやreturn path追加時に保護漏れを再発しやすいため採用しない。

### 2. Gate state cleanup専用のNonCancellable lock helperを使う

`DatabaseWriteGate`へprivate helperを追加する。

```kotlin
private suspend fun <T> withNonCancellableStateLock(action: () -> T): T =
    withContext(NonCancellable) {
        stateLock.withLock { action() }
    }
```

helperの対象は`stateLock`取得と同期的なstate mutationだけとする。`action`はnon-suspend lambdaとし、lock保持中に`suspend`関数を呼べない型契約を維持する。

次をhelper経由にする。

- `cleanupWriter()`のqueue除去/release。
- `cleanupSuspension()`のqueue除去/release。
- queued writerの`RESERVED -> RUNNING`遷移。

user block、`signal.await()`、DB I/O、外部callbackは`NonCancellable`へ含めない。

### 3. Cancellation propagationを明示的に維持する

単一`try/finally`ではcatchして変換せず、finally cleanup完了後に元の`CancellationException`を自然伝播させる。`ensureActive()`を`RESERVED -> RUNNING`直後かつuser block前に実行し、transition待ち中にcancelされたwriterが新しいuser workを開始しないようにする。

`ensureActive()`がthrowしてもownership finallyがwriterをreleaseする。user blockの通常exceptionと戻り値もfinally cleanup後に変更せず伝播する。

### 4. Release helperを全stateでidempotentに保つ

`releaseReservedWriterLocked()`は`RESERVED`/`RUNNING`だけを`RELEASED`へ遷移させ、`activeWriters`を1回だけ減らす。`cleanupWriter()`が複数pathから呼ばれても`RELEASED`はno-opとする。

`releaseActiveSuspensionLocked()`は`ACTIVE`だけをreleaseし、`RELEASED`はno-opとする。queued cancellationはqueue除去だけを行う。

必須invariant:

- `activeWriters`は`RESERVED`または`RUNNING` waiter数と一致する。
- `suspensionActive=true`ならACTIVE waiterが1件だけ存在する。
- cancel済みQUEUED waiterはqueueに残らない。
- token解放またはqueue除去でgateがidleになった場合は`advanceQueueLocked()`を呼ぶ。

### 5. Lock contentionをdeterministicに作るtest seamを最小化する

既存`UnconfinedTestDispatcher`だけでは`Mutex.lock()`のcancellable suspension pathを安定して再現できない。`DatabaseWriteGate`へJVM test専用のinternal seamを追加し、testが`stateLock`を明示的に保持・解放できるようにする。seamはproduction APIへ公開せず、state mutationは行わない。

また`RESERVED` signal受信直後をdeterministicに停止できない場合は、internal no-op hookをsignal await後・transition前に置く。testはhookでbarrier待機し、writerをcancelしてからbarrierを解放する。hookはownership `try/finally`内に置き、production defaultは即returnとする。

test seam追加時もpublic constructor/APIを変更せず、Hilt injectionを維持する。

## Implementation Contract

1. `DatabaseWriteGate.kt`へ`NonCancellable`、`withContext`、`currentCoroutineContext`、`ensureActive`の必要importを追加する。
2. `withNonCancellableStateLock()`はprivate、non-suspend action型とし、KDocでcleanup専用境界を説明する。
3. `withWritePermit()`はwaiter取得後のqueue await、reserved transition、`ensureActive()`、user blockを単一`try/finally`で囲む。
4. `withWritePermit()` finallyは`cleanupWriter()`を必ず1回呼ぶ。queue await専用catchでcleanupを重複させない。
5. `cleanupWriter()`自身が`withNonCancellableStateLock()`を使用し、QUEUED/RESERVED/RUNNING/RELEASEDをidempotentに処理する。
6. `RESERVED -> RUNNING`は`withNonCancellableStateLock()`内で行い、直後にoriginal contextで`ensureActive()`を呼ぶ。
7. `withWritesSuspended()`はwaiter取得後のqueue awaitとuser blockを単一`try/finally`で囲み、finallyから`cleanupSuspension()`を1回呼ぶ。
8. `cleanupSuspension()`自身が`withNonCancellableStateLock()`を使用し、QUEUED/ACTIVE/RELEASEDをidempotentに処理する。
9. `NonCancellable`範囲内でuser block、signal await、DB I/O、delay、外部callbackを実行しない。
10. cancellationを結果値へ変換または握りつぶさず、cleanup後に元の`CancellationException`を伝播する。
11. test seamはinternalかつdefault no-opとし、production gate state/fairnessへ影響させない。
12. 新規非自明helper/test seamにはKDoc、30行を超えるpublic gate関数にはsection headerを維持する。

## Error Cases

- queued writer cancel: queueから除去し、後続waiterを進めて`CancellationException`を伝播する。
- RESERVED writer cancel: `activeWriters`予約を1回解放し、後続suspensionを進める。
- running writer block cancel/exception: writer tokenを解放して元throwableを伝播する。
- queued suspension cancel: queueから除去し、FIFO上の後続waiterを進める。
- active suspension cancel/exception: `suspensionActive=false`へ戻し、後続writer/suspensionを進める。
- cleanup時に`stateLock`競合: cancel済みcontextでもlock解放まで待ち、state mutationを完了する。
- cleanupの重複呼び出し: RELEASED state guardでcountを二重減算しない。

## Compatibility

- `withWritePermit()`と`withWritesSuspended()`のsignature、戻り値、例外契約を変更しない。
- FIFO queue、連続writer group予約、suspension exclusivityを変更しない。
- Hilt `@Singleton` / `@Inject constructor`を維持する。
- Repository/DataSource call site変更を要求しない。
- 新dependencyを追加しない。

## Testing Strategy

- queued writer cancel中にtest seamで`stateLock`を競合させ、cancel jobがlock解放後に完了し、後続suspensionがtimeout内に開始することを検証する。
- running writer block cancel中にlockを競合させ、cleanup完了後に後続suspensionが開始することを検証する。
- queued suspension cancel中にlockを競合させ、後続writerのFIFO順序とprogressを検証する。
- active suspension block cancel中にlockを競合させ、後続writerがtimeout内に開始することを検証する。
- writerをsignal受信後・reserved transition前のbarrierで停止しcancelする。barrier解放後にwriter blockが実行されず、後続suspensionが開始することを検証する。
- blockが通常値を返すcase、通常exceptionを投げるcase、複数writer batching、suspension FIFOの既存testsを回帰実行する。
- 各cancellation testは最終的にfresh writerとfresh suspensionの両方を実行し、gate全体が再利用可能であることを確認する。
- probabilistic stress loopだけに依存せず、barrierとtimeoutで各windowをdeterministicに固定する。
- Android CIで全unit testsとCI APK buildを実行し、記録HEADとworkflow `headSha`を一致させる。

## Risks / Trade-offs

- [cancel済みcoroutineがstateLock解放まで待つ] → lock内は同期的state mutationだけに限定し、待機時間を短く保つ。
- [NonCancellable範囲を広げると不要なDB処理が続行する] → private helperへ集約し、non-suspend state actionだけを受ける。
- [単一finallyへの変更でdouble releaseが起きる] → RELEASED guardとstate別cleanup testsでexactly-onceを固定する。
- [test seamがproduction behaviorへ影響する] → internal/default no-opとし、state mutationをseamへ委譲しない。
- [ensureActiveで従来より早くcancelを観測する] → signal後にcancel済みwriterがuser DB workを開始しないための意図した安全化とする。

## Migration Plan

1. lock contention/reserved transition用test seamを追加する。
2. 現行実装でleakを再現するregression testsを追加する。
3. non-cancellable state-lock helperを追加する。
4. writerを単一ownership `try/finally`へ変更する。
5. suspensionを単一ownership `try/finally`へ変更する。
6. cancellation、FIFO、batching、exception、return value testsを回帰実行する。

runtime data migrationは不要である。問題発生時は変更commitをrevertできるが、cancellation cleanupを通常contextへ戻す部分rollbackは行わない。

## Open Questions

なし。test seamの具体形は既存`DatabaseWriteGateTest.kt`のtest utility構造に合わせ、public APIを増やさない形で実装する。
