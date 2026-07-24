## Context

現在の `TabScreenContent` は `rememberCoroutineScope()` を保持し、`TabsPagerContent` に渡す `onCloseThreadTab` で `coroutineScope.launch { tabSessionStore.closeThreadTab(tab) }` を実行する。最後のスレッドタブの削除によって一覧が空になり、ナビゲーションまたは `TabsBottomSheet` の破棄で `TabScreenContent` が Composition から外れると、この coroutine は repository 書き込みまたは Room の正規状態確認中にキャンセルされ得る。

`TabSessionStore` には既に `requestCloseThreadTab(threadKey: String, boardUrl: String)` があり、`@ActivityRetainedScoped` な store 自身の `CoroutineScope` で同じ `ThreadTabsCoordinator` の FIFO 削除経路を開始する。`ThreadScaffold` の close 経路はこの API を使用しており、Composition の寿命ではなく `TabSessionStore.close()` を close 処理のキャンセル境界としている。

現状は `TabScreenContent` 内に callback がインライン定義されているため、local JVM test から実際の委譲先を直接検証できない。`androidTest` には Hilt 対応の `TabScreenContent` 描画基盤がなく、Composition 破棄との競争を時刻依存で再現するテストは不安定になる。

## Goals / Non-Goals

**Goals:**

- タブ一覧の閉じるボタン、スワイプ削除、および長押しメニューから共通して到達するスレッドタブ close callback を `TabSessionStore.requestCloseThreadTab` へ委譲する。
- 最後のスレッドタブの削除開始後にタブ一覧 Composition が破棄されても、retained store の寿命内では DB 削除と Room 正規状態確認を継続する。
- callback が `ThreadTabInfo.threadKey` と `ThreadTabInfo.boardUrl` をそのまま retained API に渡すことを、local JVM unit test で決定論的に検証する。
- 既存の `ThreadTabsCoordinator` による FIFO、選択補正、メタデータ更新、repository rollback、および `TabSessionStore.close()` でのキャンセル境界を維持する。

**Non-Goals:**

- `TabSessionStore.requestCloseThreadTab`、`ThreadTabsCoordinator`、repository、Room schema の変更。
- 板タブ close、`ThreadScaffold`、削除アニメーション、画面遷移条件、表示文言、アクセシビリティ構造の変更。
- Compose の実時間競争を再現する instrumented test の追加。
- 他の close 呼び出し元または他の Codex finding の修正。

## Decisions

### 1. タブ一覧 callback は retained close API のみを呼び出す

`TabScreenContent` が `TabsPagerContent` へ渡す `onCloseThreadTab` は、`ThreadTabInfo` から `threadKey` と `boardUrl` を取得し、`TabSessionStore.requestCloseThreadTab(threadKey, boardUrl)` を同期的に一度だけ呼び出す。callback 自身は coroutine を起動せず、`closeThreadTab(ThreadTabInfo)` を呼び出さない。

これにより削除 intent は `TabSessionStore` 所有 scope に投入され、その後の UI Composition 破棄から独立する。処理本体は既存 coordinator を通るため、FIFO、canonical snapshot 確認、選択補正、および metadata cleanup の契約は変わらない。

代替案として `rememberCoroutineScope` の Job を別の Composition に移す方法は、UI の寿命に依存する問題を残すため採用しない。`GlobalScope` や新規独立 scope は所有者とキャンセル境界を増やすため採用しない。

### 2. callback builder を最小の JVM test seam として抽出する

`app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabScreenCloseCallbacks.kt` に、`TabSessionStore` を受け取り `(ThreadTabInfo) -> Unit` を返す `internal` top-level function `createThreadTabCloseHandler` を追加する。`TabScreenContent` はインライン lambda の代わりにこの関数の戻り値を `onCloseThreadTab` へ渡す。

この seam は UI parameter や callback 型を変更せず、Compose、ナビゲーション、アニメーションを描画せずに実際の配線を検証できる。実装関数には、`ThreadTabInfo` から retained API の識別子へ変換することを示す KDoc と、リポジトリのコメント規則に従う簡潔な変換説明を付ける。

