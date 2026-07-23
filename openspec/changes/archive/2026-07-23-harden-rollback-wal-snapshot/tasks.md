## 1. Marker modelと互換性

- [x] 1.1 `PendingRestoreMarker.kt`の`PendingRestoreMarker`へ`hadExistingLiveDb: Boolean? = null`を追加する。完了条件: 新markerは元DB有無を明示でき、fieldなし旧JSONは`null`としてparseできる。
- [x] 1.2 `PendingRestoreMarker.kt`の`RestoreStatus`へ`ROLLBACK_READY`を追加する。完了条件: 既存status文字列を変更せず、新statusをMoshiでserialize/deserializeできる。
- [x] 1.3 marker serialization testsへ`hadExistingLiveDb=true/false`のround-tripとfieldなし旧JSONの互換caseを追加する。完了条件: 3 casesすべてが期待値で成功する。
- [x] 1.4 新規field/statusへrepository comment rulesに従うKDocを追加する。完了条件: type docsがannotationより前にあり、stateの役割と`null`の互換semanticsが説明されている。

## 2. Rollback snapshot manifest

- [x] 2.1 `PendingRestoreDbSwapper.kt`または同packageの専用fileへ、format version、main DB file名、`walIncluded`を保持するimmutable rollback snapshot manifestを追加する。完了条件: typeにKDocがあり、初期format versionが定数化される。
- [x] 2.2 `RealPendingRestoreDbSwapper`へmanifest read/write helperと`rollback-ready.json` pathを追加する。完了条件: invalid JSON、unsupported version、main欠落、`walIncluded=true`でWAL欠落を完成snapshotとして返さない。
- [x] 2.3 `hasRollbackBackup()`をmain DB存在判定からmanifest-driven validationへ変更する。完了条件: mainだけのpartial directoryは`false`、valid main-only/main+WAL snapshotは`true`になる。
- [x] 2.4 snapshot manifest parse/validationのunit testsを追加する。完了条件: valid main-only、valid main+WAL、manifestなし、invalid JSON、unsupported version、required WAL欠落を個別に検証する。

## 3. Transactional rollback backup作成

- [x] 3.1 `createRollbackBackup()`が完成rollback directoryへ直接書かず、同じparent filesystemのtemp snapshotへmain DBをcopyするよう変更する。完了条件: copy途中のdirectoryを`hasRollbackBackup()`が完成済みと認識しない。
- [x] 3.2 source `-wal`が存在してlengthが1 byte以上の場合だけ必須fileとしてtemp snapshotへcopyする。完了条件: WAL copy成功後に`walIncluded=true`、WALなし/0 byteでは`false`のmanifestを生成する。
- [x] 3.3 main DBまたは必須WALのcopy failureをerror detailとして返す。完了条件: ready manifestをpublishせず、live main/WAL/SHMを変更しない。
- [x] 3.4 mainと必須WALのcopy完了後にだけready manifestを最後に書き、temp snapshotをrollback directoryへ公開する。完了条件: publish/rename失敗もbackup failureとなり、完成snapshotとして認識されない。
- [x] 3.5 `-shm`のbackup処理を削除する。完了条件: source SHMが存在してもrollback snapshotへcopyされない。
- [x] 3.6 file copy/manifest publish failureをdeterministicに注入できるinternal test seamを既存patternに合わせて追加する。完了条件: Android framework mockやfilesystem permission依存なしでmain/WAL/publish failureを個別に再現できる。

## 4. Backup作成security regression tests

- [x] 4.1 `PendingRestoreDbSwapperTest.kt`へmain DBのみの成功caseを追加する。完了条件: ready manifestが`walIncluded=false`で、`hasRollbackBackup()`が`true`になる。
- [x] 4.2 0 byte WALの成功caseを追加する。完了条件: WALをsnapshotへcopyせず、main-only snapshotを完成扱いする。
- [x] 4.3 非空WALの成功caseを追加する。完了条件: mainとWALの内容が一致し、manifestが`walIncluded=true`になる。
- [x] 4.4 非空WAL copy failure caseを追加する。完了条件: errorを返し、ready manifestがなく、live filesが変化しない。
- [x] 4.5 main copy後かつWAL/publish前のprocess death相当となるpartial snapshot caseを追加する。完了条件: main fileがあっても`hasRollbackBackup()`が`false`になる。
- [x] 4.6 source SHM存在caseを追加する。完了条件: snapshot内にSHMが存在しない。

## 5. Manifest-driven rollback restore

- [x] 5.1 `restoreRollbackBackup()`の開始時にready manifestと必須file setを検証する。完了条件: invalid/incomplete snapshotではlive filesを変更せず`false`を返す。
- [x] 5.2 rollback開始時にlive WAL/SHMをcleanupし、main DBを復元する既存順序を維持する。完了条件: main restore failureで`false`を返す。
- [x] 5.3 manifestが`walIncluded=true`の場合にbackup WALを必須で復元する。完了条件: WAL restore failureをlogだけで成功扱いせず`false`を返す。
- [x] 5.4 manifestが`walIncluded=false`の場合はmain DBだけで成功できるようにする。完了条件: WAL/SHMをsnapshotから作成しない。
- [x] 5.5 rollback SHM restore処理を削除する。完了条件: backup directoryに旧SHMが残っていてもlive SHMへcopyしない。
- [x] 5.6 main+WAL成功、main-only成功、main restore failure、必須WAL restore failure、SHM非復元testsを追加する。完了条件: failure casesは`false`で、rollback source filesが保持される。

