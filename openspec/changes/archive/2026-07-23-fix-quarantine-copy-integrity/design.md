## Context

`PendingRestoreApplier.quarantineAndFail()`はrollback不能なinvalid live DB setを専用incident directoryへ退避し、FAILED marker/resultを書いて`cleanupPending()`する。`preserveQuarantineFile()`は`renameTo()`失敗時に`copyTo(overwrite = true)`と`source.delete()`を実行するが、deleteの戻り値を無視し、destinationが存在するだけで成功を返す。

Java `File.delete()`は削除成功時だけ`true`を返し、Kotlin `File.copyTo()`はsourceを削除しない。そのためrename失敗、copy成功、delete失敗ではsourceとdestinationが共存する。現行callerはこれをquarantine成功としてFAILEDへ終端しpending stateを削除するため、Room pathのinvalid DBを次回起動で再処理できない。

この処理はmain DB、`-wal`、`-shm`へ共通適用される。部分失敗後も自動再試行可能にするには、main DBをRoom pathから除去する前に全sidecarを処理し、先に移動済みのsourceをincident artifactからbest-effortで復元する必要がある。incident artifact自体は診断・手動復旧用として削除しない。

## Goals / Non-Goals

**Goals:**

- quarantine成功を、処理対象だった各fileについてdestination artifactが完全に作成され、sourceがRoom pathから消えた場合だけとする。
- rename失敗、copy成功、source delete失敗をquarantine未完了として検出する。
- 未完了時は元の`MIGRATION_PENDING`または`ROLLBACK_REQUIRED` marker、rollback/staging payload、partial incidentを保持し、次回cold startで再試行可能にする。
- 先に移動済みのfilesをsource pathへ復元し、main DBを最後に処理することで、部分失敗時にinvalid main DBがretry anchorとしてRoom pathに残るようにする。
- JVM unit testでrename/copy/delete結果を決定的に注入し、state transitionと実file内容を検証する。

**Non-Goals:**

- quarantine artifactのUI表示、共有、復元、削除、retentionを追加しない。
- Room schema、backup archive、marker/result JSON schema、status enumを変更しない。
- migration判定、rollback recovery、通常restore、既存incident lifecycleを変更しない。
- quarantine対象DBのSQLite妥当性を再検証しない。ここでのartifact integrityはfile copy/move完了、元size一致、実在、内容保持testで確認する。

## Decisions

### 1. quarantine file操作をpostconditionで判定する

`PendingRestoreApplier.preserveQuarantineFile()`は操作前のsource sizeを保持し、単純な`Boolean`ではなく、成功または失敗時のsource/destination状態を持つstructured resultを返す。次の両方を満たす場合だけ成功とする。

1. destinationが通常fileとして存在し、sizeが操作前source sizeと一致する。
2. sourceが存在しない。

rename成功時もpostconditionを確認する。rename失敗時はcopy完了後にsource deleteを実行し、deleteが`true`かつ上記postconditionを満たす場合だけ成功とする。deleteが`false`ならdestinationを消さず失敗を返す。失敗resultは操作前size、sourceの現在状態、destinationの現在状態、source復元要否を保持し、callerが現在失敗中のfileもrollback対象にできるようにする。

本changeでのartifact validityは、rename/copy APIが例外なく完了し、destinationがregular fileとして存在し、操作前source sizeと一致することである。hash/byte再比較をproductionへ追加する案はinvalid DB全体の再読込と新しい処理costを生むため採用しない。unit testではfixture bytes一致も確認するが、normativeなproduction postconditionはregular file実在とsize一致である。

### 2. file operationsを小さいinternal collaboratorへ分離する

`PendingRestoreApplier.kt`内または同じ`pending` packageに、rename、copy、delete、exists/isFile/lengthを委譲するinternal filesystem operations contractとproduction implementationを置く。public constructorはproduction implementationを使用し、`createForTest()`だけoverrideを受け取れるようにする。

`File`を継承・mockする案とpermission変更による実filesystem failure再現は、JVM/OS依存でdelete失敗を安定再現できないため採用しない。quarantine全体を1 lambdaで置換する案も、問題のあるpostcondition実装自体をtestしないため採用しない。

### 3. sidecarを先、main DBを最後に処理し、失敗時にsource setを復元する

