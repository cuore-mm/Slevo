## Context

`SlevoApplication.onCreate()`は`runBlocking`内で`PendingRestoreApplier.runIfNeeded()`を完了させてから`MainActivity`を開始する。applierと`PendingRestoreCompletionChecker`は`filesDir/pending-restore-result/restore-result.json`へ最新の成功・失敗を記録するが、`PendingRestoreManager.readResultFile()`と`deleteResultFile()`を呼ぶproduction UI consumerは存在しない。現在の`BackupScreen`の`SnackbarHost`はroute localであり、再起動後の初期routeがバックアップ画面でない場合に通知できない。

Room migrationが必要な復元では、applierが`migrationCompleted = false`の中間成功resultと`MIGRATION_PENDING` markerを書いた後、Roomの`DatabaseCallback.onOpen()`が`PendingRestoreCompletionChecker.runIfNeeded()`を非同期起動する。checkerは検証後に最終resultを上書きするため、root UIが初回compositionでresultだけを読む実装には中間成功を誤通知するraceがある。

result fileは履歴ではなく最新状態を保持する単一documentである。通知処理はrestore適用の成功条件に含めず、UIが起動できない場合にもrestore state machineを進行できなければならない。

## Goals / Non-Goals

**Goals:**

- 現在のrouteに関係なく、起動時restoreの確定した成功・失敗をroot-level Snackbarで通知する。
- 未通知resultをViewModel所有の`UiState`として保持し、表示完了後にだけacknowledgeする。
- `MIGRATION_PENDING`の中間成功resultを表示しない。
- 構成変更、画面遷移、表示前のprocess終了、新旧resultの競合に対して通知を失わない。
- malformed resultやI/O failureでアプリ起動とrestore state machineを壊さない。

**Non-Goals:**

- pending restoreの適用順序、rollback、migration validationを変更しない。
- marker/result JSON schema、backup archive形式、Room schemaを変更しない。
- 通知履歴画面、再通知ボタン、failure詳細dialogを追加しない。
- `BackupScreen`固有のexport/restore準備Snackbarをroot-levelへ移動しない。
- Snackbarから再restoreやartifact削除を実行するactionを追加しない。
- Snackbarの文言・layout・styleを変更せず、待機中のprogress bannerその他の進捗UIを追加しない。

## Decisions

### 1. App-level ViewModelが通知UiStateを所有する

`MainActivity`配下に専用のHilt ViewModelを追加し、未通知restore resultをnullableなnotificationとして持つimmutable `UiState`を公開する。Repository規約に従い、ViewModelがfileを直接操作せず、`PendingRestoreManager`またはpending package内の専用result consumer境界へ読取・acknowledgeを委譲する。

起動直後のone-shotを`SharedFlow`のreplayなしイベントとしてemitする案は採用しない。Compose collector開始前のemitで通知が消失するためである。未通知notificationはacknowledge完了までstateに残し、UIはnotificationをkeyにした`LaunchedEffect`で表示する。

想定するデータフローは次のとおり。

```text
restore marker + restore-result.json
              |
              v
pending result consumer
  - marker/result整合確認
  - 中間result待機
  - typed read outcome
              |
              v
app-level ViewModel / UiState
              |
              v
AppScaffold SnackbarHost
              |
       showSnackbar完了
              |
              v
ViewModel.acknowledgeResult()
              |
              v
対応するresultだけを削除
```

### 2. `AppScaffold`に唯一のroot SnackbarHostを置く

`AppScaffold.kt`の既存`Scaffold`へroot用`SnackbarHostState`と`SnackbarHost`を追加する。`MainActivity.kt`はapp-level ViewModelの`UiState`とcallbackを`AppScaffold`へ渡す。これにより`AppNavGraph`のstart destination、deep link、画面遷移に関係なく同じhostを使用する。

既存`BackupScreen`のSnackbarはrestore準備やbackup exportなどroute固有の即時操作結果に使用し続ける。起動時restore結果だけをroot hostで扱い、二重hostへ同じresultを送らない。

成功・失敗文言は`app/src/main/res/values/strings.xml`の固定resourceから解決する。resultの`message`は内部診断を含み得るため直接Snackbarへ表示しない。失敗通知は成功通知より長い表示時間を使用してよいが、初期実装ではactionを持たない。

### 3. markerで中間resultをgateし、lifecycle中に再評価する

result consumerはresult単体を通知可能と判定しない。少なくともmarker status、`success`、`migrationCompleted`の整合を確認する。

- `COMPLETED`: `success = true`かつ`migrationCompleted = true`のresultだけを成功候補にする。不整合は診断へ記録し、成功を推測しない。
- `FAILED`: failure resultを通知候補にする。
- `ROLLBACK_REQUIRED`: data確認が必要なfailure resultを通知候補にできる。後続起動でrollback retryが別resultを書いた場合は新しい結果として再評価する。
- `MIGRATION_PENDING`: `migrationCompleted = false`の中間resultを通知せず、marker遷移後に再評価する。
- `PREPARED`、`APPLYING`、`ROLLBACK_READY`、`DB_SWAPPED`: 適用途中として通知しない。
- markerなし: pending directory cleanup後にresultだけが残る正常なsuccess/failure pathが既存実装にあるかを実装前に既存testsとwrite orderingで確認する。正当なorphan resultは通知可能、由来を確認できないresultは診断して安全に破棄する。

