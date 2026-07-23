## 1. Result Lifecycleの現状固定

- [x] 1.1 `PendingRestoreApplier.kt`、`PendingRestoreCompletionChecker.kt`、`PendingRestoreManager.kt`、`PendingRestoreFileStore.kt`のresult write/read/delete callを一覧化する。完了条件: marker status、write ordering、cleanup ordering、markerなしresultが発生するpathが実装メモまたはtest名で特定される。
- [x] 1.2 migrationあり・なし、rollback成功、`ROLLBACK_REQUIRED`、`FAILED`、`COMPLETED`の各pathについて通知可能条件をtest tableへ落とす。完了条件: 中間成功を通知しない期待値が各statusに設定される。
- [x] 1.3 result fileのproduction writer instanceとHilt bindingを確認する。完了条件: applier/checker/consumerが共有できるprocess-wide synchronizationの配置先が既存class名に基づいて決定される。
- [x] 1.4 `PendingRestoreResultFile`の全fieldと既存converter behaviorを確認する。完了条件: schema変更なしでconditional acknowledgeに使用するimmutable tokenまたはraw fingerprintが決定される。

## 2. Pending Result Consumer

- [x] 2.1 `data/backup/pending/`へresult consumerのinterface、typed read outcome、UI非依存notification modelを追加する。完了条件: `Absent`、`Pending`、`Ready`、`Unreadable`をnullに畳み込まず表現できる。
- [x] 2.2 production consumerでmarkerとresultを読み、`COMPLETED`、`FAILED`、`ROLLBACK_REQUIRED`、進行中statusをdesignのgate tableどおり分類する。完了条件: `MIGRATION_PENDING`かつ`migrationCompleted = false`が`Ready`にならない。
- [x] 2.3 marker/resultのsuccess・`migrationCompleted`不整合をvalidation failureとして扱う。完了条件: 不整合から成功または失敗を推測せず、診断logを残して通常起動を継続する。
- [x] 2.4 resultなし、read failure、malformed JSONを区別するread pathを実装する。完了条件: malformed resultはraw payloadをlogへ出さず、同一破損contentだけを条件付きcleanupできる。
- [x] 2.5 読取時tokenと現在のresultを比較して削除するconditional acknowledge APIを実装する。完了条件: 一致時だけ削除し、更新済みresultを保持する。
- [x] 2.6 result writerとconditional acknowledgeへ共有のprocess-wide同期境界を適用する。完了条件: compare/delete間にapplierまたはcheckerが新resultを書き込めず、全production write pathが同じ境界を通る。
- [x] 2.7 read/acknowledge failureを非fatalとして`AppLogger`へ記録する。完了条件: notification I/O failureがexceptionを`MainActivity`またはrestore state machineへ伝播しない。
- [x] 2.8 result consumerと追加typeへrepository規約どおりKDocを追加する。完了条件: ownership、terminal gate、token、conditional delete、機密情報をUIへ渡さない制約が記述される。

## 3. Result Consumer Unit Tests

- [x] 3.1 success fixtureで`COMPLETED`かつ`migrationCompleted = true`を`Ready.Success`へ変換するtestを追加する。完了条件: UI modelにdiagnostic messageが含まれない。
- [x] 3.2 `FAILED`と`ROLLBACK_REQUIRED`のfailure fixtureを`Ready.Failure`へ変換するtestを追加する。完了条件: successへ誤分類されず、ユーザー向け固定message種別だけが公開される。
- [x] 3.3 `PREPARED`、`APPLYING`、`ROLLBACK_READY`、`DB_SWAPPED`、`MIGRATION_PENDING`を`Pending`にするparameterized testを追加する。完了条件: 中間resultが通知候補にならない。
- [x] 3.4 marker/result不整合、resultなし、markerなしresultのtestを追加する。完了条件: task 1.1で確定した正当なorphan pathだけが通知され、不明なresultは成功扱いされない。
- [x] 3.5 malformed JSON、read failure、delete failure testsを追加する。完了条件: 各failureが非fatal outcomeとなり、raw result内容がlog assertionへ露出しない。
- [x] 3.6 conditional acknowledgeの一致・不一致testを追加する。完了条件: 一致resultは削除され、Snackbar表示中に上書きされた新resultは残る。
- [x] 3.7 writerとacknowledgeのconcurrency testを追加する。完了条件: controlled coroutine schedulingで新resultのwriteと旧resultのdeleteが競合しても新resultが失われない。

