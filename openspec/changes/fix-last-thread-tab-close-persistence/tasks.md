## 1. Retained close 受付

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt` に `requestCloseThreadTab(threadKey: String, boardUrl: String)` を追加し、既存の store `scope.launch` から既存 suspend `closeThreadTab` を呼ぶ。`GlobalScope`、新規 scope、`NonCancellable` を使わず、repository/Room 確認まで store lifetime が所有することを KDoc で明記する。
- [x] 1.2 `app/src/main/java/com/websarva/wings/android/slevo/ui/thread/screen/ThreadScaffold.kt` の gesture `onCloseTab` を、既存の非空 guard 内から `requestCloseThreadTab` を直接呼ぶ形へ変更する。close 専用の outer `rememberCoroutineScope` 変数を削除し、`onEmptyTabs`、nested sheet scope、UI 表示に差分がないことを diff で確認する。

## 2. 決定的な回帰テスト

- [x] 2.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/TabSessionStoreTest.kt` に、実 `ThreadTabsCoordinator`、mock `TabsRepository`、制御可能な snapshot Flow、`CompletableDeferred` write barrier を使う last-tab fixture を追加する。初期 snapshot は 1 タブ、delete 成功後の snapshot は空とし、実時間待機を使わない。
- [x] 2.2 同テストファイルに、短命な要求元 Job から `requestCloseThreadTab` を呼び、repository delete が barrier で待機して保留投影が空になった後に要求元 Job をキャンセルしても delete がキャンセルされない回帰テストを追加する。barrier 解放と空 snapshot 通知後、delete が 1 回だけ正常完了し、`openThreadTabs` が空、`selectedThreadTabKey` が null であることを検証する。
- [x] 2.3 `TabSessionStore.close()` を未完了 retained close の正当な cancellation 境界として検証する必要が既存テストで不足する場合だけ、同じ barrier fixture で store close 後に repository 待機がキャンセルされるテストを追加する。既存 `close_disposesAllHoldersAndCancelsScope` で契約を十分に検証できる場合は重複テストを追加せず、その根拠を実装記録に残す。

## 3. 回帰確認

- [x] 3.1 `./gradlew testDebugUnitTest --tests '*TabSessionStoreTest' --tests '*ThreadTabsCoordinatorTest' --tests '*DeepLinkHandlerTest'` を実行し、新規 delayed-write ケース、FIFO/cancellation ケース、deep link caller cancellation がすべて成功することを確認する。
- [x] 3.2 `./gradlew testDebugUnitTest` を実行し、全 unit test が成功することを確認する。
- [x] 3.3 `./gradlew assembleDebug` を実行し、debug build が成功することを確認する。
- [x] 3.4 `git diff` で変更が `TabSessionStore.kt`、`ThreadScaffold.kt`、`TabSessionStoreTest.kt` とこの OpenSpec の範囲に限定され、`ThreadTabsCoordinator.kt`、deep link、repository/DAO、projection、他 UI、他 finding に差分がないことを確認する。
