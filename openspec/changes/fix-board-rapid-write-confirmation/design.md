## Context

`BoardTabsCoordinator.register()` は全 command を `pendingCommands` に追加し、`acceptWithoutWaiting()` は各 command の `execute()` を別 coroutine で起動する。Repository の targeted write は `DatabaseWriteGate` で直列化されるが、Room 2.7.0 の observable query は write ごとの中間値通知を保証しない。現在の `reconcileCanonical()` / `isConfirmed()` は command ごとの期待値との一致を待つため、同一 Board に `Scroll(A)`, `Scroll(B)`, `Scroll(C)` が commit されて Room が `C` だけを通知すると、`A` と `B` が永続的に残る。

残った command は `effectiveTabs()` で受理順に projection される。最新 command が canonical confirmation で除去された後に古い command の projection が再び有効になるため、表示が stale になり、200ms scroll 保存の継続中は pending 数も増え続ける。

対象は `BoardTabsCoordinator` の Board 更新 command のみである。DB schema、`OpenBoardTabDao`、`TabsRepository`、`DatabaseWriteGate`、UI、Thread coordinator は変更しない。

## Goals / Non-Goals

**Goals:**

- 同一 `boardUrl`・同一更新種別の rapid `Scroll`、`Pin`、`Info` を最新 intent へ収束させる。
- Room が中間 canonical 値を通知しなくても、当該 key の pending projection を常に最大 1 件に保つ。
- supersede された command の waiter を terminal にし、失敗や遅延完了で stale projection を復活させない。
- targeted write、DB canonical state、atomic `TabPresentationState`、異なる Board の独立性を維持する。

**Non-Goals:**

- `Ensure`、`Delete`、close の隣接選択、Deep Link の明示的 terminal result を変更しない。
- 全 command を Flow confirmation 待ちで直列化しない。
- full-list persistence、DAO/Repository API、DB migration を追加しない。
- Thread P1 または deferred P2 finding を分析・修正しない。
- 同一 Board の異なる更新種別を相互に supersede しない。

## Decisions

### 1. Board と更新種別の組を supersession key にする

`BoardTabsCoordinator.kt` 内で `Scroll(boardUrl)`, `Pin(boardUrl)`, `Info(tab.boardUrl)` だけに内部 supersession key を与える。key は `boardUrl` と operation kind の組であり、異なる Board、または同一 Board の異なる kind は独立した pending command とする。`Ensure` と `Delete` は一意の lifecycle と selection/Deep Link 契約を持つため対象外とする。

単一 Board key だけでまとめる案は、scroll と pin のような独立した targeted write を不要に取り消すため採用しない。全 Board command を repository write 完了または Flow confirmation まで直列化する案は、既に除去した broad/global blocking を復活させるため採用しない。

### 2. register 時に同一 key の先行 command を terminal `NoOp` にする

`register()` は新 command を追加する同じ `_state.update` 内で、同一 supersession key の先行 pending command を除去する。これにより presentation は一度の atomic state 更新で旧 projection から最新 projection へ切り替わり、中間的に canonical へ戻らない。state 更新後、除去した各 command の `CompletableDeferred` を `TabCommandResult.NoOp` で完了する。

`NoOp` は「先行 intent は後続 intent により意図的に obsolete になり、個別 canonical confirmation を待たない」という terminal result を表す。supersede 時点で先行 write が既に開始済みでも waiter は待ち続けない。先行 write の後発 success/failure は既に terminal になった result を上書きせず、pending/projection を復活させない。

### 3. supersede 済み command は repository 呼び出し前に skip する

`execute()` は `loadPhase == Loaded` を待った後、repository method を呼ぶ直前に command ID が現在の `pendingCommands` に残っているか確認する。supersede 済みなら write を行わず return する。先行 write が既に repository に入っている場合は cancel しない。その write は後続 command より先に `DatabaseWriteGate` に到着済みであり、後続の最新 write がその後に適用される。

この pre-write membership check により、ロード待ちまたは未スケジュールの stale write が最新 write の後から DB を上書きすることを防ぐ。Job 管理、actor、channel を新設する案は同じ保証に対して過剰なため採用しない。

### 4. canonical confirmation と failure は最新 command だけを扱う

`reconcileCanonical()` と `isConfirmed()` の値比較は維持するが、同一 supersession key では最新 command だけが pending に存在する。Room が最終値だけを通知した場合、その最新 command が `CommittedAwaitingCanonical` なら `Success` となり除去される。

最新 command の repository call が `Failure` なら、その command を `Failure` で完了して除去し、presentation はその時点の DB canonical state へ rollback する。最新 command が `NoOp` なら既存どおり terminal `NoOp` とする。supersede 済み command の遅延 failure は無視し、最新 command の lifecycle や projection に伝播させない。異なる Board または異なる operation kind の failure は相互に影響しない。

## Implementation Contract

