## Context

`PendingRestoreApplier.applyRestore()`はcold startでRoom/Hiltの`AppDatabase`が生成される前に実行される。既存live DBがある場合、`PendingRestoreDbSwapper.createRollbackBackup()`はmain DBをrollback directoryへコピーし、`-wal`と`-shm`をbest-effortでコピーしてから`replaceDbFile()`へ進む。

現在の問題は次の2点である。

1. 非空`-wal`にはmain DBへcheckpointされていないcommit済みtransactionが含まれ得るが、コピー失敗をwarningだけで継続する。後続rollbackがmain DBだけを復元すると、そのtransactionが失われる。
2. markerを`APPLYING`へ変更してからmain DB、WALの順にコピーする。main DBコピー後、WALコピーまたはswap前にprocess deathすると、`hasRollbackBackup()`はmain DBの存在だけでpartial snapshotを完成済みと判定する。

`-shm`はSQLiteが再生成できるが、`-wal`は再生成できない。両者を同じbest-effort sidecarとして扱ってはならない。またbackup exportで利用するRoom管理connectionとwrite gateはこのcold-start経路に存在しないため、raw SQLite connectionからlive DBへ`wal_checkpoint(TRUNCATE)`を実行する案は採用しない。

関連する既存file/functionは以下である。

- `PendingRestoreDbSwapper.kt`: `createRollbackBackup()`、`hasRollbackBackup()`、`replaceDbFile()`、`restoreRollbackBackup()`、`cleanWalShm()`。
- `PendingRestoreApplier.kt`: `runIfNeededOnIo()`、`applyRestore()`、`rollbackAndFail()`、stale marker recovery。
- `PendingRestoreMarker.kt`: `PendingRestoreMarker`、`RestoreStatus`。
- `PendingRestoreDbSwapperTest.kt`、`PendingRestoreApplierTest.kt`: file operationとstate recoveryのtests。

## Goals / Non-Goals

**Goals:**

- main DBと存在する非空WALを、一貫したrollback file setとして保存する。
- 必須fileのコピーがすべて成功するまでsnapshotをrecoveryへ公開しない。
- WAL backup failure時はlive DBとWALを変更せず、DB swapを開始しない。
- process death後にbackup作成中とswap開始後を区別する。
- rollback時に必須WALを復元できなければ成功扱いせず、manual recovery用fileを保持する。
- 旧marker/snapshotが新contractを満たすか不明な場合に、live DBまたはrollback filesを破壊しない。

**Non-Goals:**

- live DBへのWAL checkpoint追加。
- multi-processから同じDBへ同時writeする構成の対応。
- ZIP backup schema、export checkpoint、Room schema、DataStore、UIの変更。
- rollback失敗後にmanual recoveryを実行する新UIの追加。

## Decisions

### 1. Rollback snapshotをmain DBと任意の必須WALで構成する

`createRollbackBackup()`はmain DBを必須とし、source `-wal`が存在してlengthが1 byte以上ならWALも必須とする。WALを必須と判定した後のopen/read/copy failureはerrorを返す。0 byteまたは存在しないWALはsnapshotへ含めない。

`-shm`はbackup/restoreしない。shared-memory indexとlock stateはSQLiteが再生成でき、commit済みpayloadの保存先ではないためである。

source file setはcold-startで`AppDatabase`生成前に読み取られ、current process内のwriterが存在しないという既存startup invariantに依存する。この順序は既存application startup/dependency testで維持する。

**代替案:** raw SQLite connectionでcheckpointしてmain DBを自己完結させる案は、live fileを変更し、Room管理connection/write gateを利用できず、crash後WALのrecoveryへ影響するため採用しない。

### 2. 一時directoryとsnapshot manifestで完成snapshotだけを公開する

rollback作成時は既存rollback directory内の完成snapshotへ直接copyせず、同じparent filesystemの一時directoryへcopyする。main DBと必須WALのcopyが成功した後、snapshot metadataを`rollback-ready.json`へ書き、rollback directoryへ公開する。

`rollback-ready.json`は少なくとも以下を保持するimmutable dataとする。

- format version（初期値1）。
- main DB file名。
- WALをsnapshotへ含めたかを示すboolean。

公開後の`hasRollbackBackup()`はmain DBの存在だけでなく、manifest parse成功、対応format version、main DB存在、`walIncluded=true`の場合のWAL存在をすべて確認する。partial directoryやmanifest不整合は完成snapshotとして扱わない。

一時directoryからrollback directoryへのrenameが既存directory構成上安全に行えない場合は、必須fileを最終directoryの一時名へcopyしてrenameし、`rollback-ready.json`を最後にpublishする。同一filesystem内のrenameと「ready manifestを最後に書く」順序は維持する。

