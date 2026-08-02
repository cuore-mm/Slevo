## Context

`ThreadScaffold` の gesture close は `rememberCoroutineScope()` で `TabSessionStore.closeThreadTab(...)` を起動する。`ThreadTabsCoordinator` は Delete intent を FIFO channel へ送り、保留 Delete を `openThreadTabs` に投影してから repository 書き込みと Room Flow の正規確認を待つ。最後のタブでは保留投影が空になるため `BbsRouteScaffold` の既存 `onEmptyTabs` が `navigateUp()` を実行し、Composition を離れた `rememberCoroutineScope` がキャンセルされる。`completion.await()` のキャンセルは intent の処理 Job へ連結されているため、待機中の repository 書き込みもキャンセルされ、削除がロールバックされ得る。

`TabSessionStore` は `@ActivityRetainedScoped` で、内部 `scope` は構成変更や個別画面の Composition より長く存続し、`close()` でのみ終了する。既存の deep link 登録は caller cancellation を coordinator intent に伝播する必要があるため、coordinator 全体の cancellation 契約は変更できない。

## Goals / Non-Goals

**Goals:**

- `ThreadScaffold` でユーザーが確定した close の所有権を、空タブ投影が画面遷移を起こす前に `TabSessionStore` の retained scope へ移す。
- retained scope が存続する限り、最後のタブの Delete intent、repository 書き込み、Room 正規確認、選択 key の後処理を完了させる。
- DB 正本、保留投影、単一 FIFO worker、および deep link の caller cancellation を維持する。
- repository 書き込みを決定的に停止した状態で画面側 scope の終了を再現し、削除継続を検証する。

**Non-Goals:**

- 新しい UI、文言、確認ダイアログ、ローディング表示、アクセシビリティ挙動を追加しない。
- `BbsRouteScaffold` の空タブ判定や `navigateUp()` のタイミングを変更しない。
- `ThreadTabsCoordinator` の intent cancellation、FIFO、投影、正規確認ロジックを変更しない。
- deep link の ensure/select、板タブ close、または他の Codex finding を変更しない。
- `TabScreenContent` など、画面離脱による当該 race を起こさない別の close 呼び出し元を変更しない。

## Decisions

### 1. 確定 close 専用の非 suspend 受付 API を `TabSessionStore` に追加する

`TabSessionStore.kt` に、`ThreadScaffold` が thread key と board URL を渡す非 suspend API `requestCloseThreadTab(threadKey: String, boardUrl: String)` を追加する。この関数は既存の内部 `scope.launch` で既存 `closeThreadTab(threadKey, boardUrl)` を呼び出す。既存 suspend API と `ThreadTabsCoordinator.closeThreadTab` は変更しない。

これにより、受付 API が戻る時点で close coroutine の親は Composition ではなく retained store になる。保留 Delete が `openThreadTabs` を空にできるのは、その retained coroutine が coordinator へ到達した後であるため、続いて `navigateUp()` が Composition を破棄しても intent completion はキャンセルされない。

代替として `NonCancellable` を Composition coroutine に適用する案は、画面寿命と処理寿命の関係を不明瞭にし、store 破棄時にも処理を残す危険があるため採用しない。coordinator の Delete intent だけ caller cancellation を無視する案は cancellation policy を共有 worker 内に持ち込み、deep link を含む既存契約を広く変更するため採用しない。DB commit 後まで navigation を遅延する案も、追加の UI 状態と callback 順序を導入せず所有権移譲だけで不具合を解消できるため採用しない。

### 2. `ThreadScaffold` の close gesture だけを retained 受付 API に切り替える

`ThreadScaffold.kt` の `onCloseTab` は既存の key/URL 非空 guard を維持し、`rememberCoroutineScope().launch` を削除して `tabSessionStore.requestCloseThreadTab(...)` を直接呼ぶ。ファイル先頭の outer `coroutineScope` 変数はこの close 専用なので削除する。`optionalSheetContent` 内の別の `rememberCoroutineScope` は変更しない。

既存の `openThreadTabs` 保留投影と `onEmptyTabs = { navController.navigateUp() }` はそのままとする。したがってユーザーに見える close と空タブ遷移は変わらず、遷移時点で削除処理の所有者だけが変わる。

