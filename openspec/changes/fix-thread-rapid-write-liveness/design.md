## Context

`ThreadTabsCoordinator.processMutationIntents()` は FIFO で intent を受理するが、各 `processIntent()` を別 coroutine で開始し、先行 command の `awaitConfirmation()` を後続 write の barrier にしない。targeted write は `DatabaseWriteGate` の到着順で直列化される一方、Room 2.7.0 の observable query は write ごとの中間 snapshot を保証しない。

現在の `awaitConfirmation()` は register 前後の `snapshotVersion` と operation 固有条件を待つ。例えば同一 Thread の pin `false→true→false` が連続 commit され、Room が最終 `false` だけを通知すると、最初の `true` waiter は二度と成立しない。同様に Ensure→Delete では一時的な存在通知が省略されると Ensure が残り、Delete→Ensure では一時的な不在通知が省略されると Delete が残る。残存 operation は optimistic projection と caller の待機も残す。

Board の rapid-write correction は update kind ごとに最新 command を保持するが、Thread には `Ensure: Int`、retained `Delete: Unit`、`Pin: Unit`、`Info: Unit` という異なる terminal / cleanup 意味がある。Board 実装の複製や全 Thread command の latest-wins 化は行わない。

## Goals / Non-Goals

**Goals:**

- 同一 Thread の両立しない rapid mutation が中間 Room snapshot なしでも有限に終端する。
- 後続 write が成功した場合だけ先行 operation を supersede し、後続失敗時は先行の confirmation と cleanup を維持する。
- Ensure/Delete の最終存在状態、retained close、Deep Link の明示的結果、targeted write、DB canonical source、metadata merge、atomic presentation を維持する。
- 異なる Thread の operation と failure を独立させる。

**Non-Goals:**

- 全 command または同一 Thread command を canonical confirmation まで直列化しない。
- predecessor blocking、generation、command ID、ownership map、process Job cancellation、timeout、retry、compensating write を追加しない。
- Ensure/Info の exact metadata confirmation を復元しない。
- Repository、DAO、`DatabaseWriteGate`、DB schema、Board coordinator、deferred P2、UI を変更しない。

## Decisions

### 1. pending entry に supersession signal を 1 個だけ持たせる

`ThreadTabsCoordinator.kt` 内の private pending representation を、既存 `ThreadTabPendingOperation` と `CompletableDeferred<Unit>` の supersession signal を持つ小さい entry にする。`pendingOperations` の順序、referential removal、`projectThreadTabs()` への operation 順は維持する。

confirmation は「自身の operation 条件を満たす post-baseline Room snapshot」または「後続の両立しない write が成功した signal」の先着を待ち、`Confirmed` / `Superseded` を返す。signal は pending 変更全般を通知する revision Flow ではなく、その operation を終端させる一回限りの deferred とする。

これにより新しい generation/map、先行 confirmation 条件、Job cancellation を導入せず、Room 通知がなくても後続成功時に先行 waiter を直接起こせる。

### 2. supersession は operation の canonical 条件が両立しない組だけに限定する

後続 operation の repository write が成功した直後、後続 entry より前にある同一 `ThreadId` entry を次の規則で signal する。

| 成功した後続 operation | supersede する先行 operation | 理由 |
|---|---|---|
| `Pin` | `Pin` | 最終 pin 値だけが canonical になり、中間値は省略可能。 |
| `Delete` | `Ensure`, `Pin`, `Info` | 最終不在は先行の存在/value 条件と両立しない。 |
| `Ensure` | `Delete` | 最終存在は先行 Delete の不在条件と両立しない。 |
| `Info` | なし | Info は tab を作成せず、identity presence 条件を変更しない。 |

同じ存在条件を共有する Ensure→Ensure、Delete→Delete、Info→Info は通常の最終 snapshot で一緒に確認できるため signal しない。Delete pending 後の Pin は現行どおり effective list に対象がなく no-op となり、Delete を supersede しない。

単一 Thread key ですべてを latest-wins にする案は、Info や存在しない target の Pin に lifecycle 意味を与えて destructive ordering を壊すため採用しない。同一 operation kind だけに限定する案は Ensure/Delete の一時存在/不在 hang を残すため採用しない。

### 3. signal は後続 write 成功後にだけ発行する

後続 entry は register 時ではなく、targeted repository method が正常な成功結果を返した後、canonical confirmation を待つ前に先行 entry を signal する。後続 write が例外または失敗結果なら signal せず、自身だけを既存 failure path で除去する。

先行 write が既に dispatch 済みでも cancel しない。現行 `processMutationIntents()` の `UNDISPATCHED` 開始と `DatabaseWriteGate` の順序により、後続 write は先行 write の後に適用され、成功した後続 write が最終 DB 状態を決める。superseded entry は projection から除去されるが、遅れて戻る先行 coroutine は同じ entry の二重 removal / completion を行っても terminal result を上書きしない構造にする。

register 時 supersession は後続 failure で先行 intent を不必要に失うため採用しない。in-flight repository coroutine の cancellation は transaction 境界と retained close を不明確にするため採用しない。

### 4. operation ごとの superseded terminal behavior を固定する

- `Pin` / `Info`: `Unit` 成功として完了する。後続成功が intent を obsolete にしたことを public API に新しい result 型として露出しない。
- `Ensure`: 後続 Delete に supersede された場合は `-1` を返す。Deep Link は canonical target を選択・navigation せず、最終不在を成功扱いにしない。
- `Delete`: 後続 Ensure に supersede された場合は `Unit` で完了するが、隣接 selection repair、`_newResCounts`、`_threadSessionStates`、`_threadRuntimeStates` の削除を実行しない。最終的に存在する tab の presentation/session を古い close completion が破壊しない。
- `Confirmed` の場合は既存 result、selection repair、session/runtime cleanup をそのまま行う。

