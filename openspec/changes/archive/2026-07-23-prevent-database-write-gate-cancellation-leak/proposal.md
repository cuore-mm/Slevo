## Why

`DatabaseWriteGate`のwriter/suspension cleanupはcancel済みcoroutine contextで`stateLock.withLock`を待つため、lock競合時にcleanup自体がcancelされ、`activeWriters`、`suspensionActive`、queue waiterが残留できる。またsignal受信後の`RESERVED -> RUNNING`遷移もcleanup境界外にあり、同じreservation leakを起こせる。

残留stateはqueueを永久停止させ、以降のDB writeとbackup用write suspensionを進行不能にするため、全必須state transitionをcancel後も完了する契約が必要である。

## What Changes

- writer/suspensionのreleaseおよびqueued waiter cleanupで、`stateLock`取得からstate更新完了までを`NonCancellable`境界で保護する。
- queued writerのsignal受信後から`RESERVED -> RUNNING`遷移、またはcancel時releaseまでを単一の保証されたownership境界で管理する。
- `CancellationException`をcleanup後に再throwし、user blockのcancel semanticsを変更しない。
- `NonCancellable`範囲を短いgate state mutationだけに限定し、user block、DB I/O、queue待機を含めない。
- lock競合下でwriter/suspensionをcancelしても、後続writer/suspensionが進行するdeterministic regression testsを追加する。

## Capabilities

### New Capabilities

- `database-write-gate-cancellation-safety`: `DatabaseWriteGate`のwriter/suspension lifecycleがcancellation下でもreservation、active state、queue entryをリークしない要件を規定する。

### Modified Capabilities

なし。既存database write gate変更はまだmain specsへarchiveされていないため、本changeでは独立したcancellation safety capabilityとして追加する。

## Impact

- `DatabaseWriteGate.kt`: writer/suspension cleanup、release、reserved transition、共通state-lock helper。
- `DatabaseWriteGateTest.kt`: lock競合下のqueued/active cancellationと後続progress tests。
- public API、DB schema、Room transaction、backup format、UI、dependencyは変更しない。