### 3. `APPLYING`と`ROLLBACK_READY`を分離する

`RestoreStatus`へ`ROLLBACK_READY`を追加し、新規restoreは次の順序でmarkerを更新する。

```text
PREPARED
  -> APPLYING          rollback snapshot作成中、swap未開始
  -> ROLLBACK_READY    snapshot完成または元DBなし、swap開始可能
  -> DB_SWAPPED
  -> MIGRATION_PENDING
```

`applyRestore()`はlive DBの存在を先に判定し、`hadExistingLiveDb`を含む`APPLYING` markerを永続化する。元DBがある場合は完成snapshotをpublishしてから、元DBがない場合はbackup不要を確認してから`ROLLBACK_READY`へ進む。`replaceDbFile()`は`ROLLBACK_READY` marker書き込み後だけ呼ぶ。

`PendingRestoreMarker.hadExistingLiveDb`は`Boolean? = null`とする。`true/false`は新flowで明示した値、`null`はfieldを持たない旧markerとして区別する。missing fieldを`false`へ暗黙変換しない。

### 4. Stale recoveryはphaseとsnapshot完成状態で決定する

- stale `APPLYING`かつ`hadExistingLiveDb=true`: swap未開始なのでlive DBを保持し、partial snapshotをcleanupしてFAILEDを記録する。
- stale `APPLYING`かつ`hadExistingLiveDb=false`: 元DBなしでswap未開始なのでpartial fileをcleanupしてFAILEDを記録する。
- stale `ROLLBACK_READY`かつ`hadExistingLiveDb=true`: manifestで完成snapshotを再検証し、snapshotからrollbackする。snapshotが不完全ならlive DBとpending filesを保持してFAILEDを記録する。
- stale `ROLLBACK_READY`かつ`hadExistingLiveDb=false`: fresh-install swapが開始済みの可能性があるため、live main/WAL/SHMをcleanupしてFAILEDを記録する。
- stale `DB_SWAPPED`以降かつ元DBあり: 完成snapshotだけをrollbackに使用する。不完全ならlive DBとpending filesを保持してFAILEDを記録する。
- `hadExistingLiveDb=null`またはready manifestを持たない旧形式で状態が曖昧な場合: main DBの存在だけからrollback可否を推測しない。live DB、live WAL、rollback filesを上書き・削除せず、manual recovery可能なfailure detailをmarker/result/logへ記録する。

旧markerの曖昧経路ではavailabilityよりdata preservationを優先する。

### 5. WAL restore failureをrollback失敗にする

`restoreRollbackBackup()`は最初にready manifestをparseして必須file setを確定する。live WAL/SHMをcleanupし、main DBを復元した後、`walIncluded=true`ならbackup WALを必ず復元する。

mainまたは必須WALのrestoreに失敗した場合は`false`を返す。callerの`rollbackAndFail()`は既存contractどおりpending/rollback directoryを削除せず、failure detailを保持する。必須WALのrestore failureをlogだけで成功扱いしてはならない。

rollback mainだけが復元された後にWAL復元が失敗する可能性があるため、rollback sourceは保持する。manual recoveryでmainとWALを再配置できることを優先する。

### 6. Snapshot metadataはrestore markerと分離する

`PendingRestoreMarker`はstate machineと元DB有無を保持し、`rollback-ready.json`はrollback file setの完全性を保持する。1つのmarkerへ混在させない。process death時は両方を照合して安全なactionを決定する。

## Implementation Contract

1. `PendingRestoreDbSwapper.kt`へrollback snapshot manifestのtype/adapterまたは明示的serializerを追加する場合、typeへKDocを付けannotationより前に置く。
2. `createRollbackBackup()`は開始時に前回のpartial temp snapshotだけをcleanupし、完成snapshotを上書きする前にmain DBを一時領域へcopyする。
3. source WALの存在とlengthを判定し、非空ならcopy成功を必須とする。copy failure時はready manifestを書かずerror detailを返す。
4. `-shm`をrollback directoryへcopyしない。
5. `rollback-ready.json`はmainと必須WALのcopy完了後にだけpublishする。
6. `hasRollbackBackup()`はready manifestとmanifest記載fileを検証し、main file単独では`true`を返さない。
7. `PendingRestoreMarker`へnullableな`hadExistingLiveDb`を追加し、新規`APPLYING` markerでは必ずnon-null値を書く。
8. `RestoreStatus.ROLLBACK_READY`を追加し、`replaceDbFile()`より前に永続化する。
9. stale `APPLYING`では`replaceDbFile()`済みと推測せず、新形式では元live DBをrollback sourceで上書きしない。
10. 旧markerまたは不完全snapshotの曖昧経路ではlive DBとpending rollback filesを削除しない。
11. `restoreRollbackBackup()`はmanifestで`walIncluded=true`ならWAL restore failure時に`false`を返す。
12. `rollbackAndFail()`は`false`時にpending snapshotを保持する既存contractを維持する。
13. 新規class/enum/data class/interfaceにはKDoc、非自明なfile I/O/state transition関数には短いKDoc、30行を超える関数にはsection headerを付ける。