`quarantineAndFail()`は処理開始時に存在したfilesを`-wal`、`-shm`、main DBの順で同一incidentへ保存し、各source/destination/操作前sizeを記録する。任意のfileが失敗したら後続fileを処理せず、失敗resultでsourceが消失している現在fileをrollback対象へ含め、その後に先行成功pairを逆順で続ける。destinationから元sourceへcopyし、sourceのregular-file実在と操作前size一致を確認する。復元時もdestinationは削除せず、partial incidentを保持する。

main DBを最後にするため、sidecar段階の失敗ではmain DBは未操作である。報告対象のcopy成功/delete失敗ではmain DB sourceが残る。rename成功後またはdelete成功後のpostcondition失敗でmain sourceが消えた場合も、destinationが存在すれば現在fileを最初に復元する。destination不在または復元後size不一致でsourceを再構成できない異常caseでは、成功を報告せずmarker/pending/incidentを保持し、同じcold-start recoveryを再試行可能な状態にするが、自動回復成功は保証せず既存manual-intervention resultを維持する。

sidecar失敗をwarning付きterminal successとしてcleanupする現行案は、「部分copy/delete失敗でpending stateを消さない」というintegrity invariantに反するため採用しない。

### 4. quarantine未完了時はmarkerを書き換えずpending cleanupを行わない

全対象fileの保存が完了した場合だけ、現行どおりmarkerをFAILEDへ更新し、failure resultを書き、`cleanupPending()`する。1 fileでも失敗した場合は次を行う。

- `marker.copy(status = FAILED, ...)`を書かず、disk上の元のretryable statusを維持する。
- 既存文言`quarantine failed: manual intervention required`を使うfailure result書込をbest-effortで試すが、incident pathをquarantine成功先として含めない。
- `cleanupPending()`を呼ばない。
- failure result書込例外はlocalでlogしてreturnし、outer unexpected-error handlerへ伝播させない。outer handlerがmarkerをFAILEDへ変えることを防ぐ。

新statusやincident path fieldをmarkerへ追加する案はserialization変更になり、この修正には不要なため採用しない。次回起動は保持された`MIGRATION_PENDING`または`ROLLBACK_REQUIRED`から既存flowを再実行し、新しいincidentを作る。過去のpartial incidentは上書き・削除しない。

### 5. UIと既存成功reportingを変更しない

新しいscreen、interaction、accessibility semantics、user-facing textは追加しない。完全成功時の`invalid DB quarantined to ...`と未完了時の既存`quarantine failed: manual intervention required`を維持する。変更点は内部state transitionとfilesystem integrityだけである。

## Implementation Contract

1. `PendingRestoreApplier.kt`の`preserveQuarantineFile()`から「source削除はbest effort」というcommentとdestination-only成功判定を削除する。
2. internal filesystem operations contractを追加し、productionでは`File.renameTo`、`File.copyTo(overwrite = true)`、`File.delete`、`File.exists/isFile/length`へ委譲する。全type/non-trivial functionへrepository規約どおりKDocを置く。
3. `PendingRestoreApplier` private constructorと`createForTest()`へoperations overrideを追加する。public constructorと既存call siteのbehavior/signatureは維持する。
4. `preserveQuarantineFile()`はrename pathとcopy/delete fallback pathの双方でdestination regular-file実在、操作前size一致、source不存在を確認する。copy後deleteが`false`なら必ず失敗とし、destinationを保持する。戻り値はsource/destinationのpostconditionと操作前sizeをcallerへ返すstructured resultにする。
5. `quarantineAndFail()`は開始時に存在する`-wal`、`-shm`、main DBをこの順で処理する。全pairを追跡し、最初の失敗で停止する。失敗fileのsourceが消えてdestinationが存在する場合は、その失敗pair自身もrollback対象に含める。
6. 部分失敗時は現在失敗pair、先行成功pairの順でdestinationからsourceへbest-effort copyし、source regular-file実在と操作前sizeを検証する。destination/incidentを削除しない。destination不在、復元後size不一致、copy例外はlogし、marker/pending stateを保持して成功を報告しない。
7. 部分失敗branchはFAILED markerを書かず、`cleanupPending()`を呼ばず、failure result書込例外をcatchして元markerを保持する。
8. 全対象file成功branchだけが既存FAILED marker、result、cleanup順序へ進む。Room schema、JSON model、status enum、他のrestore pathへ変更を加えない。
9. 約30行を超える変更functionはincident準備、file保存、partial rollback、result/state transitionのsection commentで分割する。

## Error Cases

