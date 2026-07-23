## 1. Existing filesystem ownershipの確認

- [x] 1.1 `PendingRestoreFileStore.kt`のinterface、`RealPendingRestoreFileStore`、全test fakeを検索し、実装classとconstructor call siteを列挙する。完了条件: quarantine API追加時に更新すべきimplementationが漏れなく特定されている。
- [x] 1.2 `PendingRestoreApplier.quarantineAndFail()`と全call siteを確認し、main DB・`-wal`・`-shm`の現在のmove/copy fallbackとfailure reason生成順序を記録する。完了条件: 既存error semanticsを維持する箇所がimplementation commentまたはtest名で明確になっている。
- [x] 1.3 `cleanupPending()`、`PendingRestoreManager.cleanupPendingDir()`、FAILED handling、`PendingRestoreCompletionChecker`のcleanup call pathを確認する。完了条件: quarantine rootを削除し得る直接・間接pathがないことを修正後に検証できる一覧がある。

## 2. Quarantine filesystem contract

- [x] 2.1 `PendingRestoreFileStore.kt`へquarantine root配下に一意なincident directoryを作成するAPIを追加する。完了条件: API名とKDocがpending staging外、既存incident非上書き、1 failure 1 directoryのcontractを示す。
- [x] 2.2 `RealPendingRestoreFileStore`でrootを`appContext.filesDir/pending-restore-quarantine`へ固定する。完了条件: canonical pathが`pendingDir`の子孫ではなく、result/pending directory名とも衝突しない。
- [x] 2.3 incident directory名をUUID等の衝突しない値で生成し、既存directoryを再利用・削除しない実装を追加する。完了条件: 連続2回の生成結果が異なり、両directoryが同時に存在する。
- [x] 2.4 incident root/directory作成失敗をcallerへ伝播させる。完了条件: 既存directory上書きや存在しないdirectoryの成功returnがない。
- [x] 2.5 `PendingRestoreFileStore`の全fake/test implementationへ新contractを追加する。完了条件: production compileとtest compileで未実装classが残らない。
- [x] 2.6 新規・変更type/functionへrepository規約に沿うKDocを追加する。完了条件: KDocがannotationより上にあり、ownershipとlifecycleを説明している。

## 3. Quarantine保存処理

- [x] 3.1 `PendingRestoreApplier.quarantineAndFail()`から`File(fileStore.pendingDir, "quarantine")`を削除し、file storeが作成するincident directoryを使用する。完了条件: application code内に`pendingDir/quarantine`を生成するpathが残らない。
- [x] 3.2 main DB、存在する`-wal`、存在する`-shm`のdestinationを同一incident directoryへ組み立てる。完了条件: source basenameとsidecar suffixが維持される。
- [x] 3.3 既存のmove失敗時copy fallbackとsource cleanup semanticsを維持してDB setを保存する。完了条件: move成功pathとcopy fallback pathの両方でmain DBがincident内に存在する。
- [x] 3.4 sidecar欠損を許容し、存在するsidecarの保存失敗は既存reason/logへ反映する。完了条件: WAL/SHMがない場合もmain DB quarantineが成功し、存在するsidecarを黙って失わない。
- [x] 3.5 main DBの保存後実在を確認してからquarantine成功pathを`failureReason`へ含める。完了条件: result内の成功pathを`File`として確認するとmain DBを含むincidentが実在する。
- [x] 3.6 directory作成またはmain DB保存に失敗した場合、存在しないpathを「quarantined」と報告しないerror branchを追加する。完了条件: failure-injection testが未完了保存を正確に報告する。
- [x] 3.7 partial incident、main DB artifact、result書込済みartifactを後続error handlingで削除しない。完了条件: sidecar/result/cleanup failureを注入しても作成済みincidentが残る。

## 4. Pending lifecycleからの分離

- [x] 4.1 `RealPendingRestoreFileStore.cleanupPending()`の削除範囲を確認し、`pendingDir`だけを対象としたまま維持する。完了条件: quarantine rootを参照・削除するcodeが追加されていない。
- [x] 4.2 `PendingRestoreManager`のFAILED branchと次回restore準備処理を確認し、quarantine cleanupを行わないことを維持する。完了条件: FAILED pending cleanup後も既存incidentが存在するfilesystem testが成功する。
- [x] 4.3 `PendingRestoreCompletionChecker`を含むsuccess cleanup pathを確認し、quarantine rootをpending cleanupへ統合しない。完了条件: success cleanup後も事前作成したincidentが残るtestまたは対象範囲assertionが成功する。
- [x] 4.4 marker/result JSON data classへfield追加がないことを確認する。完了条件: serialization fixtureまたは既存compatibility testsが変更なしで成功する。

