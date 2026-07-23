## 1. Marker I/O経路の確認

- [x] 1.1 `PendingRestoreFileStore.kt`、`PendingRestoreManager.kt`とpending packageを検索し、`restore.json`に対する全production `readText()`、`writeText()`、stream I/O call siteを列挙する。完了条件: restore準備、applier、completion checkerの各経路が対応するreader/writerまで追跡されている。
- [x] 1.2 `PendingRestoreFileStoreTest.kt`、`PendingRestoreManagerPrepareTest.kt`、`PendingRestoreApplierTest.kt`、`PendingRestoreCompletionCheckerTest.kt`のmarker fixtureとfakeを確認する。完了条件: real filesystemで検証するtestとmemory fakeのまま維持するtestが区別されている。
- [x] 1.3 使用中Android SDKの`android.util.AtomicFile` contractを確認する。完了条件: `startWrite()`、`finishWrite()`、`failWrite()`、`openRead()`のstream close、sync、backup recovery責務がimplementation commentまたはtest構造へ反映されている。

## 2. 共有Atomic Marker Abstraction

- [x] 2.1 pending packageへmarker pathと`JsonAdapter<PendingRestoreMarker>`を受け取るinternal marker file abstractionを追加する。完了条件: typeの責務がmarker JSONのatomic read/writeに限定され、repository規約に沿うKDocがannotationより上にある。
- [x] 2.2 shared readerを`AtomicFile.openRead()`で実装する。完了条件: base fileの`exists()`だけでearly returnせず、base/backupともにない場合は`null`、valid backupがある場合は直前のmarkerを返す。
- [x] 2.3 shared readerでmalformed JSONを既存契約どおり`null`へ変換する。完了条件: parse exceptionがstartupを妨げず、partial JSONが有効markerとして返らない。
- [x] 2.4 shared writerでparent directoryの存在・作成結果を検証する。完了条件: parentが作成不能またはdirectoryでない場合、`startWrite()`前に明示的な例外を返す。
- [x] 2.5 shared writerでmarker JSONをUTF-8 bytesへ変換し、`AtomicFile.startWrite()`のstreamへ書いた後、成功時だけ`finishWrite()`を呼ぶ。完了条件: `finishWrite()`前にstreamを`use`または明示closeしていない。
- [x] 2.6 shared writerのfailure pathで`failWrite()`を実行し、元のwrite/sync exceptionをcallerへ再throwする。完了条件: 不完全な新markerを成功扱いせず、旧markerを回復できる。
- [x] 2.7 shared abstractionのnon-trivial functionsへKDocと必要なsection commentsを追加する。完了条件: atomic publication、backup recovery、error propagationの境界がcodeから読める。

## 3. Production Call Siteの統一

- [x] 3.1 `RealPendingRestoreFileStore`へshared atomic marker abstractionを組み込み、`readMarker()`を委譲する。完了条件: `PendingRestoreFileStore.kt`のreal readerがmarkerへ直接`readText()`しない。
- [x] 3.2 `RealPendingRestoreFileStore.writeMarker()`をshared writerへ委譲する。完了条件: applier/completion checkerからの全status更新がatomic protocolを使用し、interfaceとfake contractは変更されない。
- [x] 3.3 `PendingRestoreManager`へ同じshared abstractionを組み込み、`readMarker()`と`updateMarker()`を委譲する。完了条件: managerの既存pending判定と手動status更新に直接marker I/Oが残らない。
- [x] 3.4 `PendingRestoreManager.prepareRestore()`の最終`PREPARED` marker作成をshared writerへ置換する。完了条件: DB/DataStore staging完了前にmarkerをpublishせず、write失敗時は既存どおりpending cleanup後にerrorを返す。
- [x] 3.5 `shouldFailMarkerWrite` test hookの既存semanticsを維持する。完了条件: test-induced failureではatomic writeを開始せず、staging cleanupとerror messageが回帰testで確認される。
- [x] 3.6 pending packageを再検索してmarker filenameへの直接`readText()`、`writeText()`がproduction codeに残っていないことを確認する。完了条件: result file、staged JSONなどscope外のI/Oだけが検索結果に残る。
- [x] 3.7 `PendingRestoreMarker` data class、status enum、`MARKER_FILENAME`、pending directory path、`PendingRestoreFileStore` interfaceが変更されていないことを確認する。完了条件: JSON fixtureと既存fakeがschema変更なしでcompileする。

