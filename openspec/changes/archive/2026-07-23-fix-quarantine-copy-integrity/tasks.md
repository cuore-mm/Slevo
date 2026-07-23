## 1. Quarantine filesystem test seam

- [x] 1.1 `PendingRestoreApplier.kt`にrename、copy、delete、exists/isFile/lengthを表すinternal filesystem operations contractとproduction implementationを追加する。完了条件: production implementationが既存`File` APIへ直接委譲し、typeとnon-trivial functionにrepository規約どおりKDocがある。
- [x] 1.2 `PendingRestoreApplier`のprivate constructorと`createForTest()`へoperations overrideを追加し、public constructorはproduction implementationを使用する。完了条件: production call siteの変更が不要で、既存test factory callがdefault parameterによりcompileできる。
- [x] 1.3 `PendingRestoreApplierTest.kt`に操作順とfailure結果をfile単位で指定できるfake operationsを追加する。完了条件: rename失敗、copy成功、delete失敗に加え、rename/delete後のsource消失とdestination postcondition不一致をOS permissionやfile lockへ依存せず再現できる。

## 2. File保存postcondition

- [x] 2.1 `PendingRestoreApplier.preserveQuarantineFile()`で操作前source sizeを記録し、rename成功後もdestinationがregular file、size一致、source不存在であることを検証する。完了条件: destination-onlyの成功判定とbest-effort delete commentが残らない。
- [x] 2.2 rename失敗時のcopy/delete fallbackで、copy正常完了、delete=`true`、destination regular-file実在、size一致、source不存在をすべて成功条件にする。完了条件: delete=`false`ではdestinationが存在しても`false`を返し、destinationを削除しない。
- [x] 2.3 copy例外、destination不在/size不一致、source残存をすべて失敗としてlogし、操作前size、source/destination状態、source復元要否を持つstructured resultを返す。完了条件: 各branchがquarantine成功へfall throughせず、callerが現在失敗fileもrollback対象にできる。

## 3. Quarantine transactionとretry state

- [x] 3.1 `quarantineAndFail()`で処理開始時に存在するfilesを`-wal`、`-shm`、main DBの順に同一incidentへ保存し、各source/destination/操作前size/resultを追跡する。完了条件: main DBが常に最後で、最初の失敗後に後続fileを処理しない。
- [x] 3.2 任意file失敗時に、sourceが消えdestinationが存在する現在失敗pairを先頭に含め、その後に先行成功pairを逆順でdestinationからsourceへbest-effort copyするrollback helperを追加する。完了条件: source regular-file実在と操作前sizeを検証し、destination/incidentは削除せず、destination不在または復元失敗もlogされる。
- [x] 3.3 全対象fileが成功した場合だけ既存のFAILED marker、failure result、`cleanupPending()`順序へ進む。完了条件: 完全成功resultだけが`invalid DB quarantined to <incident>`を含み、cleanup後もincidentが残る。
- [x] 3.4 incident作成または任意file保存が失敗したbranchではFAILED markerを書かず、既存`quarantine failed: manual intervention required` resultをbest-effortで書き、`cleanupPending()`を呼ばずreturnする。完了条件: disk上の元marker statusとpending payloadが保持され、成功incident pathがresultに含まれない。
- [x] 3.5 未完了branchの`writeResult()`例外をlocalでcatchしてlogする。完了条件: exceptionが`runIfNeeded()`のouter unexpected-error handlerへ到達せず、markerがFAILEDへ変更されない。
- [x] 3.6 `quarantineAndFail()`が約30行を超える場合、incident準備、file保存、partial rollback、state/result transitionをsection commentまたは小さいKDoc付きhelperへ分離する。完了条件: repositoryのlong-function/comment規約を満たす。

## 4. Codex finding回帰tests

- [x] 4.1 `PendingRestoreApplierTest.kt`にmain DBのrename=`false`、copy成功、delete=`false` testを追加する。完了条件: sourceと同内容のdestinationが残り、sourceもRoom pathに残り、元marker status不変、FAILED marker未書込、成功path未報告、`cleanupPending`未呼出をassertする。
- [x] 4.2 4.1の保持stateからfake deleteを成功へ切り替えて`runIfNeeded()`を再実行するtestを追加する。完了条件: 再試行後にsourceが消え、new incident artifactの内容が一致し、FAILED marker/result/cleanupへ終端する。
- [x] 4.3 sidecar成功後の後続sidecar失敗testを追加する。完了条件: main DBは未操作、先行sidecar sourceは内容・size一致で復元、partial incidentは保持、marker/pending stateはcleanupされない。
- [x] 4.4 未完了failure result書込例外testを追加する。完了条件: result書込失敗後も元marker statusが保持され、`cleanupPending` eventがない。
- [x] 4.5 rename成功とrename失敗・copy/delete成功のtestsを追加または拡張する。完了条件: 両pathでdestination内容/size一致、source不存在、既存terminal FAILED/result/cleanup behaviorをassertする。
- [x] 4.6 rename=`true`後のdestination missing/non-regular/size mismatchと、copy/delete成功後のdestination size mismatch testsを追加する。完了条件: sourceが消えた現在失敗fileも復元対象となり、marker/pending/incident保持、cleanup/成功reportなしをassertする。
- [x] 4.7 現在失敗fileと先行成功fileそれぞれのsource復元copy failure testを追加する。完了条件: 復元不能でもmarker/pending/incidentを保持し、cleanup/成功reportなしで次回`runIfNeeded()`が再試行する。

## 5. Scopeと最終検証

- [x] 5.1 既存`PendingRestoreApplierTest`のquarantine creation failure、main DB move/copy failure、sidecar、result/cleanup failure、cold-start testsを回帰確認し、変更したintegrity contractと矛盾する期待値だけを更新する。完了条件: unrelated restore、rollback、migration判定のassertionを変更していない。
- [x] 5.2 最終diffで変更範囲を`PendingRestoreApplier.kt`、`PendingRestoreApplierTest.kt`、本OpenSpec changeに限定する。完了条件: Room schema、archive/marker/result format、status enum、UI/resource、accessibility semantics、user-facing text、dependency変更がない。
- [x] 5.3 `openspec validate fix-quarantine-copy-integrity --strict`と`git diff --check`を実行する。完了条件: strict validationとwhitespace checkが成功する。
- [x] 5.4 Android CIで`PendingRestoreApplierTest`を含む全unit testsとAPK buildを実行する。完了条件: workflow runの`headSha`が検証対象commitと一致し、全required stepが成功する。
- [x] 5.5 implementationとtask completionを日本語Conventional Commitでcommitしremote branchへpushする。完了条件: push成功後にworking treeがcleanである。