## 5. FileStore filesystem tests

- [x] 5.1 `PendingRestoreFileStoreTest.kt`にtemporary files directoryを使うquarantine root ownership testを追加する。完了条件: rootのcanonical pathが`pendingDir`外かつapplication files directory内である。
- [x] 5.2 一意incident生成testを追加する。完了条件: 2件のpathが異なり、1件目を含む既存contentsが2件目生成後も不変である。
- [x] 5.3 `cleanupPending()` survival testを追加する。完了条件: pending marker/stagingは削除され、incident内のmain DB、WAL、SHMは残る。
- [x] 5.4 directory生成failure/collision testを追加する。完了条件: 既存incidentを上書きせず、callerがfailureを観測できる。

## 6. Applier quarantine regression tests

- [x] 6.1 `PendingRestoreApplierTest.kt`または専用filesystem test fixtureをreal delete semanticsを持つtemporary directoryで構築する。完了条件: fake `cleanupPending()` no-opに依存せずartifact existenceをassertできる。
- [x] 6.2 strict migration validation失敗・rollbackなしのtestを追加する。完了条件: FAILED result後にpending directoryが消え、main DBを含むincidentが残る。
- [x] 6.3 DB本体、WAL、SHMが存在するtestを追加する。完了条件: 3 filesの内容が保存前と一致し、同一incidentに格納される。
- [x] 6.4 sidecarなしのtestを追加する。完了条件: main DBが保持され、欠損sidecarがquarantine全体の失敗にならない。
- [x] 6.5 failure result path整合性testを追加する。完了条件: reasonから検証対象pathを取得し、そのdirectoryとmain DBが実在する。
- [x] 6.6 incident作成失敗およびmain DB move/copy失敗testを追加する。完了条件: 存在しない成功pathを報告せず、元のfailure status/result contractを維持する。
- [x] 6.7 result書込またはpending cleanup failure後のsurvival testを追加する。完了条件: 先に保存されたincidentが削除されない。

## 7. Lifecycleと複数incident tests

- [x] 7.1 quarantine failure後にcold-start相当で`runIfNeeded()`を再実行するtestを追加する。完了条件: 再実行前後でincidentとmain DB内容が一致する。
- [x] 7.2 FAILED pending handling後に新しいrestoreを準備するtestを追加する。完了条件: stagingは再初期化され、既存incidentは保持される。
- [x] 7.3 2回のquarantine failureを実行するtestを追加する。完了条件: 異なる2 incidentが存在し、それぞれのmain DB内容が上書きされていない。
- [x] 7.4 rollbackなしで`quarantineAndFail()`へ到達する既存のDB unreadable、pre-validation failure、unexpected version、`ROLLBACK_REQUIRED` testsを回帰実行する。完了条件: 全pathが共通のdurable quarantine処理とFAILED statusを使用する。

## 8. Documentationと最終検証

- [x] 8.1 `PendingRestoreFileStore.kt`と`PendingRestoreApplier.kt`のtype/function commentsを新しいownershipへ更新する。完了条件: pending staging、result、quarantine incidentの異なるlifecycleが説明されている。
- [x] 8.2 長さが約30行を超える変更functionをrepository規約のsection commentで分割する。完了条件: filesystem準備、DB set保存、result、pending cleanupの境界が読める。
- [x] 8.3 `openspec validate preserve-quarantined-database-artifact --strict`と`git diff --check`を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 8.4 Android CIで`PendingRestoreFileStoreTest`、`PendingRestoreApplierTest`を含む全unit testsとAPK buildを実行する。完了条件: workflow開始前HEADとrunの`headSha`が一致し、全stepが成功する。
- [x] 8.5 最終diffを確認する。完了条件: pending restore quarantine保存と関連tests/OpenSpecだけが変更され、Room schema、archive format、UI、dependency変更が混在しない。
- [x] 8.6 implementationとtask completionをcommitしてremote branchへpushする。完了条件: push成功後にworking treeがcleanである。
