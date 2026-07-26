## 1. Characterization と test fixture

- [x] 1.1 `BoardTabsCoordinatorTest.kt`、`ThreadTabsCoordinatorTest.kt`、`TabSessionStoreTest.kt`、`DeepLinkHandlerTest.kt` の既存 regression test を要件 matrix（loading/empty、selection repair、metadata、close、Deep Link、cancellation）へ対応付け、削除・期待値弱化がないことを checklist で確認する。
- [ ] 1.2 `BoardTabsCoordinatorTest.kt` に controlled repository failure を追加し、Board command と `registerAndConfirmBoardRoute` 相当処理が terminal failure で完了し navigation しない failing test を作る。
- [ ] 1.3 Board／Thread Controller test fixture に `StandardTestDispatcher`、replay 付き `MutableSharedFlow`、repository write barrier、call recorder を揃え、write と canonical emission を別々に進められることを fixture test で確認する。
- [ ] 1.4 Controller test に command 受理前 cancellation、受理後 caller cancellation、store teardown の三ケースを追加し、pending／repository call／terminal result の期待値を固定する。
- [ ] 1.5 Thread test に command A の matching Flow を止めたまま command B の DB write が開始し、B が A を含む effective state から導出される failing test を追加する。
- [ ] 1.6 Board／Thread test に 1,252 canonical tabs と 100 rapid commands の fixture を追加し、unique key、stable order、targeted call count、full replacement 0 回を wall-clock timeout なしで検証する。

## 2. Shared pure state/reducer contract

- [x] 2.1 `ui/tabs` の既存 `TabPresentationState`、selection resolution、`ThreadTabsProjection.kt` の定義／call site を確認し、重複型を作らない配置を決定して日本語 KDoc を付けた command id/lifecycle/result 型を追加する。
- [x] 2.2 load phase、canonical tabs、pending commands、selected key、presentation、command results を保持する immutable state contract と reducer event/effect contract を追加し、type invariant の pure unit test を通す。
- [x] 2.3 canonical + ordered pending から effective tabs を一回の indexed fold で作る shared primitive を実装し、ensure/delete/pin/update、部分確認、stale snapshot の table-driven unit test を通す。
- [x] 2.4 existing selection/close rules を pure reducer primitive に接続し、Loading、loaded-empty、valid、PendingMissing、selected close adjacent/last、invalid first repair、restore の unit test を通す。
- [x] 2.5 Thread placeholder metadata と Board resolved metadata の merge rule を repository/projection/matcher から再利用できる pure function に集約し、identity、単調増加値、sort/pin/scroll 保持の unit test を通す。
- [x] 2.6 deterministic call-count test で 1,252 tabs + 100 pending の reconciliation が pending ごとの full-list replay を行わないことを確認する。

## 3. Board targeted persistence

- [x] 3.1 `OpenBoardTabDao.kt` に single-row ensure/upsert、delete、pin、metadata/scroll の必要な targeted query/update を追加し、既存 schema のまま row count/result を返すことを Room test で確認する。
- [x] 3.2 `TabsRepository.kt` に Board targeted suspend command と explicit success/no-op/failure result を追加し、各 write が `DatabaseWriteGate` と必要な transaction を一度だけ通ることを repository interaction test で確認する。
- [x] 3.3 Board ensure が既存 sort/pin/scroll/resolved metadata を placeholder input で上書きしないよう shared merge rule を適用し、1,252 行中の他行が field 単位で不変な Room test を通す。
- [x] 3.4 Board normal add/close/pin/info/scroll call site から `saveOpenBoardTabs` と `upsertAll + deleteNotIn` を除去し、明示 bulk/restore 以外の呼出しが検索／mock verification で 0 件であることを確認する。

## 4. Board Controller migration

- [x] 4.1 `BoardTabsCoordinator.kt` を単一 immutable Controller state と Room canonical event reducer へ移行し、旧 `_openBoardTabs`、独立 selected/pending mutable source を互換 Flow の正本にしないことを state test で確認する。
- [x] 4.2 Board command acceptance、ordered pending projection、targeted repository effect、canonical matcher、terminal result を接続し、failure 後に canonical + 残存 pending へ戻って後続 command が進む test を通す。
- [ ] 4.3 Board close の session cleanup と adjacent/first/empty repair を command result/reducer event に結び、非選択 close、選択 close、last close、Composition 破棄の既存 tests を通す。
- [x] 4.4 `TabSessionStore.kt` の Board API を Controller state/result の透過委譲へ変更し、Store 内の list mutation、repository call、presentation `first { Selected }` success inference が検索と delegation test で 0 件であることを確認する。
- [x] 4.5 `DeepLinkHandler.kt` の Board path を explicit command result→selection result→navigation の順に変更し、success、repository failure、caller cancellation、既存 selection 保持の tests を通す。
- [ ] 4.6 Board parity suite が通った後だけ fire-and-forget `saveBoardTabs` normal path と重複 Board mutable state を削除し、bulk API と restore behavior が残ることを確認する。

