## 1. Test seamと現行期待値の整理

- [x] 1.1 `app/src/test/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplierTest.kt`の`FakePendingRestoreFileStore`へ、marker read/write、result write、cleanupのcallと例外を個別観測・注入できる最小のfieldを追加し、既存testがcompileすることを確認する。
- [x] 1.2 同testのDB swapper/DataStore reflector fakeで、live DB replace成功直後、`DB_SWAPPED`確定後、rollback再試行中のthrow pointを決定的に発生できることを確認し、不足する場合だけfakeへthrow injectionを追加する。
- [x] 1.3 既存`unexpectedException_doesNotEscapeAndWritesFailureResult`の期待値を、`DB_SWAPPED`が`FAILED`へ変わらずfailure resultのみ記録され、`cleanupPending()`が呼ばれない契約へ変更する。

## 2. Status-aware exception記録

- [x] 2.1 `app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt`の`recordStartupRestoreFailureOnIo()`へexhaustiveな`RestoreStatus`分類を実装し、`ROLLBACK_READY`、`DB_SWAPPED`、`MIGRATION_PENDING`、`ROLLBACK_REQUIRED`、`COMPLETED`ではmarker writeを行わないことをcode reviewで確認する。
- [x] 2.2 同handlerで`PREPARED`、`APPLYING`だけを既存failure reason付き`FAILED`へ更新し、既存`FAILED`は再terminalizeせず、全statusで既存failure result writeをbest-effort維持する。
- [x] 2.3 同handlerおよび追加helperにrepository規約どおりKDoc、guard/fallback comment、必要なsection commentを追加し、`RestoreStatus`、marker/result schema、startup ordering、recovery methodsに変更がないことをdiffで確認する。

## 3. State boundary回帰test

- [x] 3.1 `ROLLBACK_READY`確定後にlive DB replaceが成功し、`DB_SWAPPED` marker writeがthrowするtestを追加して、atomic storeの直前の`ROLLBACK_READY`、DB/DataStore rollback artifact、stagingが保持され、generic handlerによるmarker再writeとcleanup/artifact削除が0回であることを検証する。
- [x] 3.2 `DB_SWAPPED`でDataStore reflectがthrowするtestを追加し、markerが`FAILED`にならず、failure resultが記録され、rollback artifactが残ることを検証する。
- [x] 3.3 必ず3.1の「DB置換成功かつdurable markerは`ROLLBACK_READY`」状態から二回目の`runIfNeeded()`を実行し、既存`recoverFromRollbackReady()`がDBとDataStoreをrestore前の同一generationへrollbackし、restore済みDBと旧/部分DataStoreを受理せず、成功後だけfailure result確定とpending cleanupを行うことを検証する。
- [x] 3.4 `MIGRATION_PENDING`が最後の確定markerであるfixtureでuser version取得時にthrowさせ、marker再write/cleanupが0回でartifactが残ることを検証する。二回目はcurrent schema versionを返してvalidation成功、`COMPLETED`、success result、cleanupへ進むことを検証する。
- [x] 3.5 `ROLLBACK_REQUIRED` rollback中のthrow testを追加し、marker再write/cleanupが0回で全再試行artifactが残ることを検証する。次回起動でrollback retryが成功し、完了後だけcleanupすることを確認する。
- [x] 3.6 `COMPLETED` result writeまたはcleanup中のthrow testを追加し、marker再writeとgeneric cleanupが0回、`COMPLETED`保持、次回起動のsuccess result/cleanup retry、rollback未実行を検証する。
- [x] 3.7 `PREPARED`または`APPLYING`のpre-swap throw testで`FAILED`記録とDB replace未実行を検証し、安全なterminal failure挙動を回帰確認する。

## 4. 診断I/O failure test

- [x] 4.1 Recoverable markerのread failureを注入し、marker write、cleanup、artifact削除が0回で、二次例外が`runIfNeeded()`からescapeしないことを検証する。
- [x] 4.2 `PREPARED`または`APPLYING`から`FAILED`へのmarker write failureを注入し、atomic storeが直前のpre-swap markerを保持し、DB replaceが未実行で、二次例外がescapeしないことを検証する。
- [x] 4.3 Recoverable marker保持後のfailure result write failureを注入し、marker write、cleanup、artifact削除が0回で、次回起動recoveryが妨げられないことを検証する。
- [x] 4.4 `ROLLBACK_READY`、`DB_SWAPPED`、`MIGRATION_PENDING`、`ROLLBACK_REQUIRED`、`COMPLETED`の各generic exception testでmarker write、cleanup、artifact削除がすべて0回であることをtable-driven assertionまたは各testの明示assertionで確認する。

## 5. Verificationとscope audit

- [x] 5.1 対象unit testを含むrepository unit test workflowを実行し、全test passをCI run URLまたはrun IDで記録する。
- [x] 5.2 Android build workflowを実行し、成功したexact commit SHAとCI run URLまたはrun IDを記録する。
- [x] 5.3 最終diffを`PendingRestoreApplier.kt`、`PendingRestoreApplierTest.kt`および直接必要なtest fakeに限定できているか監査する。`RestoreStatus`、永続schema、既存recovery method、UI、queued Codex findingへ変更が及ぶ場合はconditional auditを開始し、本changeの承認済みscopeへ戻すか追加承認を得る。