`MIGRATION_PENDING`の再評価はActivityが`STARTED`の間だけ継続する。既存の`repeatOnLifecycle(Lifecycle.State.STARTED)`をlifecycle境界として、block開始時にViewModelの観察を開始し、blockの`finally`で観察を停止する。観察jobは開始直後にdelayなしで1回読み、`Pending`なら200msから指数的に待機時間を増やし、2秒を上限として`200ms → 400ms → 800ms → 1.6s → 2s → 2s ...`で再読する。`Ready`、`Absent`、`Unreadable`のterminal outcomeでjobを終了し、回数や総時間では終了させない。

`STOP`では待機中・読取中の観察jobをcancelし、次の`START`では以前のbackoffを引き継がず即時読取から再開する。ViewModel clear時は`viewModelScope`によって同じjobをcancelする。開始・停止・job参照の更新はMain thread上へ集約し、各開始に単調増加する観察generationを割り当てる。新しい開始または停止は先にgenerationを無効化してから旧jobをcancelし、現行として所有するgenerationを常に1つ以下にする。同じgenerationのactive jobがある重複開始は新しいjobを追加しない。

cancel済みのreadがcancellationへ即応しない場合、その旧coroutineがunwindするまで新generationのreadと物理的に重なることは許容する。即時再開を妨げる旧jobのjoinは行わず、generation guardによって旧jobが`UiState`をpublishすること、次のbackoff/readをscheduleすること、現行job参照をclearまたは置換することを禁止する。ここでの「重複job防止」は、現行として所有されstateへ作用できる観察generationが複数存在しないことを意味する。

実装時にはRoomが初回openする既存経路とcheckerの実行順をtestで固定し、backoffとlifecycle cancellationはvirtual timeと制御可能なconsumerで検証する。sleepだけに依存するCompose testは作らない。

代替案として初回compositionで一定時間delayしてから読む方法は、端末性能やmigration時間に依存してraceを除去できないため採用しない。`PendingRestoreCompletionChecker`をUIから直接呼ぶ方法も、UIをrestore state machineへ結合するため採用しない。

### 4. acknowledgeは読み取ったresultと現在のresultを照合する

Snackbar表示完了後、UIはViewModelへnotificationのacknowledgeを通知する。ViewModelはconsumerへ委譲し、表示対象として読み取ったresultと現在のresultが同一である場合だけ削除する。Snackbar表示中にcheckerまたは次のrecoveryがresultを書き換えていた場合、新しいresultを削除せず再評価対象に残す。

result schemaへnotification IDを追加しないため、consumerは既存resultの全fieldを表すimmutable valueまたは読取時raw contentのfingerprintをprocess内tokenとして保持する。compareとdelete間の競合を防げる既存同期境界があるか実装前に確認し、なければpending result file操作専用のprocess-wide `Mutex`を導入してproduction reader/writer/acknowledgerで共有する。単なる無条件`deleteResultFile()`をSnackbar callbackから呼ばない。

`showSnackbar()`完了前には削除しない。表示中にprocessが終了した場合はfileが残り、次回起動で再通知される。この契約は正常process内ではone-shot、process failureに対してはacknowledgeまでat-least-onceとなる。

### 5. File read outcomeを区別してfallbackを固定する

既存`readResultFile()`が「fileなし」と「parse/read失敗」を同じ`null`へ畳み込む場合、専用consumerは少なくとも`Absent`、`Ready`、`Pending`、`Unreadable`を区別するtyped outcomeを持つ。

- `Absent`: UI stateを変更せず通常起動する。
- `Pending`: fileを保持し、Activityが`STARTED`の間は上限2秒の指数backoffで再読する。`STOP`後は次回`START`の即時読取へ委ねる。
- `Ready`: notification UiStateを公開する。
- `Unreadable`: errorを`AppLogger`へ記録し、成功・失敗を推測しない。同じ破損fileで毎起動失敗しないよう、current contentが変わっていないことを確認して削除する。削除失敗はログに残し、起動を継続する。

UI向けmessageには診断reason、filesystem path、exception textを含めない。result内容のloggingは既存security方針に従い、Cookieやsnapshot値を記録しない。

## Implementation Contract