### 3. store 終了は正当な cancellation 境界として維持する

`TabSessionStore.close()` が内部 scope をキャンセルした場合は、未完了 close も既存どおりキャンセルされてよい。Activity retained component 自体が破棄された後まで書き込みを孤立させない。repository 例外時は coordinator の既存処理により保留投影を除去し、正規状態へ戻す。新しい retry、補償 delete、例外 UI は追加しない。

## Implementation Contract

1. `TabSessionStore.kt` に `requestCloseThreadTab(threadKey: String, boardUrl: String)` を追加し、関数本体ではクラス既存の `scope.launch { closeThreadTab(threadKey, boardUrl) }` のみを所有権移譲に使用する。`GlobalScope`、新規独立 scope、`NonCancellable` は使用しない。
2. 新 API には repository 書き込みと Room 確認を retained scope で完了させる責務を説明する KDoc を、annotation より上に追加する。
3. `ThreadScaffold.kt` の `onCloseTab` は既存 guard 内で新 API を同期的に呼び、Composition scope から `closeThreadTab` を launch しない。空タブ navigation と表示は変更しない。
4. `ThreadTabsCoordinator.kt`、`DeepLinkHandler.kt`、repository/DAO、projection、intent 型には変更を加えない。特に `ensureThreadTab` の caller cancellation は維持する。
5. `TabScreenContent.kt`、`OpenThreadsList.kt`、板タブ経路、他 finding は変更しない。
6. 既存の日本語 KDoc、非自明関数コメント、Kotlin formatting 規約を満たす。

## Error Cases / Compatibility

- 不正な board URL は既存 `closeThreadTab(threadKey, boardUrl)` と同じく holder 解決を省略し、coordinator の既存 no-op 解決へ委譲する。
- repository 書き込み失敗時は既存 coordinator が pending Delete を外し、Room 正本を再投影する。画面遷移後に新規エラー UIは出さない。
- store の `close()` と競合した request は retained lifecycle 終了としてキャンセル可能であり、Composition 離脱とは区別する。
- 永続データ形式、Room schema、公開 navigation route に変更はなく migration は不要である。

## Testing Strategy

- `TabSessionStoreTest.kt` に実 `ThreadTabsCoordinator` と mock `TabsRepository`、制御可能な Room snapshot Flow、`CompletableDeferred` の write barrier を組み合わせたテストを追加する。
- 初期正規 snapshot に最後の 1 タブを流し、新 API で close を要求する。repository delete 開始後に barrier で停止し、要求元を表す短命 scope/Job をキャンセルしても delete coroutine がキャンセルされず、保留投影が空のまま処理が存続することを確認する。
- barrier 解放後に空の正規 snapshot を流し、repository delete が正常完了し、`openThreadTabs` が空、`selectedThreadTabKey` が null、同じ delete が 1 回だけ呼ばれたことを確認する。実時間 sleep や timeout 頼みにはせず、`runTest`、test dispatcher、`runCurrent`/`advanceUntilIdle`、Deferred barrier を使う。
- 既存 `DeepLinkHandlerTest.threadDeepLink_cancellationStopsBeforeSelection` と coordinator cancellation tests を回帰確認し、caller cancellation 契約が維持されることを確認する。

## Risks / Trade-offs

- [画面遷移後に repository 失敗すると、次回表示時にタブが正規 DB から再出現する] → 既存の失敗時ロールバック契約を維持し、この isolated correction では retry/UI を追加しない。
- [retained scope への移譲で close 完了を UI が直接 await できない] → 当該 UI は完了値を使わず、保留投影を描画するため影響しない。suspend API は他の呼び出し元向けに残す。
- [テストが mock coordinator だけでは DB write cancellation を証明できない] → 実 coordinator と遅延 mock repository を使って repository 呼び出しの存続と正規確認まで検証する。

## Migration Plan

Room/data migration は不要。新 API と `ThreadScaffold` 呼び出し変更を同時に適用する。問題があれば両変更を戻すことで従来の caller-owned close に戻せる。

## Open Questions

なし。
