## Context

`refactor-tab-controller-state-machine` の実装は targeted DB mutation、Room canonical state、pending projection、atomic presentation、retained close、Deep Link の明示的な成功／失敗を実現した。一方、現行コードを再調査すると、設計時に想定した machinery の一部は既に削除済みであり、残存物は次の二群に分かれる。

1. 安全性に寄与しない未参照 state／型／API: Thread の `_controllerState` mirror、Board state の `commandResults` 履歴、`TabReducerTransition`、未使用 Thread Deep Link command API、未使用 Thread repository result wrapper。
2. 低確率順序だけを保証する照合: Thread の `pendingStateRevision`、`hasEarlierPendingOperation`、ensure/info の全 metadata field matcher。

通常 mutation は既に `TabsRepository` の targeted API だけを呼び、Room Flow が canonical list を供給する。Thread command は初回 `Loaded` 後に Controller scope で write を開始し、last-tab close は `TabSessionStore.requestCloseThreadTab` の retained scope が所有する。これらは本変更で触れない安全境界である。

### 現状の keep/remove 判断

| 対象 | 判断 | 現行箇所 | 理由／量的効果 |
|---|---|---|---|
| 同一 key predecessor confirmation | Remove | `ThreadTabsCoordinator.awaitConfirmation`、`hasEarlierPendingOperation` | confirmation の条件 1 個と専用関数／同一 key scan を削除する。rapid same-tab pin の先行確認禁止だけを失う。 |
| pending-state revision | Remove | `pendingStateRevision`、`pendingStateRevisionFlow`、`registerPending`、`removePending` | stored counter 2 個と更新 4 行、`combine` を削除する。predecessor 消滅時の再評価が不要になるため replacement は追加しない。`snapshotVersion` は post-write Flow 条件として維持する。 |
| referential operation lookup | Keep | `removePending` の `===` lookup | 同値 payload の別 command を誤って消さない最小手段であり、削除には command ID 等の代替 state が必要になる。 |
| exact metadata confirmation | Remove (Thread ensure/info only) | `isThreadTabOperationConfirmed`、`matchesThreadMetadata` | 5 field 比較 helper を削除し、identity existence に縮小する。Board は current canonical を write 直後にも照合するため exact Info/Ensure 条件を維持し、承認外の即時 rollback を避ける。Thread delete absence と pin value match も維持する。 |
| Thread duplicate state/projection | Remove mirror / Keep authoritative flows | `_controllerState`、`controllerState` と 3 update site | 未参照 mirror holder 1 個、公開 mirror Flow 1 個、3 write branch を削除する。`canonicalTabs`、`pendingOperations`、`_threadTabState`、`_openThreadTabs`、`_threadPresentationState` は実際の command/UI source なので維持する。 |
| `TabReducerTransition` | Remove | `TabControllerContracts.kt` | definition 以外の参照 0。型 1 個を削除する。 |
| duplicate Deep Link delegation | Remove unused path | `registerAndSelectThreadRouteCommand`、`ensureThreadTabCommand`、`selectThreadTabCommand` | production caller 0 の public/internal API 3 個を削除する。`DeepLinkHandler` が使う `registerThreadRoute`→canonical check→selection 経路は変更しない。 |
| Board command-result history | Remove history / Keep per-command waiter | `TabControllerState.commandResults`、`BoardTabsCoordinator.finish` | 読み手 0 で無制限増加する map holder 1 個と append branch 1 個を削除する。各 `CompletableDeferred` の terminal result は維持する。 |
| Thread repository result wrapper | Remove | `ensureOpenThreadTabResult`、`deleteOpenThreadTabResult`、`setThreadTabPinnedResult`、`updateThreadStateResult` | caller 0 の wrapper API 4 個を削除する。実際に使う targeted API は維持する。 |
| full-list bulk API | Keep | `saveOpenBoardTabs`、`replaceOpenThreadTabsForBulkOperation` | normal production caller は 0 で、明示 bulk／Room test setup の境界である。削除して test setup を別 writer に置換する利益がなく、通常 mutation への接続禁止を test で維持する。 |
| caller cancellation ownership | Keep implementation / narrow race tests | command queue、Controller scope、completion cancellation checks | 現行は専用 phase/callback machinery が既にない。受理前 side effect 防止と受理済み write 継続に必要な小さい checks は維持し、commit と caller cancel が同一 mock boundary で競合する厳密順序だけを contract/test から外す。 |