## 4. App-level UiState Owner

- [x] 4.1 `MainActivity` scopeのHilt ViewModelとimmutable `UiState`を追加する。完了条件: nullableな未通知notification、load/recheck状態、安定したnotification tokenをComposeへ公開する。
- [x] 4.2 ViewModel初期化時のlifecycle外readを廃止し、Activityの`STARTED`通知だけを初回観察の起点にする。完了条件: START前にconsumerを読まず、START時の即時readで`Ready`だけをUiStateへ設定し、`Absent`、`Unreadable`では通常起動し、`Pending`ではresultを削除しない。
- [x] 4.3 `MIGRATION_PENDING`向け観察を、即時read後に`200ms → 400ms → 800ms → 1.6s → 2s → 2s ...`で再読する指数backoff loopへ置き換える。完了条件: Activityが`STARTED`の間は回数・総時間で打ち切らず、2秒を超えるdelayを設定せず、`Ready`、`Absent`、`Unreadable`のterminal outcomeで停止する。
- [x] 4.4 ViewModelへlifecycle連動の観察開始・停止APIと単一の現行generation ownershipを実装する。完了条件: 開始時はdelayなしで読み、同一観察の重複開始で現行jobを増やさず、停止時はgenerationを無効化してjobをcancelし、次の開始では旧jobをjoinせずbackoffをresetして即時に読み直す。
- [x] 4.5 観察jobのgeneration guardをUiState publish、次回readのschedule、job参照の解放へ適用する。完了条件: cancelへ即応しなかった旧readが新しい観察の開始後に完了しても、旧generationはnotification、waiting state、現行job参照を変更せず、次のbackoff/readを開始できない。
- [x] 4.6 `acknowledgeResult(token)`でconsumerのconditional acknowledgeを呼び、成功時だけUiStateをclearする。完了条件: delete failureまたはtoken不一致時に新resultを消さず、再評価可能なstateを保つ。
- [x] 4.7 lifecycle観察へ変更したViewModel APIとstate更新functionのKDocを更新する。完了条件: STARTED中の継続観察、terminal停止、STOP/ViewModel clear cancellation、generation ownershipがrepository規約どおり説明される。

## 5. ViewModel Tests

- [x] 5.1 `Absent`、`Ready.Success`、`Ready.Failure`のViewModel unit testsをSTART起点へ更新する。完了条件: 観察開始前のconsumer readが0回で、START相当のAPI呼出し直後に1回読み、Ready時だけnotification UiStateが設定され、各terminal outcome後に追加readがない。
- [x] 5.2 `Pending`から`Ready`へ遷移するtestをvirtual timeで更新する。完了条件: 開始直後のreadと200ms後の最初の再読を境界値で確認し、real sleepなしで最終結果が1件だけ公開され、terminal後に追加readがない。
- [x] 5.3 `Pending`が続く場合の指数backoff testを追加する。完了条件: virtual timeで`200/400/800/1600/2000/2000ms`のread時刻を順にassertし、2秒到達後もSTARTED相当の観察が継続する。
- [x] 5.4 acknowledge成功・delete failure・token不一致testsを追加する。完了条件: 成功時だけnotificationがclearされ、新resultまたは未削除resultが失われない。
- [x] 5.5 停止・再開と重複開始のtestを追加する。完了条件: 停止で待機中jobをcancelして追加readを行わず、再開直後は旧delayの満了を待たずに読み、現行として所有されstateへ作用できるgenerationが常に最大1である。
- [x] 5.6 cancellation raceとViewModel clearのtestを追加する。完了条件: 制御可能なnon-cooperative readを用い、停止・job置換・ViewModel clear後に旧readが完了しても、その結果をpublishせず、次のbackoff/readをscheduleせず、現行job参照をclearまたは置換しない。cancel済み旧readの一時的な物理 overlapはjob重複失敗として数えない。