## Data Flow

```text
same Thread: earlier write ──success──> await(own Room condition OR superseded signal)
                                         ▲
later incompatible write ──success───────┘
           │
           ├─ signal earlier entry (terminal without intermediate Room value)
           └─ await own final Room condition ──> remove latest pending

later write failure ──> remove/fail later only; no signal; earlier keeps normal wait
```

## Implementation Contract

1. `app/src/main/java/com/websarva/wings/android/slevo/ui/tabs/coordinator/ThreadTabsCoordinator.kt` に private pending entry と private `Confirmed` / `Superseded` resolution を追加する。public API と result 型を追加しない。
2. `pendingOperations` の各利用箇所を entry 対応にし、`projectThreadTabs()` と selection-key 判定には受理順の `entry.operation` だけを渡す。`canonicalTabs` の writer と `publishThreadPresentation()` の atomic 更新を変更しない。
3. `registerPending()` は entry と baseline version を返し、`removePending()` は entry の referential identity だけを削除する。data-class equality で別 command を削除しない。
4. `awaitConfirmation()` を signal と既存 `snapshotVersionFlow.first { version > baseline && isThreadTabOperationConfirmed(...) }` の race に置換する。snapshot 条件自体、特に Pin value、Delete absence、Ensure/Info identity presence は弱めない。
5. successful repository return 後だけ、現在 entry より前の同一 Thread entry を表の規則で signal する。異なる Thread、後続 entry、両立する operation を signal しない。
6. `processPin()` は effective presentation から target 値を導出し続ける。全 rapid pin targeted write を順番どおり dispatch し、bulk persistence や write cancellation を追加しない。
7. `processEnsure()` の `Superseded` は `-1`、`processDelete()` の `Superseded` は cleanup/selection repair なしの `Unit`、`processPin()` / `processInfo()` の `Superseded` は `Unit` とする。
8. exception path は自身の entry だけを除去・failure 完了し、先行 entry を signal しない。supersede 済み先行 coroutine の遅延 return は最新 entry を除去しない。
9. `ThreadTabsProjection.kt`、Repository、DAO、Board の変更が不要な coordinator-private 実装を優先する。上記範囲を広げる必要が判明したら実装を止めて OpenSpec を再評価する。

## Error Cases and Compatibility

- latest Pin failure: 先行 Pin は supersede されず、自身の成功 write を表す canonical 値で完了する。latest waiter だけが例外になる。
- Delete failure after Ensure: Ensure を signal せず、canonical presence で正常完了する。Delete だけが失敗する。
- Ensure failure after Delete: Delete を signal せず、canonical absence 確認後に既存 cleanup を行う。Ensure は `-1` / exception の既存 failure pathに従う。
- coordinator `close()`: scope cancellation は既存どおり pending confirmation と未処理 queue waiter を終了する。supersession signal は DB write や scope lifetime を所有しない。
- schema、保存形式、public Kotlin API に互換性変更はなく migration は不要。

## Testing Strategy

`ThreadTabsCoordinatorTest.kt` の `MutableSharedFlow(replay = 1)`、MockK、`runTest`、`runCurrent()`、`CompletableDeferred` write barrier を使い、初期 snapshot と最終 snapshot だけを emit して Room coalescing を deterministic に再現する。

1. rapid repeated pin: 3 回以上を同一 Thread に dispatchし、全 targeted write の値と順序、先行 waiter の有限完了、最終 snapshot 後の latest 完了、pending projection の最終 canonical 収束、bulk API 0 回を検証する。
2. Ensure→Delete: Ensure の存在 snapshot を emit せず両 write を成功させ、Ensure が `-1`、Delete が最終 absence で完了し、tab/selection/session が削除状態に収束することを検証する。
3. Delete→Ensure: Delete の不在 snapshot を emit せず両 write を成功させ、Delete waiter が終端しても close cleanup/selection repair を行わず、最終 presence で Ensure が index を返すことを検証する。
4. successor failure: 後続の両立しない write を失敗させ、先行 waiter が supersede されず先行 canonical 値で完了し、失敗 waiter だけが例外になることを検証する。
5. different-thread independence: Thread A の latest confirmation を保留したまま Thread B の rapid mutation と最終 snapshotを完了でき、A の pending/resultを変更しないことを検証する。
6. 既存 metadata merge、retained close lifetime、Deep Link failure/non-navigation、atomic presentation、targeted persistence test は変更せず回帰対象とする。

実装後は repository 規約に従って `./gradlew testDebugUnitTest` と `./gradlew assembleDebug` を実行する。device test の新設は不要で、Room coalescing 自体ではなく coordinator contract を deterministic fake Flow で検証する。

## Risks / Trade-offs

- [superseded Unit waiter は個別 write の最終可視化を表さない] → 後続 successful write が同じ Thread の authoritative final intent になった場合だけ完了し、DB 最終状態は gate 順序と latest confirmation で検証する。
- [signal と canonical confirmation が同時に成立する] → どちらも terminal で、referential removal と one-shot deferred により二重 completion を無害にする。最終 DB canonical state と latest operation の結果は変わらない。
- [pending entry wrapper が state を増やす] → operation ごとに one-shot signal 1 個だけとし、ID、generation、map、Job ownership、historyを持たない。
- [Room Flow 自体が永久停止する] → superseded predecessor は解放されるが、latest command は既存どおり canonical confirmation を待つ。timeout/retry は本 finding の範囲外である。

## Migration Plan

DB migration と段階 rollout は不要。coordinator-private state と unit test を同一変更で導入し、build/unit test 後に通常配布する。問題時は当該 implementation commit を revert すれば schema/data 変換なしで旧 behavior に戻せる。

## Open Questions

なし。