## 5. Thread repository result alignment

- [x] 5.1 `TabsRepository.kt` の Thread ensure/delete/pin/info/scroll targeted API を success/no-op/failure が判別できる result 契約へ揃え、既存 `DatabaseWriteGate`／transaction／GC 契約を Room test で維持する。
- [x] 5.2 Thread ensure repository、pending projection、canonical matcher が同じ metadata merge function を使うよう変更し、unrelated revision と matching metadata revision の既存 tests を通す。
- [ ] 5.3 Thread normal operation から full replacement API が呼ばれず、1,252 rows の対象外 sort/pin/scroll/ThreadState が不変であることを instrumented test で確認する。

## 6. Thread Controller state consolidation

- [x] 6.1 `ThreadTabsCoordinator.kt` の canonical、pending、selected、presentation、result を単一 immutable state/reducer へ移し、互換 Flow は同じ state から派生することを test で確認する。
- [x] 6.2 command acceptance order と effective state から ensure/delete/pin/info payload を導出し、rapid add/delete と 2/3 回 pin toggle の既存 tests を通す。
- [x] 6.3 repository effect runner と canonical reconciliation を分離し、command A の confirmation 停止中に command B の write が進む 1.5 の test を通す。
- [ ] 6.4 Room snapshot ごとに operation-specific matcher で部分確認し、stale/unrelated emission では pending/result を維持し、matching emission で各 terminal result を一度だけ返す tests を通す。
- [x] 6.5 accepted command の caller cancellation link を execution から除去し、受理後 cancellation でも mutation/reconciliation が継続し、caller navigation だけが停止する tests を通す。
- [x] 6.6 `TabSessionStore.close()` を唯一の Controller execution cancellation boundary とし、未完 waiter、effect runner、Room collector、session holder disposal の teardown test を通す。
- [ ] 6.7 Thread close と `requestCloseThreadTab` の retained ownership、target session/runtime cleanup、last-tab Empty、tab-list callback の既存 tests を変更せず通す。

## 7. Thread Deep Link と Store facade

- [x] 7.1 `TabSessionStore.kt` の Thread ensure/select/close/info API を Controller command/result の委譲へ揃え、Store が readiness/presentation/canonical 観測から成功を推論しないことを delegation test で確認する。
- [x] 7.2 `DeepLinkHandler.kt` の Thread path を explicit ensure/selection result に接続し、registration 重複禁止、success→navigate、failure/no-op→非遷移、caller cancellation の既存 tests を通す。
- [ ] 7.3 Board／Thread presentation が `BbsRouteScaffold` の既存 UI 契約を維持することを `BbsRouteScaffoldSelectionTest.kt` と `BbsRouteScaffoldTest.kt` で確認し、新規 text/icon/layout/semantics がないことを diff review する。

## 8. 旧 machinery の段階削除

- [x] 8.1 1〜7 の parity tests 完了後、`ThreadTabsCoordinator.kt` の matching Flow を後続 command barrier にする FIFO worker/revision wait を削除し、repository write order と acceptance order の tests が通ることを確認する。
- [x] 8.2 parity tests 完了後、`PinWritePhase` と repository return 後の ownership/cancellation phase machinery を削除し、commit 後 caller cancellation と rapid toggle tests が新 contract で通ることを確認する。
- [x] 8.3 `canonicalRevisionFlow`、旧 completion/cancellation callback、重複 mutable canonical/open lists、独立 selection/presentation source の未使用定義を削除し、検索で旧 success inference と fire-and-forget persistence が 0 件であることを確認する。
- [x] 8.4 `saveOpenBoardTabs` と Thread bulk replacement は明示 bulk/restore use case だけに visibility/name を限定し、通常 production call graph と unit mocks から到達しないことを確認する。

## 9. 統合 verification

- [ ] 9.1 deterministic matrix として Board/Thread × ensure/delete/pin/info × success/no-op/failure × stale/matching Flow × caller active/cancelled × non-empty/last-tab を実行し、各 scenario の terminal result、projection、selection を確認する。
- [ ] 9.2 `./gradlew testDebugUnitTest` を実行し、既存五 active change の regression tests と新 reducer/controller tests が全て成功することを確認する。
- [ ] 9.3 `./gradlew assembleDebug` を実行し、production/test call site の source API 移行漏れがないことを確認する。
- [ ] 9.4 接続済み emulator/device で `TabsRepositoryThreadStateTest`、新 Board targeted Room test、`BbsRouteScaffoldTest` を実行し、Room transaction/Flow と Compose 表示回帰がないことを確認する。
- [x] 9.5 production diff を確認し、DB schema/resource/manifest、UI text/icon/layout/theme/accessibility、route format に変更がないこと、日本語 KDoc/comment と長関数 section header 規則を満たすことを確認する。
