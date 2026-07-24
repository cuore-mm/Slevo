## 1. Retained close handler

- [x] 1.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabScreenCloseCallbacks.kt` を追加し、`internal fun createThreadTabCloseHandler(tabSessionStore: TabSessionStore): (ThreadTabInfo) -> Unit` を実装する。handler が `tab.threadKey` と `tab.boardUrl` を `requestCloseThreadTab` に一度だけ渡し、coroutine を起動しないことをコードレビューで確認する。
- [x] 1.2 `createThreadTabCloseHandler` に、`ThreadTabInfo` を retained close 識別子へ変換して store に委譲する構造を説明する KDoc と必要な変換コメントを付け、リポジトリのコメント配置・内容規則を満たすことを確認する。

## 2. タブ一覧 callback の配線

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabScreenContent.kt` の `TabsPagerContent` に渡す `onCloseThreadTab` を、Composition scope の `launch { tabSessionStore.closeThreadTab(tab) }` から `createThreadTabCloseHandler(tabSessionStore)` へ置き換える。対象 callback に `launch` と suspend close 呼び出しが残っていないことを確認する。
- [x] 2.2 `TabScreenContent` の `rememberCoroutineScope()` は URL 入力処理で引き続き使用し、`TabsPagerContent`、`OpenThreadsList`、`RemovableTabList`、`TabSessionStore`、`ThreadTabsCoordinator`、板タブ close を変更していないことを diff で確認する。

## 3. 決定論的回帰テスト

- [x] 3.1 `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabScreenCloseCallbacksTest.kt` を追加し、MockK の `TabSessionStore` と有効な `ThreadTabInfo` で production の `createThreadTabCloseHandler` を呼び出す。
- [x] 3.2 handler 呼び出し後に `requestCloseThreadTab(tab.threadKey, tab.boardUrl)` が exactly once で呼ばれ、`closeThreadTab(tab)` が呼ばれないことを検証する。テストに Compose 描画、delay、実時間待機、独自 coroutine 起動を使用していないことを確認する。

## 4. 検証

- [x] 4.1 `./gradlew testDebugUnitTest` を実行し、新規 callback test、既存 retained caller-cancellation test、store lifetime cancellation test を含む unit test がすべて成功することを確認する。
- [x] 4.2 `./gradlew assembleDebug` を実行し、Debug build が成功することを確認する。
- [x] 4.3 最終 diff を確認し、削除アニメーション、ナビゲーション、表示文言、アクセシビリティ、FIFO、メタデータ、DB schema、他 finding に変更がないことを確認する。
