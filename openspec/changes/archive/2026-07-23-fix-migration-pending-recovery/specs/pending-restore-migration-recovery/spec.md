## ADDED Requirements

### Requirement: MIGRATION_PENDING は Room migration 前の旧版 DB を保持する
システムは起動時に `MIGRATION_PENDING` marker を検出した場合、live DB の `user_version` が backup marker の databaseVersion と一致し、かつ current databaseVersion より古いとき、その DB を Room migration 待ちとして扱わなければならない（MUST）。この状態で migration 前 DB の pre-validation が成功する場合、システムは rollback、quarantine、pending cleanup、marker 更新を行ってはならない（MUST NOT）。

#### Scenario: Room migration 前の旧版 DB は rollback しない
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が `marker.databaseVersion` と一致し、`marker.databaseVersion < currentDbVersion` であり、pre-validation が成功する
- **THEN** システムは marker を `MIGRATION_PENDING` のまま維持し、rollback backup と pending restore directory を保持し、Room open 後の migration と completion checker に処理を委ねる

#### Scenario: Room migration 前の旧版 DB が pre-validation に失敗する
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が `marker.databaseVersion` と一致するが、pre-validation が失敗する
- **THEN** システムは restore 継続不能として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

### Requirement: MIGRATION_PENDING は Room migration 済み DB を strict validation する
システムは起動時に `MIGRATION_PENDING` marker を検出し、live DB の `user_version` が current databaseVersion 以上である場合、その DB を Room migration 済みとして扱い、current schema に対する strict validation を実行しなければならない（MUST）。

#### Scenario: Room migration 済み DB の strict validation が成功する
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が current databaseVersion 以上であり、strict validation が成功する
- **THEN** システムは同じ startup 内で durable な `COMPLETED` marker、`success=true` かつ `migrationCompleted=true` の result、pending cleanup の順に実行し、追加 restart を要求せずアプリを実行し続ける

#### Scenario: Room migration 済み DB の strict validation が失敗する
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が current databaseVersion 以上であり、strict validation が失敗する
- **THEN** システムは restore 継続不能として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

### Requirement: MIGRATION_PENDING は unreadable または想定外 version の DB を継続しない
システムは起動時に `MIGRATION_PENDING` marker を検出した場合、live DB の `user_version` を読み取れない、または `marker.databaseVersion` と current databaseVersion のどちらにも該当しない version を検出したとき、restore を待機継続してはならない（MUST NOT）。

#### Scenario: user_version を読み取れない
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` を読み取れない
- **THEN** システムは DB unreadable として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

#### Scenario: 想定外の中間 user_version
- **WHEN** 起動時 recovery が `MIGRATION_PENDING` marker を読み、live DB の `user_version` が `marker.databaseVersion` と一致せず、かつ current databaseVersion 未満である
- **THEN** システムは version mismatch として既存の rollback backup があれば rollback し、rollback backup がなければ quarantine/failure path を実行する

### Requirement: user_version 判定は Room に依存しない
システムは `MIGRATION_PENDING` の起動時復旧で live DB の `user_version` を判定するとき、Room database、DAO、Hilt graph に依存せず、SQLite file から `PRAGMA user_version` を読み取らなければならない（MUST）。

#### Scenario: 起動時 applier が Room 初期化前に user_version を読む
- **WHEN** `PendingRestoreApplier` が `SlevoApplication.onCreate()` の起動時処理として `MIGRATION_PENDING` recovery を実行する
- **THEN** システムは `AppDatabase` を生成せず、DAO を参照せず、SQLite file の pragma 読み取りだけで migration 前後を判定する

### Requirement: migration 完了 finalization は retryable な durable order を守る
システムは migration 済み DB の strict validation 成功後、`COMPLETED` marker、`migrationCompleted=true` の success result、pending cleanup の順序で finalization を行わなければならない（MUST）。marker と result は temporary write、sync、atomic replace、failure rollback を持つ durable write で publish し、失敗時に最後の valid JSON を保持しなければならない（MUST）。各段階が失敗した場合、システムは後続段階を開始してはならず（MUST NOT）、完了済み commit point、retryable state、および再試行に必要な残存 artifact を `FAILED` へ上書きせず保持しなければならない（MUST）。

#### Scenario: COMPLETED marker の書き込みに失敗する
- **WHEN** strict validation は成功するが `COMPLETED` marker の durable write が失敗する
- **THEN** システムは success result の書き込みと pending cleanup を実行せず、最後の valid `MIGRATION_PENDING` marker と pending artifact を次回起動で再試行可能な状態に保ち、同じ session の次 restore prepare を拒否する

#### Scenario: success result の書き込みに失敗する
- **WHEN** `COMPLETED` marker は durable に書かれたが、`success=true` かつ `migrationCompleted=true` の result write が失敗する
- **THEN** システムは partial result JSON を publish せず pending cleanup を実行せず、`COMPLETED` marker、最後の valid result、pending artifact を保持し、同じ session の次 restore prepare を拒否して次回起動で result write から再試行する

#### Scenario: pending cleanup が失敗する
- **WHEN** `COMPLETED` marker と `migrationCompleted=true` の success result は durable だが、pending payload の cleanup が全部または一部失敗する
- **THEN** システムは active `COMPLETED` marker と残存 artifact を保持し、同じ session の次 restore prepare を許可せず、次回 recovery で cleanup を再試行する

#### Scenario: pending cleanup が成功する
- **WHEN** `COMPLETED` marker と `migrationCompleted=true` の success result が durable であり、pending payload の cleanup が成功する
- **THEN** システムは staged DB、staged DataStore、rollback backup、DataStore rollback snapshot を除去した後に active marker を最後に除去し、pending directory 外の success result と quarantine incident は保持し、アプリを終了せず同じ session の次 restore prepare を許可する

#### Scenario: partial cleanup を再試行する
- **WHEN** 前回 cleanup で pending payload の一部だけが除去され、active `COMPLETED` marker と残存 payload がある
- **THEN** システムは既にない payload を成功扱いして残存 payload の cleanup を再開し、すべての payload が除去されるまで marker を保持する

### Requirement: same-startup completion は既存 success UI contract を維持する
システムは required restore restart の startup 内で finalization が成功した場合、既存の result consumer を通して既存 success Snackbar を表示可能にしなければならない（MUST）。システムはこの変更のために追加 restart を要求してはならず（MUST NOT）、Snackbar の text または layout を変更してはならない（MUST NOT）。

#### Scenario: cleanup 後に success result を消費する
- **WHEN** same-startup finalization が成功し、marker が cleanup 済みで `success=true` かつ `migrationCompleted=true` の result が残っている
- **THEN** result consumer はその result を既存の restore success として扱い、アプリを実行し続けたまま既存 success Snackbar path を使用する

#### Scenario: 同じ startup で success Snackbar を表示する
- **WHEN** application startup の同期 recovery が ordered finalization を完了し、その後 Activity が既存 result observation を開始する
- **THEN** システムは同じ process/startup 内で durable success result を観測して既存 text/layout の success Snackbar を表示し、追加 restart を要求しない

#### Scenario: result consumption は cleanup failure を unblock しない
- **WHEN** durable success result が消費され success Snackbar が表示されたが、cleanup failure により active `COMPLETED` marker が残っている
- **THEN** システムは同じ session の次 restore prepare を拒否し、cleanup の marker-removal commit point が成功した後にだけ prepare を許可する

#### Scenario: finalization が未完了である
- **WHEN** marker または result write が失敗し、durable success result まで到達していない
- **THEN** result consumer は restore success を表示せず、retryable pending state を次回 recovery のために維持する