量的には production から stored state holder 4 個（Thread mirror、pending revision counter/Flow、Board result map）、mirror Flow 1 個、unused type 1 個、unused API 7 個、専用 predecessor function 1 個、metadata comparator 1 個を除去する。`ThreadTabsCoordinatorTest` は exact rapid/cancellation test 5 件を 1 件の限定 contract test へ集約し、metadata confirmation test 2 件を書き換える。

## Goals / Non-Goals

**Goals:**

- data loss、全件 overwrite、通常 last-tab close failure、Deep Link の無期限待機を再導入せず、残存 defensive state と分岐を削減する。
- Room を canonical source、pending を一時 projection、通常 write を対象行 API とする境界を維持する。
- Thread confirmation を `snapshotVersion > baselineVersion` と最小 operation 条件へ縮小する。
- 未参照 API と state を削除し、実際の production call graph を OpenSpec の契約に合わせる。
- rare rapid pin ordering と temporary metadata rollback を意図した residual risk として明記する。

**Non-Goals:**

- Board／Thread Controller の統合、新しい generic reducer、timeout、retry、compensation、debounce を追加しない。
- DB schema、DAO query、DatabaseWriteGate、backup/restore、bulk operation を変更しない。
- UI text、layout、icon、theme、accessibility、navigation、初回 loading、normal tab close、Deep Link の通常挙動を変更しない。
- rapid pin の代替 ordering mechanism や metadata rollback 防止の別 revision を追加しない。
- `refactor-tab-controller-state-machine` の未完 device task 9.4 を本変更内で完了扱いにしない。

## Decisions

### 1. Thread confirmation は post-write Flow と最小 operation 条件だけを待つ

`awaitConfirmation` は `snapshotVersionFlow.first { version > baselineVersion && isThreadTabOperationConfirmed(...) }` とする。`pendingStateRevisionFlow`、`pendingStateRevision`、`hasEarlierPendingOperation` は削除する。

最小条件は Delete=`target absent`、Pin=`target pin equals requested`、Ensure/Info=`target identity present` とする。これにより accepted add は存在確認まで、delete は不在確認まで保持されるため、stale initial Flow が新規 add/delete intent を消すことはない。Pin は単発の通常操作で値一致を待つ。同一 key の複数 pending は互いの confirmation dependency を持たない。

**代替案:** predecessor を key ごとの generation/map に置換すると state と分岐を別形態で残すため採用しない。全 operation を「任意の次 emission」で確認すると単発 pin/delete の正常契約まで弱めるため採用しない。

### 2. Metadata safety と projection continuity を分離する

metadata の永続安全性は `TabsRepository.ensureOpenThreadTab`、`updateThreadState` と `mergeThreadTabMetadata` が保持する。pending 中の表示は `projectThreadTabs` が同じ merge を適用する。一方、Ensure/Info の completion は全 metadata field の exact match を要求せず identity presence で終端できる。

その結果、write 後の無関係 Flow emission が対象 identity を含む場合、pending projection が外れて古い canonical metadata が次の emission まで一時表示され得る。これは承認済み residual risk であり、placeholder が DB の解決済み値を上書きすることとは区別する。Board matcher は write 直後の current snapshot を照合する構造なので変更しない。

### 3. 公開されない logical mirror と履歴を state contract から外す

Thread の atomic UI 契約は `_threadPresentationState` が tabs と selection resolution を一つの `TabPresentationState` で公開することで満たす。未参照 `_controllerState` を並行更新しても atomicity や安全性は増えないため削除する。load readiness は `_threadTabState`、canonical は `canonicalTabs`、pending は `pendingOperations` が引き続き各責務を持つ。

Board の `TabControllerState` は authoritative state として維持するが、terminal result の履歴 map は state に保存しない。`BoardPendingOperation.result` を一度 complete して pending から除去すれば Deep Link の明示 result 契約を満たす。

**代替案:** Thread を Board と同じ単一 state に再移行する案は、今回の目的に反して大規模な state rewrite と一時的な dual source を作るため採用しない。

### 4. 現在の production call graph にない API を削除する

Thread Deep Link は `DeepLinkHandler.handleThreadDeepLinkRoute` から `TabSessionStore.registerThreadRoute`、`isCanonicalThreadTab`、`selectThreadTab` の一経路を使う。この経路の persistence exception／negative result／selection failure は navigation failure へ明示的に伝播する。caller 0 の `registerAndSelectThreadRouteCommand` と、それだけが使う coordinator command wrappers は削除する。

