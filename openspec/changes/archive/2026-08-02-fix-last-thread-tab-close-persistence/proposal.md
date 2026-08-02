## Why

最後のスレッドタブを閉じると、削除の保留投影によって空タブ遷移が先に発生し、画面 Composition に属する coroutine がキャンセルされる。DB 書き込みが待機中の場合はこのキャンセルが削除 intent に伝播してロールバックされるため、確認済みの閉じる操作を画面寿命から分離する必要がある。

## What Changes

- ユーザーが確定したスレッドタブの閉じる要求を `TabSessionStore` の retained lifetime に移譲し、画面遷移後も FIFO 削除処理を継続する。
- 空タブ遷移は既存の保留投影に従って維持するが、投影が空になる時点では削除要求が retained scope に所有されていることを保証する。
- deep link の登録・選択など、呼び出し元が放棄した処理に対する既存の caller cancellation 契約は変更しない。
- 遅延した repository 書き込みと画面側 scope のキャンセルを再現する決定的な回帰テストを追加する。

## Capabilities

### New Capabilities

- `last-thread-tab-close-persistence`: 最後のスレッドタブを閉じた後の画面遷移と削除処理の所有権・永続化契約を定義する。

### Modified Capabilities

なし。

## Impact

- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt`
- `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/TabSessionStoreTest.kt`
- 必要に応じて既存の coordinator 回帰テスト。Room 正本、保留投影、FIFO intent queue、deep link の cancellation 契約、および UI 表示は変更しない。

## 後続統合変更との関係

`refactor-tab-controller-state-machine` は retained close ownership、Composition 破棄後の継続、`TabSessionStore.close()` の lifetime cancellation 境界を継承する。本 change の要件と回帰テストは supersede せず、そのまま統合 Controller の受入条件として参照する。