- incident directory作成失敗: marker/pending payloadを保持し、成功pathを報告せずcleanupしない。
- rename失敗、copy例外: destination partial fileを削除せず、先行成功sourceを復元し、markerを保持する。
- rename失敗、copy成功、deleteが`false`: sourceとdestinationを保持した未完了として扱い、FAILED markerとcleanupを実行しない。
- renameが`true`でもdestination不在、size不一致、source残存: 未完了として扱う。sourceが消えてdestinationが存在する場合は現在失敗fileもdestinationから復元し、destination不在ならmarker/pending stateを保持してmanual intervention resultに留める。
- sidecar保存失敗: main DBを処理せず、先行sidecarを復元してpending stateを保持する。
- main DB保存失敗: main DB sourceをRoom pathに残し、先行sidecarを復元してpending stateを保持する。
- source復元失敗: destination artifactがあれば保持し、marker/pending payloadを保持して成功を報告しない。main DBを最後にすることでsidecar失敗時のmain DBは未操作にし、main DB自身のpostcondition失敗では現在失敗pairも復元対象にする。
- 未完了failure result書込失敗: exceptionをlocalで記録し、outer handlerによるFAILED遷移を発生させない。

## Compatibility

- marker/result JSON、Room、archive formatにfield/version変更はなく、既存serialized stateをそのまま読める。
- 完全成功pathのFAILED resultとcleanup contractは維持する。
- 修正版から旧版へrollbackしても新formatはなく、保持済みpending stateとpartial incidentsは既存pathとして残る。ただし旧版で再実行すると同じbugが再発し得るため、rollback後の自動retryは保証しない。
- quarantine失敗時にpartial incidentとpending payloadの両方が残るためstorage使用量は一時的に増えるが、data safetyを優先する。

## Testing Strategy

- `PendingRestoreApplierTest`: operations fakeでrename=`false`、copy成功、main source delete=`false`を注入し、destination内容保持、source残存、marker status不変、FAILED marker未書込、resultに成功pathなし、`cleanupPending`未呼出を検証する。
- 同testで次回`runIfNeeded()`時に操作を成功へ切り替え、保持markerから再試行され、sourceが消え、incident artifactが保持され、FAILED/result/cleanupへ一度だけ終端することを検証する。
- sidecar部分失敗test: sidecar成功後の次file失敗を注入し、main DB未操作、成功済みsidecar source復元、partial incident保持、pending cleanup未実行を検証する。
- postcondition failure tests: rename=`true`後のdestination missing/non-regular/size mismatch、copy/delete成功後のdestination size mismatchを注入し、現在失敗fileが復元対象になること、marker/pending保持、cleanup/成功reportなしを検証する。
- rollback failure test: 現在失敗fileまたは先行成功fileのsource復元copyを失敗させ、incidentとmarker/pending保持、cleanup/成功reportなし、次回再試行が行われることを検証する。
- rename成功pathとcopy/delete成功pathの回帰test: source不存在、destination regular file、size/content一致、既存terminal transitionを確認する。
- failure result書込例外test: partial failure後もmarkerがretryable statusのままでcleanupされないことを確認する。
- 既存quarantine filesystem、rollbackなしmigration failure、`ROLLBACK_REQUIRED` testsを回帰実行する。
- Android CIで全unit testsとAPK buildを実行し、run `headSha`が検証対象commitと一致することを確認する。

## Risks / Trade-offs

- [source復元copyも失敗し得る] → main DBを最後に処理し、sidecar失敗時はmain DBを未操作にする。main DB自身のpostcondition失敗では現在pairも復元対象にし、復元不能でもpartial incidentとpending markerを保持して成功を偽らない。
- [size一致だけではcryptographic integrityを証明しない] → copy API正常完了を必須とし、unit testではbytes一致をassertする。invalid SQLite DBへ追加validationやhash costを導入しない。
- [partial incidentが複数残る] → 既存の非上書き・非自動削除contractを維持し、data lossよりstorage増加を選ぶ。
- [constructor seam追加がtest call siteへ波及する] → `createForTest()`の新parameterにproduction相当defaultを設け、failure injection testだけoverrideする。

## Migration Plan

1. filesystem operations contractとproduction/test implementationsを追加する。
2. `preserveQuarantineFile()`のpostconditionと`quarantineAndFail()`の順序・partial rollback・state branchを変更する。
3. delete失敗、再試行、sidecar rollback、result write failure testsを追加する。
4. 既存quarantine testsと全unit/build CIを実行する。data/schema migrationなしでreleaseする。
5. rollback時はcodeだけを戻し、pending stateとincident artifactsを削除しない。

## Open Questions

- なし。