`TabsRepository` の `*Result` wrapper 4 件も caller 0 なので削除する。実働する Boolean/Unit targeted methods、Board の `TabMutationResult`、bulk API は変更しない。

### 5. Test は弱める範囲と保持する invariant を分離する

`ThreadTabsCoordinatorTest.kt` の 2/3/100 rapid same-tab toggle test と commit 同時 cancellation test 2 件は、厳密な predecessor/result ordering だけを固定しているため削除する。代わりに rapid toggle が targeted write だけを発行し、必要な canonical pin 値の emission 後に有限に完了する 1 test を置き、順序や中間 projection は assertion しない。

`ensureConfirmation_requiresMergedMetadataMatch` は identity presence で confirmation する pure test に書き換える。`ensureExistingTab_waitsForMatchingMetadataAfterUnrelatedRevision` は、無関係 post-write emission で command が完了し得ること、後続 matching canonical emission で metadata が最終的に反映されることを検証する test に書き換える。

## Mandatory Invariants

1. 初回 canonical 未受信は `Loading` であり loaded-empty と区別し、mutation を開始しない。
2. 通常 add/delete/pin/info/scroll は targeted repository/DAO operation だけを使い、full-list replacement を呼ばない。
3. Room Flow だけが canonical tabs を供給し、pending projection は canonical list 自体を書き換えない。
4. placeholder input は Repository merge で既存の identity、resolved metadata、sort、pin、scroll を永続的に上書きしない。
5. last-tab close は Composition disposal 後も retained scope で対象行削除と Empty selection repair まで進む。
6. Deep Link は persistence completion と selection の成功／失敗を有限の result/exception path で判定し、失敗時に navigation しない。
7. tabs と selection resolution は同じ `TabPresentationState` emission で公開される。
8. 初回 stale Flow は accepted add を対象存在確認前に消さず、accepted delete を対象不在確認前に消さない。
9. repository failure は該当 pending を除去し、canonical + 残存 pending へ復帰して後続 command を停止させない。
10. Controller teardown は未開始 write と未完 waiter を終了し、既commit DB state は次回 Room load から回復可能にする。

## Data Flow

```text
UI / DeepLinkHandler
        │ command await（caller cancellation は navigation/wait を停止）
        ▼
TabSessionStore（delegate / retained close owner）
        ▼
BoardTabsCoordinator / ThreadTabsCoordinator
        ├─ Loaded gate
        ├─ pending projection ────────────────> atomic TabPresentationState
        ├─ targeted repository write ────────> DatabaseWriteGate ─> Room
        └─ Room Flow (canonical)
               └─ post-write version + minimal operation match
                       └─ pending removal + caller completion
```

## Affected Areas

### Production files

- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/controller/TabControllerContracts.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/BoardTabsCoordinator.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsProjection.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/store/TabSessionStore.kt`
- `app/src/main/java/com/websarva/wings/android/slevo/data/repository/TabsRepository.kt`

### Test files changed

- `app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/ThreadTabsCoordinatorTest.kt`

### Tests retained unchanged as mandatory regression coverage

- `TabControllerPrimitivesTest.kt`: Loading/Empty、pending projection、selection repair。
- `BoardTabsCoordinatorTest.kt`: targeted write、failure terminal、metadata preservation、close/selection。
- `TabSessionStoreTest.kt` と `TabScreenCloseCallbacksTest.kt`: retained last-tab close と teardown。
- `DeepLinkHandlerTest.kt`: result→selection→navigation と failure/cancellation 非遷移。
- `BbsRouteScaffoldSelectionTest.kt`、`BbsRouteScaffoldTest.kt`: atomic presentation の UI adapter。
- `TabsRepositoryThreadStateTest.kt`: targeted mutation、他行不変、placeholder metadata preservation。

## Error Cases and Recovery

- Repository failure: 現行 catch path で pending を除去し caller を失敗完了する。新しい retry／compensation は追加しない。
- Room Flow 停止: matching 条件を満たさない command waiter は retained lifetime 中 pending のままになり得るが、後続 write は進み、store teardown が waiter を終了する。新 timeout は追加しない。
- Rapid same-tab pin: 後続 pin が古い canonical 値に一致して先に確認され、中間 projection または最終表示が厳密な toggle 回数どおりにならない場合がある。後続 Room emission が DB canonical 値を再公開する。
- Unrelated metadata Flow: Ensure/Info pending が先に外れて古い metadata が一時表示される場合がある。DB merge は保持され、次の canonical emission で回復する。
- Caller cancellation と repository return の同時 race: 厳密な test ordering は保証しない。Controller scope で既に開始した write、DatabaseWriteGate、Room canonical recovery は維持する。
- Process death/rollback: schema と persisted representation は不変なので migration はない。既commit値は次回 Flow load で復元する。

## Compatibility and Migration

削除対象 API は repository 内検索で production/test caller がないものに限定するため、外部形式や runtime migration はない。bulk API は残す。internal Kotlin source API の削除は同一モジュール内 compile で検出する。

## Testing Strategy

1. `ThreadTabsCoordinatorTest` で Loading、loaded-empty、pending add/delete、single pin match、failure recovery、atomic close、pre-acceptance cancellation、Controller-owned accepted mutation を維持する。
2. rapid same-tab toggle は exact acceptance order を assertion せず、targeted write と有限 completion のみを検証する。
3. Thread metadata confirmation は identity presence で終端できることと、Repository/Projection merge test が resolved metadata を維持することを分離して検証する。
4. production call-site search で削除 API／state／typeの参照 0、通常 mutation から `saveOpenBoardTabs`／`replaceOpenThreadTabsForBulkOperation` 呼出し 0 を確認する。
5. 実装後に `./gradlew testDebugUnitTest` と `./gradlew assembleDebug` を実行する。
6. 接続済み device/emulator で `TabsRepositoryThreadStateTest` と `BbsRouteScaffoldTest` を実行する。本変更は既存 change の task 9.4 を自動的に完了扱いにせず、実行結果を各 change の task に個別記録する。

## Implementation Contract

1. 先に `ThreadTabsCoordinatorTest.kt` の exact rapid pin test 3 件と exact commit/cancel test 2 件を、限定 contract test 1 件へ置換し、metadata test 2 件を新 contract へ書き換える。mandatory regression test は削除しない。
2. `ThreadTabsCoordinator.awaitConfirmation` を `snapshotVersionFlow.first` へ変更してから、同じ commit 内で `pendingStateRevision`／`pendingStateRevisionFlow`／`hasEarlierPendingOperation`／不要な `confirmationKey` helper を削除する。旧・新 confirmation writer を併存させない。
3. `isThreadTabOperationConfirmed` の Delete/Pin 条件を維持し、Ensure/Info だけ identity existence に変更して `matchesThreadMetadata` を削除する。Repository と projection の `mergeThreadTabMetadata` は変更しない。
4. Thread の `_controllerState`／`controllerState` と `setThreadTabState`／`publishThreadPresentation` 内の mirror update 3 箇所を一括削除する。`_threadPresentationState` を置換・二重化しない。
5. `TabControllerState.commandResults` と `BoardTabsCoordinator.finish` の map append を同時に削除する。`pending.result.complete(result)` を先に消してはならない。`TabReducerTransition` は参照 0 を確認して削除する。
6. `TabSessionStore.registerAndSelectThreadRouteCommand`、`ThreadTabsCoordinator.ensureThreadTabCommand`／`selectThreadTabCommand` を一括削除し、active `DeepLinkHandler` 経路を変更しない。続けて `TabsRepository` の未使用 `*Result` wrapper 4 件だけを削除する。
7. bulk API、targeted API、DAO、DatabaseWriteGate は変更しない。同一 domain に新 writer、full-list fallback、compensating write を追加しないため、どの stage にも dual writer は存在しない。
8. 各削除後に call-site search を行い、残存参照を新 adapter で埋めず、真の production caller が見つかった場合はその削除を中止して OpenSpec を再評価する。
9. repository の日本語 KDoc/comment 規則を満たし、build/unit test/device test を Testing Strategy の順で実行する。

## Migration Plan

1. Test contract を rare ordering から mandatory invariant へ先に切り替える。
2. Thread confirmation の guard/revision/matcher を一体で簡素化する。
3. Thread mirror state を一体で削除し、atomic presentation の既存 source は維持する。
4. Board result historyと vestigial shared type を削除する。
5. 未使用 Store/Coordinator/Repository API を call graph の外側から削除する。
6. unit/build/device verification と source search を完了する。

rollback は schema migration がないため変更 commit の通常 revert で可能である。ただし途中 rollback でも dual writer を作らないよう、confirmation 一式と Thread mirror 一式はそれぞれ同一 commit/stage 単位で戻す。

## Open Questions

なし。承認済み residual risk を超える normal-path pin、close、Deep Link、UI の変化が見つかった場合は実装を進めず product/UI 判断へ戻す。
