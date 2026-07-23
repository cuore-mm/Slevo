## Context

`PendingRestoreApplier.quarantineAndFail()`は、rollback snapshotがない状態でmigration recoveryを継続できない場合に、live DB setを`File(fileStore.pendingDir, "quarantine")`へ退避する。その後、FAILED markerとresultを書き、`PendingRestoreFileStore.cleanupPending()`を呼ぶ。real storeのcleanupは`pendingDir.deleteRecursively()`であるため、同じtreeに作ったquarantineも削除される。

このpathはDB読取不能、strict validation失敗、pre-migration validation失敗、unexpected intermediate version、`ROLLBACK_REQUIRED`なのにrollback backupがない場合から共通利用される。既存の`PendingRestoreApplierTest`はfake storeのcleanupが実ファイルを削除しないため、result文字列しか検証できずartifact消失を検出できない。

一方、`pending-restore-result`は`pendingDir`外にあり、pending cleanup後も保持される。quarantineも同様に一時stagingと異なるownership/lifecycleを持たせる必要がある。

## Goals / Non-Goals

**Goals:**

- rollback不能なfailure pathでlive DB本体と存在する`-wal`/`-shm`をpending staging外へ保持する。
- `cleanupPending()`、FAILED pending処理、cold start、次回restore準備がquarantineを暗黙に削除しない構造にする。
- 同じinstallationで複数回failureが発生しても、過去のquarantineを上書きせずincident単位に保持する。
- failure resultが実在するincident directoryを報告し、保存失敗時には存在しないpathを成功扱いで報告しない。
- real filesystem testでdirectory ownershipとartifact survivalを回帰検証する。

**Non-Goals:**

- quarantine artifactをUIから閲覧、共有、復元、削除する機能は追加しない。
- 保存期間、世代数、容量上限による自動削除policyは導入しない。
- migration validation、Room schema、rollback snapshot作成、backup archive formatは変更しない。
- 既にbuggy versionで削除済みのartifactを復元しない。

## Decisions

### 1. quarantine rootをpending stagingのsiblingとして所有する

`PendingRestoreFileStore`にquarantine保存先を表すcontractを追加し、`RealPendingRestoreFileStore`ではapplication `filesDir`直下の`pending-restore-quarantine`をrootとする。`PendingRestoreApplier`は`pendingDir`からpathを組み立てず、file store経由でincident directoryを取得する。

```text
filesDir/
├── pending-restore/                 # 一時staging、cleanupPendingの所有範囲
├── pending-restore-result/          # UI/診断result
└── pending-restore-quarantine/      # durable recovery root
    └── <unique-incident>/           # 1 failureのDB set
```

`cleanupPending()`の意味は変えず、`pendingDir`だけを再帰削除する。quarantineを同じtreeに残してskip listを設ける案は、cleanup implementationへartifact semanticsを混在させ、別の`pendingDir.deleteRecursively()`経路で再発するため採用しない。

### 2. failureごとに一意なincident directoryを作る

固定`quarantineDir`へ毎回上書きするのではなく、file storeが`pending-restore-quarantine`配下に衝突しないincident directoryを作成する。実装時は既存のAndroid API levelで利用可能なJDK/Kotlin APIを確認し、UUID等の衝突しない名前を用いる。directory生成は`PendingRestoreFileStore`の責務とし、`PendingRestoreApplier`がtimestamp文字列やpath規則を直接持たない。

新しいfailureは新しいincidentへ保存し、既存incidentを削除・再利用しない。自動retentionを設けないtrade-offは、唯一のrecovery artifactを安全側で保持するため受け入れる。

### 3. DB setを同一incidentへ保存し、main DBの実在を成功条件にする

`PendingRestoreApplier.quarantineAndFail()`は現在のDB swapper/file move contractを維持しつつ、main DBと存在する`-wal`/`-shm`を同一incident directoryへ移動またはfallback copyする。sidecarが元々存在しないことはfailureにしない。

quarantine成功としてpathをresultへ記録する条件は、処理後にincident内のmain DBが存在することである。移動・copy途中で例外が発生した場合は、作成済みのincidentやpartial filesを削除せず、実際の保存結果をfailure reasonへ反映する。存在確認できないincidentを「quarantined」と報告してはならない。

### 4. durable artifactとpending state cleanupを分離する

quarantine成功または保存試行後も、FAILED marker/result作成と`cleanupPending()`という既存state machine終端は維持する。ただしcleanup対象は`pendingDir`のみであり、quarantine root/incidentには到達しない。

`PendingRestoreManager.handleExistingPending()`のFAILED branch、次回restore準備、`PendingRestoreCompletionChecker`のsuccess cleanupなど、既存のpending cleanup call siteからquarantine cleanupを追加しない。cold startでFAILED markerが既にcleanup済みの場合も、独立directoryのartifactを保持する。

明示的なartifact管理UIがない本changeではquarantineを自動削除しない。将来retentionを導入する場合は、別changeでユーザー可視性と復旧可能性を含めて定義する。

### 5. result JSON schemaは変更しない

既存のfree-form `failureReason`に実在するincident directoryのabsolute pathを含める。marker/result data classへ新fieldを追加せず、旧versionとのserialization compatibilityを維持する。保存失敗時は成功を示す文言ではなく、quarantine attemptが完了しなかったことを記録する。