1. `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/BoardTabsCoordinator.kt` の private implementation のみを変更する。
2. `Operation.Scroll`、`Operation.Pin`、`Operation.Info` から安定した `(boardUrl, operation kind)` supersession key を得る private helper/type を追加する。`Ensure` と `Delete` は key を返さない。
3. `register()` で同一 key の既存 `BoardPendingOperation` を収集し、単一 `_state.update` でそれらを除外して新 command を追加し、既存の `rebuildPresentation()` を一度だけ呼ぶ。Delete の `selectedKey` 処理は変更しない。
4. state 更新後に supersede 対象の `result` を `TabCommandResult.NoOp` で `complete` する。完了済み deferred の再完了を前提にせず、遅延 write 完了は既存 `finish()` の id-based removal と `CompletableDeferred.complete` の terminal 性で無害にする。
5. `execute()` の Loaded 待機後・repository dispatch 前に command ID の membership check を置く。存在しない command は repository を呼ばず return する。in-flight repository call の cancellation 機構は追加しない。
6. `accept()`、`acceptWithoutWaiting()`、`finish()`、`isConfirmed()` の公開 result 契約、targeted repository method の選択、`effectiveTabs()` の fold 順、close/selection/session cleanup を変更しない。
7. application code の変更範囲を上記 coordinator から広げる必要が判明した場合は、実装を止めて OpenSpec を再評価する。Thread のファイルは変更しない。

## Error Cases and Compatibility

- 先行 command が write 前に supersede: write を skipし、先行 waiter は `NoOp`、最新 command だけが persist/confirm される。
- 先行 command が write 中または commit 後に supersede: 先行 waiter は `NoOp`。先行結果は projection を復活させず、最新 write が DB の最終値を決める。
- 最新 command の write failure: 最新 pending を `Failure` で除去し、canonical snapshot を表示する。先行 command は復活させない。
- Room が先行値だけを一時通知: 最新 command の期待値と異なるため pending/projection を維持する。
- Room が最終値だけを通知: 最新 command を確認して pending を除去する。
- 同値への反復更新: 最新 command の commit 後に同値 canonical が観測されれば確認できる。古い同値 command は復活しない。
- coordinator `close()`: その時点で残る最新 pending のみ既存の `CancellationException` failure で完了する。既に supersede 済み waiter は `NoOp` のまま変化しない。

API、DB schema、保存データ形式に変更はなく migration は不要である。rollback は coordinator と追加 unit test の commit を戻すだけでよい。

## Testing Strategy

`app/src/test/java/com/websarva/wings/android/slevo/ui/tabs/BoardTabsCoordinatorTest.kt` の既存 `MutableSharedFlow(replay = 1)`、MockK、`runTest` / `runCurrent()` harness を使う。実 Room/device test は不要であり、Room coalescing は初期 snapshot と最終 snapshot だけを emit する fake Flow で deterministic に再現する。

- repeated scroll: 各 write success 後も中間 Flow を emit せず、同一 Board の pending が最大 1、projection が常に最新、最終 snapshot で pending 0・canonical/effective が最終値になること。
- queued scroll coalescing: dispatcher を進める前の複数 command では supersede 済み repository call が skip され、最新 targeted write だけが必要値で呼ばれること。
- rapid pin: effective projection を基にした toggle が最新 intent を表し、中間通知なしでも pending が 1 を超えず、最終 canonical に収束すること。
- rapid resolved info: 同一 Board の古い boardId/boardName projection が復活せず、最終 info snapshot で pending が解放されること。
- failure: supersede 後の最新 write failure で pending が 0 になり canonical state へ戻ること。先行の遅延 failure が最新 command を除去しないこと。
- different-board independence: Board A と Board B の同種 command が別々に pending となり、A の最終 snapshot は A だけを確認し、B は B の最終 snapshot まで維持されること。
- regression: 既存の close、selection、animation、resolved-info field preservation、no bulk persistence の test を維持する。

## Risks / Trade-offs

- [supersede 済み command の個別 write 成否を waiter に返さない] → `NoOp` を「obsolete intent」の明示的 terminal result と定義し、最新 command の failure だけを現在 intent の failure とする。
- [in-flight stale write は DB に一時反映され得る] → cancellation race を導入せず、既存 FIFO `DatabaseWriteGate` と後続最新 write により最終値を保証し、projection は最新 intent を維持する。
- [operation kind の分類漏れ] → key helper を sealed `Operation` の exhaustive `when` で実装し、Ensure/Delete が対象外であることを test/regression で固定する。
- [private lifecycle の test が実装詳細へ依存] → reflection や新しい production API を追加せず、repository call、公開 controller state、presentation、最終 canonical convergence で検証する。

## Migration Plan

データ migration と段階 rollout は不要。coordinator と unit test を同一変更として適用し、build/unit test 成功後に通常配布する。問題があれば当該変更を revert して従来の confirmation semantics に戻せる。

## Open Questions

なし。
