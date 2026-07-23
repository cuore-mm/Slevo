## Context

pending restoreは`filesDir/pending-restore/restore.json`の`PendingRestoreMarker.status`をsource of truthとして、`PREPARED`、`APPLYING`、`ROLLBACK_READY`、`DB_SWAPPED`、`MIGRATION_PENDING`、終端状態へ遷移する。現在の`RealPendingRestoreFileStore.writeMarker()`、`PendingRestoreManager.prepareRestore()`、`PendingRestoreManager.updateMarker()`は`File.writeText()`で同じmarkerを直接truncateしており、process終了やI/O例外が書き込み途中に発生すると、最後に確定した状態も失われる。

`RealPendingRestoreFileStore.readMarker()`と`PendingRestoreManager.readMarker()`はmalformed JSONを`null`へ変換するため、更新途中のmarkerは「pending restoreなし」と誤認され得る。特にDB swap後にmarkerを失うと、次回起動でrollbackまたはstale recoveryへ進めない。

marker I/Oは`PendingRestoreFileStore.kt`と`PendingRestoreManager.kt`に重複している。前者は起動時applier/completion checker、後者はrestore準備と既存pending判定から使用されるため、一方だけのatomic化では不十分である。

## Goals / Non-Goals

**Goals:**

- marker更新途中にprocessが終了しても、直前に確定したmarkerを次回読み取り時に回復する。
- 初回marker publishが完了しなかった場合、不完全なJSONを有効markerとして扱わない。
- markerの全production read/write pathを同じatomic file abstractionへ統一する。
- write失敗をcallerへ伝播し、state transition成功後の処理を続けない。
- marker schema、status、path、既存JSON compatibilityを維持する。

**Non-Goals:**

- `pending-restore-result/restore-result.json`のatomic化。
- rollback manifest、staged DB、DataStore JSONのatomic化。
- DataStore rollback snapshotのprocess death対応。
- startup restore resultのUI通知。
- 複数processまたは複数threadからの同時marker更新を新たに許可すること。

## Decisions

### 1. Android frameworkの`AtomicFile`をmarker publicationに使用する

`android.util.AtomicFile`を`restore.json`へ適用する。現行Android APIでは更新内容を`.new`へ書き、既存base fileを保持したまま、内容を書き終えた後に`finishWrite()`で確定する。旧実装由来の`.bak`が残る場合は`openRead()`のlegacy recoveryも利用する。例外時は`failWrite()`で未確定artifactを破棄し、例外をcallerへ再送出する。

手動の`.tmp`作成と`File.renameTo()`も検討したが、失敗時の旧file復元、read時のstale backup回復、stream同期を各call siteで再実装する必要がある。framework APIを使用することで同一filesystem上の小さなstate file向けの既存protocolへ統一でき、外部dependencyも追加しない。

`finishWrite()`と`failWrite()`がstreamの同期・closeとcommit/cleanup処理を担当するため、writer側は返された`FileOutputStream`を別の`use`で先にcloseしない。JSONをUTF-8 byte arrayへ変換してstreamへ書き、成功時に`finishWrite(output)`、失敗時に`failWrite(output)`を必ず呼ぶ。

### 2. marker専用の共有file abstractionへread/writeを集約する

`PendingRestoreFileStore.kt`と`PendingRestoreManager.kt`が独立して`File.readText()/writeText()`を呼ばないよう、marker pathと`JsonAdapter<PendingRestoreMarker>`を受け取るinternal marker file abstractionをpending packageへ追加する。名称と配置は実装時に既存package構成を確認して決定するが、責務は次に限定する。

- parent directoryの作成結果を検証する。
- `AtomicFile.openRead()`によるbackup回復を含むreadを行う。
- `AtomicFile.startWrite()/finishWrite()/failWrite()`によるatomic writeを行う。
- markerなしは`null`、malformed markerは既存契約どおり`null`を返す。
- write失敗は握りつぶさずthrowする。

`RealPendingRestoreFileStore`と`PendingRestoreManager`は同じabstractionを生成または注入して使用する。`PendingRestoreFileStore` interfaceの公開contractは変更せず、既存fakeはmemory実装のままとする。

別案として`PendingRestoreManager`から`RealPendingRestoreFileStore`を直接使用する方法は、result/quarantine/cleanupまで含む広いinterfaceをrestore準備へ結合し、test failure injectionを複雑にするため採用しない。

### 3. read前のbase file存在確認を行わない

既存の`if (!markerFile.exists()) return null`は、baseがなくlegacy `.bak`だけが残る状態で`AtomicFile.openRead()`の回復処理を迂回する可能性がある。共有abstractionはbaseの存在だけでearly returnせず、legacy `.bak`がbaseなしで残る場合は明示的にbaseへ戻した後、`openRead()`を呼ぶ。base/未確定`.new`/legacy `.bak`のいずれからも有効fileを開けない場合だけmarkerなしとして扱う。

`.bak`から回復したJSONまたは旧baseがvalidならそのmarkerを返す。初回publish中断で旧markerが存在せず`.new`だけが残った場合は有効markerを返さず、既存の不完全staging cleanup方針を維持する。

