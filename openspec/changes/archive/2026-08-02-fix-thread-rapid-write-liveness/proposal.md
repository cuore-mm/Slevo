## Why

`ThreadTabsCoordinator` は同一 Thread の mutation を先行する canonical 確認より前に書き込みへ進める一方、各 waiter は自身の中間 pin 値または一時的な存在状態を待つ。Room が rapid write の中間 invalidation をまとめて最終状態だけを通知すると、その条件が二度と成立せず、optimistic pending と `togglePinThreadTab` / `ensureThreadTab` の待機が残り続ける。

## What Changes

- 同一 Thread の後続 write が成功して先行 intent と両立しない最終状態を決めた場合、先行 operation を明示的な superseded terminal 状態へ進める。
- repeated pin は最新の成功 write を authoritative intent とし、先行 pin waiter を canonical 中間値なしで解放する。
- Ensure と Delete の順序を operation-aware に扱い、Ensure→Delete は先行 Ensure を `-1`、Delete→Ensure は先行 Delete を cleanup なしの完了として解放する。
- supersession は後続 repository write の成功後にだけ確定し、後続失敗時は先行 operation の通常 confirmation / cleanup を維持する。
- Board coordinator、repository/DAO、metadata confirmation、deferred P2、UI は変更しない。

## Capabilities

### New Capabilities

- `thread-rapid-mutation-liveness`: 同一 Thread の rapid pin と lifecycle mutation が Room の中間通知なしでも安全に終端し、最終 DB canonical state へ収束する契約。

### Modified Capabilities

なし。

## Impact

- Production: `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt` と、必要な最小 signal を operation に保持する場合のみ `ThreadTabsProjection.kt`。
- Tests: `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinatorTest.kt`。
- API、DB schema、DAO、Repository、`DatabaseWriteGate`、UI、migration に変更はない。
