## 1. 現行FlowとCall Siteの確認

- [x] 1.1 `PendingRestoreApplier.kt`の`applyRestore()`、`rollbackAndFail()`、全`recoverFrom*()`を追跡し、DB rollbackを実行するstatus/call siteを一覧化する。完了条件: DB_SWAPPED、MIGRATION_PENDING、ROLLBACK_REQUIRED、fresh-install、validation failureの全経路が特定されている。
- [x] 1.2 `PendingRestoreDataStoreReflector`のproduction/fake implementationとconstructor call siteを検索する。完了条件: prepare/rollback API追加時に更新すべき全class/test fixtureが列挙されている。
- [x] 1.3 `PendingRestoreDataStoreWriter.kt`、`SlevoPreferenceDataStores.kt`とDataStore testsを確認する。完了条件: 使用中key/value type、cookies非対象、empty store、既存full-overwrite helperのcontractが記録されている。
- [x] 1.4 `PendingRestoreFileStore.cleanupPending()`、FAILED handling、`PendingRestoreManager.handleExistingPending()`を確認する。完了条件: rollback未完了artifactを削除し得るstartup/次回prepare pathが特定されている。

## 2. Durable Snapshot Modelと変換

- [x] 2.1 pending packageへformat version、settings、tabs、nullable cookiesを持つDataStore rollback snapshot modelを追加する。完了条件: cookies対象外の`null`と対象だが空のempty listを区別し、全typeにrepository規約どおりKDocがある。
- [x] 2.2 key名、value type、typed valueを持つsnapshot entry modelとvalue type enumを追加する。完了条件: String、Boolean、Int、Long、Float、Double、StringSetを表現できる。
- [x] 2.3 `PendingRestoreDataStoreWriter.kt`または専用converterへ`Preferences`からentry listへの変換を追加する。完了条件: key昇順、StringSet値昇順のdeterministic outputになり、unknown runtime typeを例外で拒否する。
- [x] 2.4 entry listから`MutablePreferences`へfull overwriteする逆変換を追加する。完了条件: edit前のvalidation後に`clear()`し、各typed key/valueをlosslessに再構成する。
- [x] 2.5 snapshot validationを追加する。完了条件: unsupported format version、store内duplicate key、unknown type、typeに対するvalueの欠損/複数指定をDataStore edit前に拒否する。
- [x] 2.6 cookies対象外ではcookies storeを読取・clear・writeしない分岐を追加する。完了条件: `cookies=null`とempty cookies snapshotの動作が異なるunit testで確認される。
- [x] 2.7 snapshot model/converterのKDocとdata transformation commentsを追加する。完了条件: ordering、uniqueness、null/empty invariantがannotationより上のdocumentationに明記される。

## 3. Atomic Snapshot Store

- [x] 3.1 app-private pending directory内の単一DataStore rollback snapshot fileを所有する専用storeを追加する。完了条件: filename定数とownershipがpending cleanup対象であることをKDocで説明する。
- [x] 3.2 snapshot JSON writeを`AtomicFile.startWrite()/finishWrite()/failWrite()`で実装する。完了条件: streamをfinish/fail前にcloseせず、失敗時に未確定`.new`を成功snapshotとして残さない。
- [x] 3.3 snapshot readでatomic recovery、Moshi decode、model validationを行う。完了条件: missing、partial、malformed、unsupported snapshotを区別可能な結果または明示errorとしてcallerへ返す。
- [x] 3.4 parent directory作成失敗とsnapshot write/read failureをcallerへ伝播する。完了条件: errorを握りつぶしてrollback source確定扱いにするpathがない。
- [x] 3.5 snapshot内容をlog、result message、external storageへ出力しないことを確認する。完了条件: diagnosticにはfailure種別/pathの有無だけが含まれ、cookies valueを含まない。

## 4. Snapshot Model/Filesystem Tests

- [x] 4.1 snapshot converterの全supported type round-trip testを`PendingRestoreDataStoreWriterTest.kt`または専用testへ追加する。完了条件: key、type、valueが変換前後で一致する。
- [x] 4.2 absent key、empty settings/tabs、empty StringSetのtestを追加する。完了条件: rollback後に余分なkeyが削除され、empty valueが保持される。
- [x] 4.3 cookies対象外とempty cookies対象のtestを追加する。完了条件: 前者はcookies無変更、後者はcookies store clearとなる。
- [x] 4.4 deterministic ordering testを追加する。完了条件: input iteration順が異なってもkey/StringSetのserialized順が一致する。
- [x] 4.5 duplicate key、type/value mismatch、unsupported version/type testsを追加する。完了条件: validation失敗前後でtarget MutablePreferencesが変更されない。
- [x] 4.6 atomic snapshot storeのnormal publish/read testをtemporary `filesDir`で追加する。完了条件: settings/tabs/cookiesを含むsnapshotが完全にround-tripする。
- [x] 4.7 snapshot publish中断testを追加する。完了条件: partial `.new`を有効snapshotとして読まず、確定済み旧snapshotがある場合は旧snapshotを返す。
- [x] 4.8 malformed JSON、parent pathがfile、write/read failure testsを追加する。完了条件: callerがfailureを観測し、DataStore rollbackを開始しない。
- [x] 4.9 `cleanupPending()` testへsnapshot base/atomic artifactを追加する。完了条件: rollback/restore完了cleanupではsnapshotが削除され、rollback未完了pathでは保持される。