1. 実装開始時に`PendingRestoreManager.kt`、`PendingRestoreFileStore.kt`、`PendingRestoreApplier.kt`、`PendingRestoreCompletionChecker.kt`の全result write/read/delete callを再確認し、markerなしresultが正当となるorderingを一覧化する。
2. pending packageにUI非依存のresult consumer interfaceとproduction implementationを置く。typed read outcome、terminal gate、conditional acknowledgeをこの境界に集約する。
3. result fileのwriteとacknowledgeが同一processで競合しないよう、既存file storeを共有可能なDI singletonへ統合するか、専用process-wide synchronizationを全production write pathへ適用する。UI側だけをlockして完了扱いにしてはならない。
4. app-level ViewModelとimmutable `UiState`を追加する。ViewModelはSTARTED中のpending観察job、200ms開始・2秒上限の指数backoff、terminal停止、generationによる重複・cancel race防止、acknowledge、acknowledge失敗を管理し、Composableへfilesystem型や`PendingRestoreResultFile`を公開しない。
5. `MainActivity.kt`でViewModel stateをlifecycle-awareにcollectする。既存の`repeatOnLifecycle(STARTED)`から観察開始を呼び、block終了時の`finally`で観察停止を必ず呼んでから、`AppScaffold.kt`へ表示modelとcallbackを渡す。
6. `AppScaffold.kt`のroot `Scaffold`へ`SnackbarHost`を1つ追加する。notificationをkeyにした`LaunchedEffect`で`showSnackbar()`を呼び、戻った後に同じnotification tokenをacknowledgeする。
7. 成功・失敗文言をstring resourceへ追加し、resultのdiagnostic messageを表示しない。
8. 既存`BackupScreen` Snackbar、navigation start destination、restore適用処理、marker/result DTO schemaを変更しない。
9. 新規class/interface/data classにはKDocを付け、state更新・validation・file I/Oを行うnon-trivial functionにもrepository規約どおりKDocと必要なsection commentを付ける。

## Error Cases and Compatibility

- resultなし、marker途中状態、marker/result不整合、malformed JSON、read failure、delete failureを通常起動可能な非fatal outcomeとして扱う。
- `ROLLBACK_REQUIRED`通知後に次回起動でrollback結果が更新された場合、更新後resultは別の通知候補として扱う。
- 古いバージョンが残した既存result JSONは現行converterが読める範囲で通知する。schema拡張は行わない。
- app-level ViewModel生成やnotification loadはRoom database openを同期blockしない。
- notification処理の失敗によってpending/rollback/quarantine artifactをcleanupしない。

## Testing Strategy

- result consumer unit test: success、failure、`ROLLBACK_REQUIRED`、`MIGRATION_PENDING`中間result、marker/result不整合、markerなし、malformed、read/delete failure、conditional acknowledge、新result上書き競合。
- ViewModel unit test: START前のreadなし、START時の即時read、pending中の`200/400/800/1600/2000/2000ms` backoff、terminal停止、STOP/ViewModel clear時cancel、再START時の即時read、単一の現行generation ownershipと旧generationの遅延完了によるrace防止、acknowledge成功/失敗、同一token重複防止。
- Activity lifecycle test: `repeatOnLifecycle(STARTED)`の開始・終了がViewModelの観察開始・停止へ対応し、STOP中にreadが継続せず、再STARTで即時readすることを確認する。
- Compose test: start destinationで成功/失敗Snackbar表示、別routeでもroot host表示、表示完了callback、notificationなし、navigation中のhost維持。
- 既存回帰test: `PendingRestoreApplierTest`と`PendingRestoreCompletionCheckerTest`でresult write orderingと同期境界を確認する。
- Android CIで全unit testsとAPK buildを実行し、workflow runの`headSha`が検証対象commitと一致することを確認する。

## Risks / Trade-offs

- [Room checker完了前に中間成功を読む] → marker gateとSTARTED中の指数backoff観察で`MIGRATION_PENDING`を通知せず、200msから2秒上限まで負荷を抑えて最終化を待つ。
- [Snackbar表示中にresultが更新され、新resultを削除する] → 読取tokenとのconditional acknowledgeとprocess-wide file synchronizationを使用する。
- [process終了で同じ通知が再表示される] → 表示前に失うより安全なat-least-once fallbackとして許容し、正常processではacknowledge後にone-shotとする。
- [root Snackbarと画面固有Snackbarが競合する] → hostは別用途のまま維持し、起動結果をroot hostだけへ送る。必要ならroot通知を優先してqueueする。
- [malformed resultを削除して診断を失う] → 削除前にmetadata付きerror logを残し、機密性のあるraw payloadはlogへ出さない。
- [STARTED中に最終化されない] → 2秒間隔を上限として観察を継続し、STOPでfileを保持したままcancelして、次回STARTまたはprocess起動で即時再評価する。
- [STOP直後に旧readが完了して新しい観察と競合する] → 観察generationを停止・再開時に無効化し、現行generation以外からのpublishとjob参照更新を拒否する。

## Migration Plan

1. result consumerとtestsを追加し、既存result/marker schemaとの互換性を確認する。
2. app-level ViewModelとroot Snackbarを追加する。
3. production result writerとconditional acknowledgeの同期境界を統合する。
4. CIで既存pending restore tests、追加unit/Compose tests、APK buildを検証する。
5. rollback時はroot notification consumer/ViewModel/Snackbar wiringだけを戻せる。既存result writerとrestore state machineは変更前の動作を維持する。

## Open Questions

- なし。通知UIは全画面共通のroot-level Snackbar、成功・失敗の両方を対象とすることで合意済み。