代替案のインライン置換だけでは store API の unit test は可能でも `TabScreenContent` の委譲先を直接固定できないため、要求されたタブ一覧経路の回帰検証として不十分である。Hilt 対応 instrumented test 基盤の新設は変更規模が過大であり、競争再現も非決定的なため採用しない。

### 3. unit test は委譲契約だけを固定する

`app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/screen/TabScreenCloseCallbacksTest.kt` を追加し、MockK の `TabSessionStore` と有効な `ThreadTabInfo` を使用して handler を一度呼び出す。テストは次を検証する。

1. `requestCloseThreadTab(tab.threadKey, tab.boardUrl)` が exactly once で呼ばれる。
2. suspend API `closeThreadTab(tab)` は呼ばれない。
3. callback 呼び出し側に coroutine scheduler の進行、delay、Composition、ナビゲーションが不要である。

retained scope の caller cancellation 耐性と store lifetime 境界は既存の `TabSessionStoreTest` が既に検証しているため重複しない。

## Implementation Contract

1. `TabScreenCloseCallbacks.kt` を `ui.tabs.screen` package に追加し、`internal fun createThreadTabCloseHandler(tabSessionStore: TabSessionStore): (ThreadTabInfo) -> Unit` を定義する。
2. 戻り lambda は受け取った `tab` の `threadKey` と `boardUrl` を順序どおり `tabSessionStore.requestCloseThreadTab` に渡す。新規 scope、`launch`、例外握り潰し、fallback は追加しない。
3. `TabScreenContent.kt` の `TabsPagerContent(onCloseThreadTab = ...)` だけを `createThreadTabCloseHandler(tabSessionStore)` に置き換える。`rememberCoroutineScope()` は URL 入力処理でも使用されるため削除しない。
4. `TabsPagerContent`、`OpenThreadsList`、`RemovableTabList` の callback 型と削除アニメーション経路は変更しない。これによりボタン、スワイプ、長押しメニューの全経路が同じ handler を使用する。
5. `TabSessionStore` と `ThreadTabsCoordinator` の実装は変更しない。
6. `TabScreenCloseCallbacksTest.kt` に上記委譲検証を追加する。テスト対象を再実装した lambda をテスト側に作らず、必ず production の `createThreadTabCloseHandler` を呼ぶ。

## Error Cases and Compatibility

- handler 呼び出し時点で対象タブが既に coordinator の正規一覧から消えている場合は、既存 `requestCloseThreadTab` の lookup/no-op 契約に従う。UI 側で再試行や別削除を追加しない。
- 無効な `threadKey` または `boardUrl` の扱いは既存 retained API の parse/lookup 契約を変更しない。handler は値を加工しない。
- `TabSessionStore.close()` 後または Activity-retained component 破棄時のキャンセルは既存の正当な lifetime boundary として維持する。
- 公開 API、保存形式、DB migration、ユーザー向け UI、ナビゲーション条件に互換性変更はない。

## Risks / Trade-offs

- [新しい top-level helper が単純な配線のためだけに増える] → 実際の UI 配線を JVM で決定論的に固定する最小 seam とし、close 処理本体や UI state は移動しない。
- [handler が古い `ThreadTabInfo` を保持する可能性] → retained API には識別子だけを即時渡し、coordinator が正規一覧から対象を解決する既存契約を利用する。
- [Composition 破棄後も削除が継続する] → これは修正対象の意図した動作であり、キャンセル境界は `TabSessionStore.close()` に限定する既存 `ThreadScaffold` 契約と一致させる。

## Migration Plan

データ migration は不要。実装、unit test、build を同一変更で導入する。問題が発生した場合は helper と `TabScreenContent` の委譲変更を同時に revert でき、DB schema や保存データの復元作業は不要である。

## Testing Strategy

- 新規 `TabScreenCloseCallbacksTest` で retained API への exact delegation と suspend API 非呼び出しを検証する。
- 既存 `TabSessionStoreTest.requestCloseThreadTab_survivesCallerCancellationAndConfirmsCanonicalDeletion` と `close_cancelsRetainedCloseAtStoreLifetimeBoundary` を含む unit test 全体を実行し、retained lifetime とキャンセル境界の退行がないことを確認する。
- `./gradlew testDebugUnitTest` と `./gradlew assembleDebug` を実行する。

## Open Questions

なし。