## 5. Reflector Contractの分割

- [x] 5.1 `PendingRestoreDataStoreReflector`へtarget pre-validationとdurable snapshot作成を行うprepare APIを追加する。完了条件: APIの成功はstaged JSON validationとsnapshot atomic publishの両方完了を意味する。
- [x] 5.2 reflectorへdurable snapshotからDataStoreをfull restoreするrollback APIを追加する。完了条件: settings、tabs、対象cookiesの個別結果をrollback orchestrationが判断できる。
- [x] 5.3 `RealPendingRestoreDataStoreReflector`のprepare phaseでsettings/tabs/cookiesを全parseし、cookies conversionをpre-validationしてからsnapshotを取得・保存する。完了条件: invalid targetではsnapshot/DB replace/DataStore writeを開始しない。
- [x] 5.4 `reflect()`をdurable snapshot準備済み前提へ更新し、write failure時に同snapshotから即時rollbackする。完了条件: process内failureとcold-start recoveryが同じrollback sourceを使用する。
- [x] 5.5 reflectorの全fakeへprepare/reflect/rollback resultとcall eventを追加する。完了条件: production/test compileで未実装classが残らず、call順序をassertできる。
- [x] 5.6 reflector/writerの長いfunctionをJSON validation、snapshot、write、rollbackのsection commentsで分割する。完了条件: 約30行超の変更functionがrepository規約を満たす。

## 6. Apply FlowとROLLBACK_READY Invariant

- [x] 6.1 `PendingRestoreApplier.applyRestore()`でDB rollback snapshot完成後、DataStore prepare/snapshotを実行する。完了条件: snapshot成功前に`ROLLBACK_READY`またはDB replaceへ進まない。
- [x] 6.2 DataStore prepare/snapshot failure pathを追加する。完了条件: live DB/DataStoreが未変更のままfailure resultを記録し、作成済みpending/DB snapshotを安全にcleanupする。
- [x] 6.3 `ROLLBACK_READY` markerをDB/DataStoreの必要なrollback sourceが両方完成した後へ移動する。完了条件: marker write順序testが`DB snapshot → DataStore snapshot → ROLLBACK_READY → replace`をassertする。
- [x] 6.4 fresh-install (`hadExistingLiveDb=false`)でもDataStore prepare/snapshotを実行する。完了条件: DB snapshotなしでもDataStore rollback sourceが確定してからROLLBACK_READYへ進む。
- [x] 6.5 DataStore反映成功後もsnapshotをMIGRATION_PENDING/ROLLBACK_REQUIRED/COMPLETEDまで保持する。完了条件: reflect直後にsnapshotを削除するcodeがなく、success cleanupだけが削除する。

## 7. Combined DB/DataStore Rollback

- [x] 7.1 `rollbackAndFail()`と必要なcallerを`suspend`化し、DB rollbackとDataStore rollbackの結果を別々に扱う。完了条件: runIfNeededのI/O context内で両方を順番に完了してからstatus/cleanupを決定する。
- [x] 7.2 hadExistingLiveDb=trueのcombined rollbackを実装する。完了条件: DB snapshot restoreとDataStore snapshot restoreが両方成功した場合だけfailure cleanupへ進む。
- [x] 7.3 hadExistingLiveDb=falseのcombined rollbackを実装する。完了条件: restore中DB setをcleanupし、restore前DataStore snapshotを復元する。
- [x] 7.4 stale DB_SWAPPED recoveryをcombined rollbackへ変更する。完了条件: cold-startでDBとsettings/tabs/cookiesがrestore前状態へ戻る。
- [x] 7.5 reflect failureをcombined rollbackへ変更する。完了条件: immediate write failureでもdurable snapshotから全対象storeをfull restoreする。
- [x] 7.6 MIGRATION_PENDINGのpre/post validation failure、DB unreadable、unexpected versionのDB rollback pathをcombined rollbackへ変更する。完了条件: DataStore反映済みの全pathでDataStoreもrollbackする。
- [x] 7.7 `recoverFromRollbackRequired()`をcombined rollbackへ変更する。完了条件: completion checker起点とretry起点の両方でDB/DataStore rollbackを実行する。
- [x] 7.8 stale ROLLBACK_READY recoveryをcombined rollbackへ統一する。完了条件: DataStore未変更でもsnapshot restoreがidempotentに成功する。