## Error Cases

- rollback temp directory作成失敗: backup failureとしてswapを開始しない。
- main DB copy失敗: ready manifestをpublishせずswapを開始しない。
- 非空WAL copy失敗: ready manifestをpublishせずswapを開始しない。
- ready manifest write/rename失敗: snapshot未完成としてswapを開始しない。
- ready manifest parse/version/file検証失敗: automatic rollbackへ使用しない。
- main DB restore失敗: rollback failureとしてsnapshotを保持する。
- 必須WAL restore失敗: rollback failureとしてsnapshotを保持する。
- process death during backup: statusは`APPLYING`、ready manifestなし。live DBを保持する。
- process death after `ROLLBACK_READY`: snapshotを再検証してrollbackまたはfresh-install cleanupする。
- 旧markerでphase不明: destructive actionを行わずfailureを記録する。

## Compatibility

- `hadExistingLiveDb`はnullable defaultにし、fieldなし旧JSONをparse可能にする。
- `ROLLBACK_READY`追加後も既存enum文字列を維持する。
- ready manifestを持たない旧rollback directoryは新形式の完成snapshotとみなさない。
- 旧形式の`APPLYING`/`DB_SWAPPED` recoveryは自動rollbackよりfile preservationを優先する。
- ZIP backup、pending staged DB、result JSONの既存fieldは変更しない。

## Testing Strategy

- `PendingRestoreDbSwapperTest`でmainのみ、main+空WAL、main+非空WALの成功境界を検証する。
- 非空WALのsource/copy失敗をcontrolled seamで発生させ、error、ready manifest未作成、swap未開始を検証する。
- mainだけ存在しready manifestがないpartial snapshotを`hasRollbackBackup()`が拒否することを検証する。
- manifestのinvalid JSON、unknown version、`walIncluded=true`かつWAL欠落を拒否する。
- SHMがbackup/restoreされないことを検証する。
- main restore失敗、必須WAL restore失敗で`false`となり、rollback sourceが残ることを検証する。
- `PendingRestoreApplierTest`で新規markerへ`hadExistingLiveDb`が保存され、`ROLLBACK_READY`がswap前に書かれる順序を検証する。
- process deathを模したstale `APPLYING`、`ROLLBACK_READY`、`DB_SWAPPED`の各stateを、元DBあり/なしで検証する。
- 旧marker（`hadExistingLiveDb=null`）と旧rollback directoryでlive DB/pending filesを削除しないことを検証する。
- 既存migration pending、rollback required、fresh install、successful restore testsを回帰実行する。
- Android CIでunit testsとCI APK buildを実行し、実行前に記録したHEADとworkflow `headSha`の一致を確認する。

## Risks / Trade-offs

- [非空WAL copyのfatal化でrestore開始が失敗しやすくなる] → 不完全snapshotでdata lossするよりrestoreを中止し、元live DBを保持する。
- [ready manifestとstateが増えて実装が複雑になる] → file-set completenessとswap phaseを別々に表現し、state別testで固定する。
- [旧markerではswap前後を完全に判別できない] → automatic recoveryを中止して両方のfile setを保持する。
- [main copy中にexternal processがDBへwriteする可能性] → 現行single-process cold-start invariantを維持する。multi-process対応は別changeとする。
- [WAL restore失敗時にlive mainだけが置換済みになる] → rollback sourceを削除せずmanual retry/recoveryを可能にする。

## Migration Plan

1. nullable `hadExistingLiveDb`と`ROLLBACK_READY`を追加し、旧marker parse testsを先に追加する。
2. rollback-ready manifestと完成判定を追加する。
3. `createRollbackBackup()`をtemp snapshot + mandatory WALへ変更する。
4. `restoreRollbackBackup()`をmanifest-driven mandatory WAL restoreへ変更する。
5. `PendingRestoreApplier`のmarker順序とstale recoveryを更新する。
6. state/failure/process-death testsを追加し、既存restore testsを回帰実行する。

問題が発生した場合も旧best-effort WAL behaviorへ戻さず、new snapshotを使用しないversionへrollbackする。既に作成された新形式snapshotはready manifestを持つため、旧versionが誤ってmain-only backupとして扱わないようrelease sequencingを管理する。

## Open Questions

なし。snapshot manifestの具体的なserializationは既存Moshi/file-store patternを再利用し、implementation開始時に既存helperを確認して選択する。