## 6. Root-level Snackbar UI

- [x] 6.1 `MainActivity.kt`の既存`repeatOnLifecycle(Lifecycle.State.STARTED)`を観察開始・停止APIへ接続する。完了条件: block開始時に観察を開始し、STOP cancellation時を含む`finally`で必ず停止し、Activityはresult fileを直接操作しない。
- [x] 6.2 `AppScaffold.kt`のroot `Scaffold`へ`SnackbarHostState`と`SnackbarHost`を追加する。完了条件: hostが`AppNavGraph`の外側に1つだけ存在し、全routeで同じinstanceが使用される。
- [x] 6.3 notification tokenをkeyにした`LaunchedEffect`で`showSnackbar()`を実行する。完了条件: 表示完了後だけ同じtokenのacknowledge callbackを呼び、compositionだけではfileを削除しない。
- [x] 6.4 成功・失敗のユーザー向け文言を`app/src/main/res/values/strings.xml`へ追加する。完了条件: resultのdiagnostic `message`、exception、pathをSnackbar textへ使用しない。
- [x] 6.5 既存`BackupScreen`のroute-local Snackbarを維持する。完了条件: 起動時resultはroot hostだけへ送られ、backup/export操作結果の表示契約が変わらない。
- [x] 6.6 新規Composable helperを追加する場合は対応する`@Preview`を作成する。完了条件: runtime dependencyなしで意味のあるpreviewが可能なComposableだけにpreviewがあり、Preview functionへKDocを付けない。
- [x] 6.7 lifecycle bridgeのtestを追加する。完了条件: STARTで観察開始、STOPで観察停止、再STARTで再開始が各1回呼ばれ、STOP中に観察が継続しないことをcontrolled lifecycleで確認する。

## 7. Root UI Tests

- [x] 7.1 start destinationで成功notificationを渡すCompose testを追加する。完了条件: 成功Snackbarが表示され、表示完了後にacknowledge callbackがtoken付きで1回呼ばれる。
- [x] 7.2 failure notificationとnotificationなしのCompose testsを追加する。完了条件: failure固定文言だけが表示され、null時はSnackbarとacknowledgeが発生しない。
- [x] 7.3 Snackbar表示中にnavigationするCompose testを追加する。完了条件: route変更後もroot hostが維持され、同じnotificationでcallbackが重複しない。
- [x] 7.4 notification token更新testを追加する。完了条件: 旧result表示中に新tokenへ変わった場合、旧acknowledgeで新resultを消さず新notificationを後続表示できる。

## 8. Regressionと最終検証

- [x] 8.1 `PendingRestoreApplierTest`へ中間result/marker orderingの回帰assertionを追加または既存assertionを確認する。完了条件: migrationありpathで中間result後に`MIGRATION_PENDING`となる現契約がtestで明示される。
- [x] 8.2 `PendingRestoreCompletionCheckerTest`へfinal marker/result orderingと共有同期境界の回帰assertionを追加する。完了条件: final write後にconsumerが確定結果を読める。
- [x] 8.3 `PendingRestoreManagerTest`のread/delete testsを新consumer contractへ更新する。完了条件: 新しいtyped outcomeとconditional acknowledgeを通さないproduction UI call siteが残らない。
- [x] 8.4 最終diffでRoom schema、backup archive DTO、`PendingRestoreMarkerFile`、`PendingRestoreResultFile`のJSON fieldに変更がないことを確認する。完了条件: schema/file format差分が0件である。
- [x] 8.5 `openspec validate notify-pending-restore-result --strict`と`git diff --check`を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 8.6 Android CIで全unit testsとAPK buildを実行する。完了条件: workflow開始前HEADとrunの`headSha`が一致し、全stepが成功する。
- [x] 8.7 最終diffを確認する。完了条件: bounded retryからlifecycle連動観察への変更とそのtests/OpenSpecだけが追加され、Snackbar文言・layout・style、progress banner、restore state machine、databaseVersion直書きなど他のCodex指摘が混在しない。
- [x] 8.8 implementationとtask completionを日本語Conventional Commitでcommitし、remote branchへpushする。完了条件: push成功後にworking treeがcleanである。