## 8. Rollback RetryとLegacy Safety

- [x] 8.1 DBまたはDataStore rollback失敗時に`ROLLBACK_REQUIRED` marker/resultを記録し、pending/rollback/snapshotを保持する。完了条件: `cleanupPending()`が呼ばれず次回起動可能なartifactが残る。
- [x] 8.2 次回起動のROLLBACK_REQUIREDでDB/DataStore rollbackを再試行する。完了条件: 1回目failure、2回目successのtestで最終cleanupまで進む。
- [x] 8.3 rollback成功時だけFAILED resultを確定してpendingをcleanupする。完了条件: resultは元failure reasonとrollback completionを区別でき、snapshot内容を含まない。
- [x] 8.4 legacy DB_SWAPPEDでsnapshotがない場合の保全pathを追加する。完了条件: DB-only rollback/cleanupを行わずlive DB、rollback、stagingを保持してmanual recovery reasonを記録する。
- [x] 8.5 legacy MIGRATION_PENDING/ROLLBACK_REQUIREDでsnapshotがない場合の保全pathを追加する。完了条件: DataStore反映済み可能性があるstateを完全rollback成功扱いしない。
- [x] 8.6 `PendingRestoreManager.handleExistingPending()`のFAILED/ROLLBACK_REQUIRED handlingを確認・更新する。完了条件: rollback未完了artifactを次回restore準備が暗黙削除しない。

## 9. Process-deathとMigration回帰Tests

- [x] 9.1 stale DB_SWAPPED直後のcold-start testを追加する。完了条件: DB、settings、tabs、対象cookiesがrestore前値へ戻りsnapshot/pendingがcleanupされる。
- [x] 9.2 settings write後のprocess death testを追加する。完了条件: partial settings反映が消え、tabs/cookiesを含む全対象storeがsnapshotと一致する。
- [x] 9.3 tabs write後のprocess death testを追加する。完了条件: settings/tabsのpartial反映が消え、cookiesもrestore前状態になる。
- [x] 9.4 cookies write後かつMIGRATION_PENDING marker前のprocess death testを追加する。完了条件: DataStore全反映済みでもDB rollback時に全DataStoreをrestore前へ戻す。
- [x] 9.5 MIGRATION_PENDING pre/post validation failureとunexpected version testsへDataStore assertionを追加する。完了条件: 各DB rollback pathでDataStoreもrestore前へ戻る。
- [x] 9.6 completion checker起点ROLLBACK_REQUIRED testへDataStore assertionを追加する。完了条件: migration後failureでもcombined rollbackが成功する。
- [x] 9.7 fresh-install rollback testへ既存DataStore fixtureを追加する。完了条件: live DB setが削除されDataStoreはfixtureへ戻る。
- [x] 9.8 DataStore rollback failure/retry testを追加する。完了条件: 1回目はartifact保持、cold-start 2回目成功後にcleanupされる。
- [x] 9.9 legacy snapshot欠損testsを追加する。完了条件: DB_SWAPPED/MIGRATION_PENDINGのartifactが削除されずDB-only rollbackを行わない。
- [x] 9.10 COMPLETED success cleanup testへsnapshot artifactを追加する。完了条件: success result後にsnapshotを含むpendingだけが削除され、quarantineは保持される。

## 10. Documentationと最終検証

- [x] 10.1 新規snapshot model/storeと変更reflector/applierへrepository規約どおりKDocを追加する。完了条件: ownership、atomic publish、type invariant、rollback retry lifecycleが説明される。
- [x] 10.2 marker/result/backup archive data class、restore status enum、Room schema、`.preferences_pb` file操作に変更がないことを確認する。完了条件: 最終diffにscope外schema/file manipulationが含まれない。
- [x] 10.3 `openspec validate persist-datastore-rollback-snapshot --strict`と`git diff --check`を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 10.4 Android CIでDataStore writer/store/applier/completion checkerを含む全unit testsとAPK buildを実行する。完了条件: workflow開始前HEADとrunの`headSha`が一致し、全stepが成功する。
- [x] 10.5 最終diffを確認する。完了条件: durable DataStore rollback、関連state machine、tests/OpenSpecだけが変更され、result UIや他のCodex指摘が混在しない。
- [x] 10.6 implementationとtask completionを日本語Conventional Commitでcommitし、remote branchへpushする。完了条件: push成功後にworking treeがcleanである。
