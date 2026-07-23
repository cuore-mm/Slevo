## ADDED Requirements

### Requirement: Recoverable startup restore markerを想定外例外後も保持する
システムは起動時restoreが想定外例外で中断された場合、live DB置換、rollback、またはfinalizationの再開に必要な確定済みmarkerを`FAILED`その他のterminal stateへ変更してはならない（MUST NOT）。

#### Scenario: Live DB置換後かつDB_SWAPPED確定前に例外が発生する
- **WHEN** `ROLLBACK_READY`確定後にlive DB置換が成功し、`DB_SWAPPED` marker確定前に想定外例外が発生する
- **THEN** システムはmarkerを再書き込みせず`ROLLBACK_READY`とDB/DataStore rollback artifactを保持し、次回起動で両方をrestore前の同一generationへrollbackしてからのみartifactをcleanupする

#### Scenario: DB_SWAPPED中にDataStore反映が例外で中断する
- **WHEN** `DB_SWAPPED`確定後かつ`MIGRATION_PENDING`確定前に想定外例外が発生する
- **THEN** システムはmarkerを再書き込みせず`DB_SWAPPED`、staging、DB rollback snapshot、DataStore rollback snapshotを保持し、次回起動でrestore前の同一generationへrollbackする

#### Scenario: Rollback要求中に想定外例外が発生する
- **WHEN** `ROLLBACK_REQUIRED` markerからのrollback処理中に想定外例外が発生する
- **THEN** システムはmarkerを再書き込みせず`ROLLBACK_REQUIRED`と未完了rollbackの再試行に必要な全artifactを保持し、次回起動でrollbackを再試行する

### Requirement: Validationおよびfinalization markerの再開意味を保持する
システムはmigration validationまたは完了後cleanupを再開するmarkerを、汎用startup exception handlerによってterminal failureへ変更してはならない（MUST NOT）。

#### Scenario: MIGRATION_PENDING recoveryが例外で中断する
- **WHEN** `MIGRATION_PENDING`が最後にatomic確定されたmarkerであり、version取得またはvalidation中に想定外例外が発生する
- **THEN** システムはmarkerを再書き込みせず`MIGRATION_PENDING`とrollback artifactを保持し、次回起動でvalidationから再開する

#### Scenario: COMPLETED finalizationが例外で中断する
- **WHEN** `COMPLETED` markerのresult確定またはpending cleanup中に想定外例外が発生する
- **THEN** システムはmarkerを再書き込みせず`COMPLETED`を保持し、次回起動でsuccess result確定とcleanupを再試行する

#### Scenario: 後続marker確定後に例外が発生する
- **WHEN** recovery処理が元のmarkerより後続のstatusをatomic確定した後に想定外例外が発生する
- **THEN** システムは最新の確定markerを保持し、そのstatusに対応するrollbackまたはfinalizationを次回起動で再開する

### Requirement: 安全なpre-swap failureとrecovery-required failureを区別する
システムはdurable markerのstatusを用いて、live DB置換開始前のfailureだけをterminal failureとして記録し、`ROLLBACK_READY`以降のstateをterminalizeしてはならない（MUST NOT）。

#### Scenario: PREPAREDで想定外例外が発生する
- **WHEN** `PREPARED` markerからの処理が`ROLLBACK_READY`確定前に想定外例外で終了し、live DB置換が開始されていない
- **THEN** システムはfailure resultを記録してmarkerを`FAILED`へ更新でき、live DBを変更しない

#### Scenario: APPLYINGで想定外例外が発生する
- **WHEN** `APPLYING` markerからのsnapshot準備が`ROLLBACK_READY`確定前に想定外例外で終了し、live DB置換が開始されていない
- **THEN** システムはfailure resultを記録してmarkerを`FAILED`へ更新でき、live DBを変更しない

### Requirement: 診断記録失敗が回復状態を破壊しない
システムは想定外例外のmarker/result診断記録自体が失敗しても、直前にatomic確定されたmarkerとpending/rollback artifactを保持しなければならない（MUST）。

#### Scenario: Recovery marker読み取りが失敗する
- **WHEN** 想定外例外handlerによるrecoverable marker readがI/O failureで失敗する
- **THEN** システムはmarker write、cleanup、artifact削除を行わず、二次例外をstartup境界外へ伝播しない

#### Scenario: Pre-swap FAILED marker更新が失敗する
- **WHEN** `PREPARED`または`APPLYING`から`FAILED`へのatomic marker updateがI/O failureで失敗する
- **THEN** システムは直前のpre-swap markerを保持し、live DBを変更せず、二次例外をstartup境界外へ伝播しない

#### Scenario: Failure result書き込みが失敗する
- **WHEN** recoverable marker保持後のfailure result writeが失敗する
- **THEN** システムはrecoverable markerとartifactを保持し、次回起動のrollback、validation、またはfinalizationを妨げない