## 6. Restore state transition

- [x] 6.1 `PendingRestoreApplier.applyRestore()`でlive DB存在確認を`APPLYING` marker書き込み前へ移動し、`hadExistingLiveDb=true/false`を保存する。完了条件: 新規`APPLYING` markerにnon-null値が必ず入る。
- [x] 6.2 元DBありの場合は完成snapshot公開後、元DBなしの場合はbackup不要判定後にmarkerを`ROLLBACK_READY`へ更新する。完了条件: backup failure時は`ROLLBACK_READY`へ進まない。
- [x] 6.3 `replaceDbFile()`を`ROLLBACK_READY` marker永続化後だけ呼ぶ。完了条件: test fakeのevent順序が`APPLYING -> snapshot ready -> ROLLBACK_READY -> replace`になる。
- [x] 6.4 WAL backup failureを既存failure result/markerへ変換し、swapを中止する。完了条件: `replaceDbFile()`未呼び出し、元live main/WAL保持、FAILED detailにWAL backup failureが含まれる。

## 7. Process-death recovery

- [x] 7.1 stale `APPLYING`かつ`hadExistingLiveDb=true`をswap未開始として処理する。完了条件: live main/WALを上書き・削除せず、partial snapshotだけをcleanupしてFAILEDを記録する。
- [x] 7.2 stale `APPLYING`かつ`hadExistingLiveDb=false`をfresh-install swap未開始として処理する。完了条件: partial pending filesをcleanupし、FAILEDを記録する。
- [x] 7.3 stale `ROLLBACK_READY`かつ`hadExistingLiveDb=true`で完成snapshotを再検証してrollbackする。完了条件: invalid snapshotではrollbackせずlive/pending filesを保持する。
- [x] 7.4 stale `ROLLBACK_READY`かつ`hadExistingLiveDb=false`でfresh-install live DB/WAL/SHMをcleanupする。完了条件: 元DBがないcaseだけに限定される。
- [x] 7.5 stale `DB_SWAPPED`以降で完成snapshotがない場合はautomatic main-only rollbackを行わない。完了条件: live DBとpending filesを保持してmanual recovery detailを記録する。
- [x] 7.6 `hadExistingLiveDb=null`の旧markerまたはready manifestなし旧snapshotをambiguousとして扱う。完了条件: live main/WALとrollback filesを削除・上書きせず、FAILED marker/result/logを残す。
- [x] 7.7 `rollbackAndFail()`でmainまたは必須WAL restoreが`false`の場合、pending rollback directoryをcleanupしない既存contractを維持する。完了条件: failure後もmanual recovery用main/WAL/manifestが存在する。

## 8. State recovery tests

- [x] 8.1 `PendingRestoreApplierTest.kt`へ`hadExistingLiveDb`保存とmarker/event順序testを追加する。完了条件: 元DBあり/なし両方で期待state sequenceを検証する。
- [x] 8.2 stale `APPLYING`の元DBあり/なしcasesを追加する。完了条件: 元DBありではlive files保持、元DBなしではfresh partial cleanupとなる。
- [x] 8.3 stale `ROLLBACK_READY`の完成snapshot、不完全snapshot、fresh-install casesを追加する。完了条件: rollback/保全/cleanupの3 actionsが仕様どおり分岐する。
- [x] 8.4 stale `DB_SWAPPED`でmanifestなしまたは必須WAL欠落caseを追加する。完了条件: main-only automatic rollbackを行わない。
- [x] 8.5 fieldなし旧marker JSONを使ったambiguous recovery caseを追加する。完了条件: live DBとrollback filesを保持し、manual recovery failureを記録する。
- [x] 8.6 必須WAL restore failure caseをapplier levelへ追加する。完了条件: pending cleanup未実行、FAILED result、rollback source保持を検証する。

## 9. Documentationと最終検証

- [x] 9.1 `PendingRestoreDbSwapper` interface、snapshot manifest、非自明なfile I/O/state transition helperへKDoc/commentを追加する。完了条件: annotation前type docs、30行超functionのsection header、partial publicationとlegacy preservation branchの説明がある。
- [x] 9.2 既存`PendingRestoreDbSwapperTest`、`PendingRestoreApplierTest`、`PendingRestoreCompletionCheckerTest`、pending restore関連testsを回帰実行する。完了条件: 新旧state recoveryを含む全対象testsが成功する。
- [x] 9.3 `openspec validate harden-rollback-wal-snapshot --strict`と`git diff --check`を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 9.4 implementation/testsをcommit/pushし、`git rev-parse HEAD`を記録してから`gh workflow run "Android CI" --ref <current-branch> --repo cuore-mm/Slevo`を実行する。完了条件: workflow `headSha`が記録SHAと一致し、unit testsとCI APK buildが成功する。
- [x] 9.5 最終diffを確認する。完了条件: rollback snapshot、pending restore marker/state、関連testsに限定され、ZIP schema、backup export、Room/DataStore、UI、dependency、展開size limit変更を混在させていない。