### 4. marker statusごとの既存error semanticsを維持する

`PendingRestoreManager.prepareRestore()`の初回marker writeが失敗した場合はpending stagingをcleanupしてerror messageを返す。`PendingRestoreManager.updateMarker()`および`PendingRestoreFileStore.writeMarker()`の更新失敗は例外をcallerへ返し、呼び出し元の既存failure処理へ委ねる。

marker JSONのfield、filename、directory、status遷移順序は変更しない。旧versionが作成したbackupなしの`restore.json`も`AtomicFile.openRead()`でそのまま読めるためdata migrationは不要である。

### 5. real filesystemで中断状態を再現する

`PendingRestoreFileStoreTest.kt`を中心にtemporary application `filesDir`を使い、次を検証する。

- marker初回作成と通常更新がround-tripする。
- validな旧marker確定後、`AtomicFile.startWrite()`で新markerのpartial bytesを書き`finishWrite()`を呼ばない中断状態を作ると、共有readerが旧markerを回復する。
- 初回作成中断でpartial JSONだけが残る場合、有効markerを返さない。
- write failureでは旧markerが保持され、例外がcallerへ伝播する。
- `cleanupPending()`後はbaseとAtomicFile backupの両方が残らない。

`PendingRestoreManagerPrepareTest.kt`では、prepare成功後のmarkerが共有readerで読めること、既存`shouldFailMarkerWrite` pathがstaging cleanupとerror returnを維持することを確認する。source textだけを照合するtestではなく、可能な限りreal filesystem behaviorをassertする。

## Implementation Contract

1. `PendingRestoreFileStore.kt`と`PendingRestoreManager.kt`内のmarkerに対する直接`readText()/writeText()`を列挙し、すべてを共有atomic marker abstractionへ置換する。
2. abstractionは`AtomicFile.startWrite()`の返却streamへUTF-8 JSON bytesを書き、正常時だけ`finishWrite()`を呼ぶ。catchでは`failWrite()`を呼んだ後、元例外を再throwする。
3. streamを`finishWrite()`より前に`close()`する`writer().use {}`または`output.use {}`構造にしない。
4. readerはbase fileの`exists()`だけでearly returnせず、`AtomicFile.openRead()`にbackup recoveryを実行させる。
5. parent directoryの`mkdirs()`結果を検証し、directoryを準備できない場合はwrite開始前に例外を返す。
6. `PendingRestoreManager.prepareRestore()`、`readMarker()`、`updateMarker()`と`RealPendingRestoreFileStore.readMarker()/writeMarker()`の全pathで共有abstractionを使用する。
7. `PendingRestoreMarker`、`PendingRestoreFileStore` interface、marker filename/path、result file処理は変更しない。
8. 新規class/non-trivial functionにはrepository規約どおりannotationより上のKDocを付け、長いfunctionはsection commentで分割する。

## Error Cases and Compatibility

- parent pathがfileでdirectoryを作れない: writeを開始せず例外を返す。
- JSON byte書き込みまたはfilesystem同期に失敗: `failWrite()`を実行し、旧markerを維持して例外を返す。
- process deathで`.new`またはlegacy `.bak`が残る: 次回readが旧markerを回復する。
- 初回write中断でvalid markerが一度も確定していない: malformed markerを`null`として扱い、未確定stateを実行しない。
- 旧アプリが作成した通常の`restore.json`: schema/path変更なしで読み取る。
- 同時write: `AtomicFile`自体はthread-safeではないため、既存のsingle restore orchestration前提を維持する。新しい並行call siteは追加しない。

## Risks / Trade-offs

- [Risk] `PendingRestoreManager`とfile storeの片方に直接I/Oが残るとatomicityがstateごとに変わる。→ marker filenameへの直接read/write検索をtaskと最終検証へ含める。
- [Risk] streamを先にcloseすると`finishWrite()/failWrite()`のprotocolを壊す。→ byte writeとfinish/failの構造を固定し、failure testを追加する。
- [Risk] parse failureを`null`にする既存契約は外部改変によるcorrupt markerを区別できない。→ 今回はwrite中断防止へscopeを限定し、malformed marker policyは変更しない。
- [Risk] file syncによりmarker transitionごとにI/O latencyが増える。→ restore時だけの小さいJSONであり、state durabilityを優先する。
- [Risk] `AtomicFile`は同時writerを排他しない。→ single-process/single-orchestration前提を明記し、並行write対応は別changeとする。

## Migration Plan

1. shared atomic marker abstractionとfilesystem testsを追加する。
2. `RealPendingRestoreFileStore`のread/writeを移行する。
3. `PendingRestoreManager`のprepare/read/updateを移行する。
4. 既存marker compatibilityとpending restore回帰testsを実行する。
5. Android CIで全unit testsとAPK buildを実行する。

marker schema/pathを変更しないためon-device migrationは不要である。rollback時はcodeを旧実装へ戻しても通常のbase `restore.json`は読めるが、rollback時点で`.bak`だけが残る極端な状態では旧実装が回復できないため、release後の単純なcode rollbackより修正版を維持する。

## Open Questions

なし。result fileとrollback manifestのatomic化は別指摘・別changeとして扱う。