### 6. real filesystem testをcontractの中心にする

fake storeのevent assertionは処理順のtestとして残すが、artifact survivalはtemporary application/files directoryを使うrealまたはfilesystem-backed storeで検証する。少なくとも次の境界を直接assertする。

- quarantine incidentが`pendingDir`の子孫ではない。
- main DBと元々存在したsidecarsがincident内にある。
- `cleanupPending()`後に`pendingDir`は消え、incidentは残る。
- failure result中のpathが実在するincidentと一致する。
- FAILED handling相当の再cleanup、applier再実行、次回restore preparation後もincidentが残る。
- 2回のfailureで異なるincidentが作られ、先のartifactが上書きされない。

## Implementation Contract

1. `PendingRestoreFileStore.kt`のinterfaceと`RealPendingRestoreFileStore`へ、quarantine root配下に一意なincident directoryを作成するAPIを追加する。test fakeも同じcontractを実装する。
2. real implementationのrootは`appContext.filesDir/pending-restore-quarantine`とし、`pendingDir`配下には置かない。public UI/APIやHilt constructor signatureは不要に変更しない。
3. `PendingRestoreApplier.quarantineAndFail()`内の`File(fileStore.pendingDir, "quarantine")`を廃止し、file storeが作成したincident directoryを使用する。
4. main DB、`-wal`、`-shm`は同一incidentへ保存する。既存のmove失敗時copy fallback semanticsを保ち、保存元削除の成否を含む既存error handlingを弱めない。
5. main DBの保存後実在を確認してからfailure reasonへquarantine pathを含める。partial artifactは追加cleanupで消さない。
6. `cleanupPending()`、`cleanupPendingDir()`、FAILED handlingへquarantine削除を追加しない。quarantine rootをpending lifecycleから独立させる。
7. existing result/marker JSON model、Room schema、backup/restore archive modelを変更しない。
8. typeとnon-trivial functionにはrepositoryのKDoc規約に従う。長いfunctionを変更する場合は処理sectionをコメントで区切る。
9. filesystem testはsleepや文字列確認だけに依存せず、実ファイルのcanonical path、存在、内容、複数incidentの非同一性をassertする。

## Error Cases

- quarantine root/incidentを作成できない: failure resultへ作成失敗を記録し、存在しない成功pathを報告しない。
- main DBのmoveとfallback copyが両方失敗する: partial incidentを保持し、main DBが保存されたとは報告しない。
- WAL/SHMだけ保存に失敗する: main DB artifactを削除せず、sidecar failureをreason/logへ反映する既存semanticsを維持する。
- result書込に失敗する: quarantine artifactをrollbackまたは削除しない。
- pending cleanupに失敗する: quarantine artifactの保持には影響させず、既存warning behaviorを維持する。
- incident name衝突: 既存directoryを上書きせず、新しい一意directory生成または明示的failureとする。

## Compatibility

- marker/result JSON schemaを変更しないため、既存serialized dataをそのまま読める。
- 旧versionで既に削除された`pendingDir/quarantine`は回収不能でありmigration対象にしない。
- fixed versionから旧versionへdowngradeしても、旧versionは新quarantine rootを認識せず削除しない。orphan化はし得るがartifact消失より安全である。
- 新directory追加によるstorage増加を許容する。retentionは別changeで扱う。

## Testing Strategy

- `PendingRestoreFileStoreTest`: quarantine rootがpending root外であること、一意incident作成、`cleanupPending()`後のsurvivalを実filesystemで検証する。
- `PendingRestoreApplierTest`または専用filesystem test: strict validation失敗・rollbackなしの代表pathでDB setとreported pathを検証する。
- rollbackなしで`quarantineAndFail()`へ到達する他のcall pathについて、既存parameterized/fake testsを維持し、共通helper利用を確認する。
- manager/applier再実行test: FAILED cleanup、cold-start相当の`runIfNeeded()`、次回restore準備が既存incidentを削除しないことを検証する。
- 複数incident test: 2回のquarantineで1件目と2件目のmain DB内容がそれぞれ保持されることを確認する。
- Android CIで全unit testsとAPK buildを実行し、workflow `headSha`が検証対象commitと一致することを確認する。

## Risks / Trade-offs

- [自動削除しないためinternal storageが増える] → failure時だけ生成されるartifactであり、安全性を優先する。retention/UI削除は別changeへ分離する。
- [internal files pathは一般ユーザーが直接参照しにくい] → 本changeはartifact消失防止を対象とし、export/recovery UIはNon-Goalとする。
- [move/copy途中のpartial DB setが残る] → incidentを削除せず、main DB実在とsidecar結果を正確に報告する。完全なartifactを偽って報告するより診断可能性を優先する。
- [interface追加がtest fakeへ波及する] → compilerで全implementationを列挙し、各fakeにfilesystem-backedまたは明示的test directory contractを実装する。

## Migration Plan

1. quarantine directory contractとreal/fake implementationsを追加する。
2. `quarantineAndFail()`を専用incident directoryへ切り替える。
3. filesystem regression testsと既存state-machine testsを実行する。
4. schema/data migrationなしでreleaseする。rollback時は新rootが残るため、artifactを削除せず旧codeへ戻せる。

## Open Questions

- なし。quarantineの閲覧・export・retentionは本changeの外で個別に設計する。