## 4. Atomic Marker Filesystem Tests

- [x] 4.1 `PendingRestoreFileStoreTest.kt`に初回marker作成のround-trip testを追加する。完了条件: real temporary `filesDir`で完全な`PREPARED` markerが読み取れる。
- [x] 4.2 同testに通常status更新testを追加する。完了条件: 旧marker確定後のatomic更新で新markerだけが読み取られる。
- [x] 4.3 同testで確定済み旧markerの後に`AtomicFile.startWrite()`でpartialな新JSONを書き、`finishWrite()`を呼ばないprocess-death相当状態を作る。完了条件: production readerがbackupから完全な旧markerを回復する。
- [x] 4.4 初回write中断testを追加する。完了条件: 旧markerなしでpartial JSONだけが残る場合、production readerが有効markerを返さない。
- [x] 4.5 write failure testを追加する。完了条件: callerが例外を観測でき、失敗後も直前に確定したmarkerを読み取れる。
- [x] 4.6 base fileがなくAtomicFile backupだけが残る状態のread testを追加する。完了条件: base `exists()` early returnに依存せず旧markerを回復する。
- [x] 4.7 `cleanupPending()` testへAtomicFile backup artifactを追加する。完了条件: pending directoryとbase/backup markerは削除され、pending外のquarantine incidentは保持される。

## 5. ManagerとState Machine回帰Tests

- [x] 5.1 `PendingRestoreManagerPrepareTest.kt`でprepare成功後のmarkerをshared production readerから読み取るtestを追加または更新する。完了条件: staging完了後にatomicに確定した`PREPARED` markerの内容がpreviewと一致する。
- [x] 5.2 marker parent作成失敗またはwrite failureを注入するmanager testを追加する。完了条件: `prepareRestore()`がfailure messageを返し、不完全なmarker/stagingを残さない。
- [x] 5.3 既存`shouldFailMarkerWrite` testを回帰実行する。完了条件: test-induced failure時のpending cleanupとreturn contractが維持される。
- [x] 5.4 `PendingRestoreApplierTest.kt`のPREPAREDからAPPLYING以降のstatus transition testsを回帰実行する。完了条件: shared atomic writer導入後もstatus順序、rollback判断、failure resultが変わらない。
- [x] 5.5 `PendingRestoreCompletionCheckerTest.kt`のMIGRATION_PENDING、COMPLETED、ROLLBACK_REQUIRED testsを回帰実行する。完了条件: marker write exceptionを含む既存state machine contractが維持される。
- [x] 5.6 `PendingRestoreMarkerTest.kt`と`PendingRestoreManagerTest.kt`のlegacy/current JSON compatibility testsを回帰実行する。完了条件: marker field、default、status serializationに差分がない。

## 6. Documentationと最終検証

- [x] 6.1 `PendingRestoreFileStore.kt`と`PendingRestoreManager.kt`のtype/function KDocをatomic marker ownershipへ更新する。完了条件: marker publication、recovery、result fileとのscope差が説明されている。
- [x] 6.2 長さが約30行を超える変更functionをrepository規約のsection commentsで分割する。完了条件: directory準備、serialization、atomic write、failure recoveryの各境界が明示される。
- [x] 6.3 `openspec validate make-pending-restore-marker-atomic --strict`と`git diff --check`を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 6.4 Android CIで`PendingRestoreFileStoreTest`、`PendingRestoreManagerPrepareTest`、`PendingRestoreApplierTest`、`PendingRestoreCompletionCheckerTest`を含む全unit testsとAPK buildを実行する。完了条件: workflow開始前HEADとrunの`headSha`が一致し、全stepが成功する。
- [x] 6.5 最終diffを確認する。完了条件: marker atomic publicationと関連tests/OpenSpecだけが変更され、result UI、DataStore rollback、rollback manifest、schema変更が混在しない。
- [x] 6.6 implementationとtask completionを日本語Conventional Commitでcommitし、remote branchへpushする。完了条件: push成功後にworking treeがcleanである。
